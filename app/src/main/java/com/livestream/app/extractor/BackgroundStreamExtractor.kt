package com.livestream.app.extractor

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
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

    // Current in-flight extraction request
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
                        // Allow internal navigation to follow player embeds/redirects
                        return false
                    }

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val reqUrl = request?.url?.toString()
                        if (reqUrl != null && isMediaStreamUrl(reqUrl)) {
                            val referer = request.requestHeaders?.get("Referer") ?: view?.url.orEmpty()
                            val ua = request.requestHeaders?.get("User-Agent") ?: DEFAULT_USER_AGENT
                            handleDetectedStream(reqUrl, "NETWORK_INTERCEPT", referer, ua)
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                        // Proceed through CDN streaming cert differences
                        handler?.proceed()
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        injectSnifferScript(view)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
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

            activeChannelId = channel.id
            activeCallback = onSuccess
            activeErrorCallback = onError

            val targetUrl = channel.embedUrl.trim()
            if (targetUrl.isBlank()) {
                onError("Embed URL is empty for channel ${channel.name}")
                return@post
            }

            Log.d(TAG, "Starting background extraction for '${channel.name}' -> $targetUrl")
            webView?.stopLoading()
            webView?.loadUrl(targetUrl)

            // Setup a 20 second timeout fallback
            val runnable = Runnable {
                if (activeChannelId == channel.id && activeErrorCallback != null) {
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

    private fun handleDetectedStream(url: String, source: String, referer: String, userAgent: String) {
        val cleanUrl = url.trim()
        if (!isMediaStreamUrl(cleanUrl)) return

        val effectiveReferer = if (referer.isNotBlank()) referer else webView?.url.orEmpty()
        val effectiveUa = if (userAgent.isNotBlank()) userAgent else DEFAULT_USER_AGENT

        val extracted = ExtractedStream(
            url = cleanUrl,
            referer = effectiveReferer,
            userAgent = effectiveUa,
            source = source,
            timestamp = System.currentTimeMillis()
        )

        val chId = activeChannelId
        if (chId != null) {
            streamCache[chId] = extracted
        }

        mainHandler.post {
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

        // Exclude common static non-stream assets
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
            lower.endsWith(".gif") || lower.endsWith(".css") || lower.endsWith(".svg") ||
            lower.endsWith(".woff") || lower.endsWith(".woff2") || lower.endsWith(".ttf") ||
            lower.endsWith(".js")
        ) {
            return false
        }

        return lower.contains(".m3u8") ||
                lower.contains(".mpd") ||
                lower.contains("/hls/") ||
                lower.contains("playlist.m3u8") ||
                lower.contains("master.m3u8") ||
                lower.contains("chunklist") ||
                lower.contains("/manifest") ||
                (lower.contains("stream") && (lower.contains("token") || lower.contains("expires=")))
    }

    inner class ExtractorBridge {
        @JavascriptInterface
        fun onStreamDetected(url: String?, source: String?, referer: String?) {
            if (url.isNullOrBlank()) return
            handleDetectedStream(
                url = url,
                source = source ?: "JS_INTERFACE",
                referer = referer ?: webView?.url.orEmpty(),
                userAgent = DEFAULT_USER_AGENT
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
