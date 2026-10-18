package com.livestream.app.extractor

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.livestream.app.data.model.ChannelItem
import com.livestream.app.data.model.ExtractedStream
import java.util.concurrent.ConcurrentHashMap

@SuppressLint("SetJavaScriptEnabled")
class BackgroundStreamExtractor(private val context: Context) {

    private val TAG = "StreamExtractor"
    private val mainHandler = Handler(Looper.getMainLooper())

    private var webView: WebView? = null
    private val streamCache = ConcurrentHashMap<String, ExtractedStream>()

    // Thread-safe state for background threads (shouldInterceptRequest & JavaBridge)
    @Volatile
    private var currentLoadedPageUrl: String = ""

    @Volatile
    private var activeExtractionToken: Long = 0L

    // Current in-flight extraction request (modified on main thread)
    private var activeChannelId: String? = null
    private var activeCallback: ((ExtractedStream) -> Unit)? = null
    private var activeErrorCallback: ((String) -> Unit)? = null
    private var timeoutRunnable: Runnable? = null

    private val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    init {
        mainHandler.post {
            initWebView()
        }
    }

    private fun initWebView() {
        try {
            webView = WebView(context.applicationContext).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    allowFileAccess = true
                    allowContentAccess = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    userAgentString = DEFAULT_USER_AGENT
                    cacheMode = WebSettings.LOAD_DEFAULT
                    setSupportMultipleWindows(false)
                    javaScriptCanOpenWindowsAutomatically = true
                }

                try {
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                } catch (_: Exception) {}

                // JavaScript bridge to receive detected streams from injected scripts
                addJavascriptInterface(ExtractorBridge(), "MediaUrlBridge")

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val nextUrl = request?.url?.toString()
                        if (nextUrl != null) {
                            currentLoadedPageUrl = nextUrl
                        }
                        return false
                    }

