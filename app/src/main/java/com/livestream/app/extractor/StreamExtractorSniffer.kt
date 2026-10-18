package com.livestream.app.extractor

object StreamExtractorSniffer {

    /**
     * JavaScript injection sniffer that hooks XHR, Fetch, Hls.js, Clappr,
     * HTMLMediaElement, scans DOM & inline script tags, and auto-clicks Cloudflare Turnstile & play buttons.
     */
    fun getSnifferJavaScript(): String {
        return """
            (function() {
                if (window.__mediaUrlSnifferInjected) return;
                window.__mediaUrlSnifferInjected = true;

                function reportStream(url, source) {
                    if (!url || typeof url !== 'string') return;
                    if (url.startsWith('blob:') || url.startsWith('data:')) return;

                    const lower = url.toLowerCase();
                    if (!lower.includes('.m3u8') &&
                        !lower.includes('.mpd') &&
                        !lower.includes('/hls/') &&
                        !lower.includes('playlist.m3u8') &&
                        !lower.includes('master.m3u8') &&
                        !lower.includes('chunklist') &&
                        !(lower.includes('stream') && (lower.includes('token') || lower.includes('expires=')))) {
                        return;
                    }

                    try {
                        if (window.MediaUrlBridge && window.MediaUrlBridge.onStreamDetected) {
                            window.MediaUrlBridge.onStreamDetected(url, source || 'JS_SNIFFER', window.location.href);
                        }
                    } catch(e) {}
                }

                // 1. Hook XMLHttpRequest
                const origOpen = XMLHttpRequest.prototype.open;
                XMLHttpRequest.prototype.open = function(method, url) {
                    this._reqUrl = url;
                    reportStream(url, 'XHR');
                    return origOpen.apply(this, arguments);
                };

                // 2. Hook Fetch API
                const origFetch = window.fetch;
                window.fetch = function(input, init) {
                    const url = typeof input === 'string' ? input : (input && input.url ? input.url : '');
                    reportStream(url, 'FETCH');
                    return origFetch.apply(this, arguments);
                };

                // 3. Hook HTMLMediaElement.prototype.src setter
                try {
                    const origSrcDesc = Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype, 'src');
                    if (origSrcDesc && origSrcDesc.set) {
                        Object.defineProperty(HTMLMediaElement.prototype, 'src', {
                            set: function(val) {
                                reportStream(val, 'MEDIA_SRC_SET');
                                return origSrcDesc.set.apply(this, arguments);
                            },
                            get: origSrcDesc.get
                        });
                    }
                } catch(e) {}

                // 4. Hook Hls.js & Clappr loadSource
                function hookPlayers() {
                    try {
                        if (window.Hls && window.Hls.prototype && !window.Hls.prototype.__hooked) {
                            window.Hls.prototype.__hooked = true;
                            const origLoad = window.Hls.prototype.loadSource;
                            window.Hls.prototype.loadSource = function(url) {
                                reportStream(url, 'HLS_JS');
                                return origLoad.apply(this, arguments);
                            };
                        }
                    } catch(e) {}

                    try {
                        if (window.Clappr && window.Clappr.Player && window.Clappr.Player.prototype && !window.Clappr.Player.prototype.__hooked) {
                            window.Clappr.Player.prototype.__hooked = true;
                            const origLoad = window.Clappr.Player.prototype.load;
                            window.Clappr.Player.prototype.load = function(target) {
                                const url = typeof target === 'string' ? target : (target && target.source ? target.source : '');
                                if (url) reportStream(url, 'CLAPPR_LOAD');
                                return origLoad.apply(this, arguments);
                            };
                        }
                    } catch(e) {}
                }
                hookPlayers();
                setInterval(hookPlayers, 1000);

                // 5. Scan DOM for <video>, <source>, <audio>, <iframe> elements & inline script tags
                function scanMediaElements() {
                    document.querySelectorAll('video, audio, source, iframe').forEach(el => {
                        const src = el.src || el.getAttribute('src');
                        if (src) reportStream(src, 'TAG_' + el.tagName);
                    });

                    // Scan inline script tags for .m3u8 regex matches & obfuscated array joins
                    const m3u8Regex = /https?:\/\/[^\s"'<>]+\.m3u8[^\s"'<>]*/gi;
                    const arrayJoinRegex = /\[\s*(?:["'][a-zA-Z0-9_\-\.\:\/\?=&%]+["']\s*,\s*)+["'][a-zA-Z0-9_\-\.\:\/\?=&%]+["']\s*\]\.join\(\s*["']{2}\s*\)/g;

                    document.querySelectorAll('script').forEach(s => {
                        const code = s.textContent || s.innerText || '';

                        // Direct matches
                        const matches = code.match(m3u8Regex);
                        if (matches) {
                            matches.forEach(m => reportStream(m, 'INLINE_SCRIPT'));
                        }

                        // Obfuscated array join matches
                        const arrayMatches = code.match(arrayJoinRegex);
                        if (arrayMatches) {
                            arrayMatches.forEach(arrStr => {
                                try {
                                    const evaluated = (new Function('return ' + arrStr))();
                                    if (evaluated && typeof evaluated === 'string') {
                                        reportStream(evaluated, 'DEOBFUSCATED_ARRAY');
                                    }
                                } catch(e) {}
                            });
                        }
                    });

                    // Check common player global variables
                    for (let key of ['source', 'stream', 'streamUrl', 'm3u8', 'file', 'videoSrc', 'hlsUrl']) {
                        if (window[key] && typeof window[key] === 'string') {
                            reportStream(window[key], 'VAR_' + key);
                        }
                    }

                    // Check explicit generator functions if they exist
                    if (typeof window.ltiyTem === 'function') {
                        try {
                            const res = window.ltiyTem();
                            if (typeof res === 'string' && res.includes('.m3u8')) {
                                reportStream(res, 'FN_ltiyTem');
                            }
                        } catch(e) {}
                    }
                }
                scanMediaElements();
                setInterval(scanMediaElements, 1200);

                // 6. Auto-kickstart video players & Auto-click Cloudflare Turnstile
                function autoKickstart() {
                    // Auto-click Turnstile checkbox if present
                    try {
                        const tsBox = document.querySelector('.cf-turnstile, #cf-turnstile, div[class*="turnstile"], div[id*="cf-stage"]');
                        if (tsBox) {
                            const r = tsBox.getBoundingClientRect();
                            tsBox.dispatchEvent(new MouseEvent('click', { view: window, bubbles: true, cancelable: true, clientX: r.left + 30, clientY: r.top + (r.height / 2) }));
                        }
                        document.querySelectorAll('iframe[src*="challenges.cloudflare.com"], iframe[src*="turnstile"]').forEach(iframe => {
                            try {
                                iframe.focus();
                                const r = iframe.getBoundingClientRect();
                                iframe.dispatchEvent(new MouseEvent('click', { view: window, bubbles: true, cancelable: true, clientX: r.left + 30, clientY: r.top + (r.height / 2) }));
                            } catch(e) {}
                        });
                    } catch(e) {}

                    // Auto-play videos & click play buttons
                    document.querySelectorAll('video').forEach(v => {
                        try { v.muted = true; v.play(); } catch(e) {}
                    });
                    document.querySelectorAll('.jw-display-icon-container, .vjs-big-play-button, button[class*="play"], div[class*="play"]').forEach(b => {
                        try { b.click(); } catch(e) {}
                    });
                }
                setTimeout(autoKickstart, 800);
                setTimeout(autoKickstart, 2000);
                setInterval(autoKickstart, 3000);

                // 7. Observe Dynamic DOM Additions
                try {
                    const observer = new MutationObserver(function(mutations) {
                        mutations.forEach(function(m) {
                            m.addedNodes.forEach(function(node) {
                                if (node.nodeType === 1) {
                                    if (node.tagName === 'VIDEO' || node.tagName === 'SOURCE' || node.tagName === 'IFRAME') {
                                        reportStream(node.src || node.getAttribute('src'), 'MUTATION');
                                    }
                                    node.querySelectorAll?.('video, source, iframe').forEach(el => {
                                        reportStream(el.src || el.getAttribute('src'), 'MUTATION');
                                    });
                                }
                            });
                        });
                    });
                    observer.observe(document.documentElement || document.body, { childList: true, subtree: true });
                } catch(e) {}
            })();
        """.trimIndent()
    }
}