                    // Called on Chromium background IO thread - DO NOT call view or webView methods here
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val reqUrl = request?.url?.toString()
                        if (reqUrl != null && isMediaStreamUrl(reqUrl)) {
                            val referer = request.requestHeaders?.get("Referer") ?: currentLoadedPageUrl
                            val ua = request.requestHeaders?.get("User-Agent") ?: DEFAULT_USER_AGENT
                            handleDetectedStream(reqUrl, "NETWORK_INTERCEPT", referer, ua, activeExtractionToken)
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                        handler?.proceed()
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        if (url != null) {
                            currentLoadedPageUrl = url
                        }
                        injectSnifferScript(view)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (url != null) {
                            currentLoadedPageUrl = url
                        }
                        injectSnifferScript(view)
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                        result?.confirm()
                        return true
                    }
                    override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                        result?.confirm()
                        return true
                    }
                    override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean {
                        result?.confirm()
                        return true
                    }
                }
            }
            Log.d(TAG, "Headless background WebView initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing background WebView: ${e.message}")
        }
    }

    private fun injectSnifferScript(view: WebView?) {
        try {
            val script = StreamExtractorSniffer.getSnifferJavaScript()
            view?.evaluateJavascript(script, null)
        } catch (_: Exception) {}
    }

    /**
     * Extracts live m3u8 stream for a given channel in the background.
     * If already cached and valid, returns immediately.
     */
    fun extractStream(
        channel: ChannelItem,
        forceRefresh: Boolean = false,
        onSuccess: (ExtractedStream) -> Unit,
        onError: (String) -> Unit
    ) {
        val cached = streamCache[channel.id]
        val now = System.currentTimeMillis()
        if (!forceRefresh && cached != null && (now - cached.timestamp) < 15 * 60 * 1000L) {
            Log.d(TAG, "Serving channel '${channel.name}' from active cache: ${cached.url}")
            onSuccess(cached)
            return
        }

        mainHandler.post {
            cancelActiveTimeout()

            val token = ++activeExtractionToken
            activeChannelId = channel.id
            activeCallback = onSuccess
            activeErrorCallback = onError

            val targetUrl = channel.embedUrl.trim()
            if (targetUrl.isBlank()) {
                onError("Embed URL is empty for channel ${channel.name}")
                return@post
            }

            currentLoadedPageUrl = targetUrl
            Log.d(TAG, "Starting background extraction [$token] for '${channel.name}' -> $targetUrl")
            webView?.stopLoading()
            webView?.loadUrl(targetUrl)

            // Setup a 20 second timeout fallback
            val runnable = Runnable {
                if (token == activeExtractionToken && activeChannelId == channel.id && activeErrorCallback != null) {
                    val cb = activeErrorCallback
                    activeCallback = null
                    activeErrorCallback = null
                    cb?.invoke("Extraction timeout. Stream could not be detected automatically.")
                }
            }
            timeoutRunnable = runnable
            mainHandler.postDelayed(runnable, 20_000L)
        }
    }

    /**
     * Pre-extracts a channel in the background to warm the cache (e.g., on app start).
     */
    fun preExtract(channel: ChannelItem) {
        val cached = streamCache[channel.id]
        val now = System.currentTimeMillis()
        if (cached != null && (now - cached.timestamp) < 15 * 60 * 1000L) {
            return
        }
        extractStream(
            channel = channel,
            forceRefresh = false,
            onSuccess = { stream ->
                Log.d(TAG, "Pre-extracted cache warmed for '${channel.name}' -> ${stream.url}")
            },
            onError = { err ->
                Log.w(TAG, "Pre-extract failed for '${channel.name}': $err")
            }
        )
    }

    fun invalidateCache(channelId: String) {
        streamCache.remove(channelId)
    }

    fun getCachedStream(channelId: String): ExtractedStream? {
        return streamCache[channelId]
    }

    private fun handleDetectedStream(
        url: String,
        source: String,
        referer: String,
        userAgent: String,
        token: Long
    ) {
        // Discard stale in-flight requests from a previously abandoned channel
        if (token != activeExtractionToken) {
            Log.d(TAG, "Discarded out-of-order stream token $token (active is $activeExtractionToken)")
            return
        }

        var cleanUrl = url.trim()
        if (!isMediaStreamUrl(cleanUrl)) return

        // Resolve relative URLs against current loaded page if needed
        if (!cleanUrl.startsWith("http://", ignoreCase = true) && !cleanUrl.startsWith("https://", ignoreCase = true)) {
            val base = currentLoadedPageUrl
            cleanUrl = try {
                val baseUri = Uri.parse(base)
                val resolved = Uri.withAppendedPath(baseUri, cleanUrl)
                resolved.toString()
            } catch (_: Exception) {
                return
            }
        }

        val effectiveReferer = if (referer.isNotBlank()) referer else currentLoadedPageUrl
        val effectiveUa = if (userAgent.isNotBlank()) userAgent else DEFAULT_USER_AGENT

        val extracted = ExtractedStream(
            url = cleanUrl,
            referer = effectiveReferer,
            userAgent = effectiveUa,
            source = source,
            timestamp = System.currentTimeMillis()
        )

        mainHandler.post {
            if (token != activeExtractionToken) return@post

            val chId = activeChannelId
            if (chId != null) {
                streamCache[chId] = extracted
            }

            cancelActiveTimeout()
            val cb = activeCallback
            activeCallback = null
            activeErrorCallback = null
            cb?.invoke(extracted)
        }
    }

    private fun cancelActiveTimeout() {
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = null
    }

    fun isMediaStreamUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase()

        // Strip query params and fragments to inspect actual file extension
        val path = lower.substringBefore('?').substringBefore('#')

        // Exclude static assets, stylesheets, fonts, and scripts
        if (path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") ||
            path.endsWith(".gif") || path.endsWith(".css") || path.endsWith(".svg") ||
            path.endsWith(".woff") || path.endsWith(".woff2") || path.endsWith(".ttf") ||
            path.endsWith(".js") || path.endsWith(".html") || path.endsWith(".htm") ||
            path.endsWith(".php") || path.endsWith(".ico") || path.endsWith(".json") ||
            path.endsWith(".map")
        ) {
            return false
        }

        // Must match an actual media manifest/stream indicator
        return lower.contains(".m3u8") ||
                lower.contains(".mpd") ||
                lower.contains("/hls/") ||
                lower.contains("playlist.m3u8") ||
                lower.contains("master.m3u8") ||
                lower.contains("chunklist") ||
                lower.contains("/manifest") ||
                path.endsWith(".mp4") ||
                path.endsWith(".m4s") ||
                path.endsWith(".ts")
    }

    inner class ExtractorBridge {
        // Runs on JavaBridge background thread - do NOT touch WebView directly
        @JavascriptInterface
        fun onStreamDetected(url: String?, source: String?, referer: String?) {
            if (url.isNullOrBlank()) return
            handleDetectedStream(
                url = url,
                source = source ?: "JS_INTERFACE",
                referer = referer ?: currentLoadedPageUrl,
                userAgent = DEFAULT_USER_AGENT,
                token = activeExtractionToken
            )
        }
    }

    fun destroy() {
        mainHandler.post {
            cancelActiveTimeout()
            activeCallback = null
            activeErrorCallback = null
            try {
                webView?.stopLoading()
                webView?.destroy()
            } catch (_: Exception) {}
            webView = null
        }
    }
}
