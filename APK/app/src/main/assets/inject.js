(function() {
    'use strict';

    if (window.__redgifsDownloaderInjected) return;
    window.__redgifsDownloaderInjected = true;

    var processedPlayers = new WeakSet();
    var INJECT_DEBOUNCE = 200;
    var pendingDownloads = {};

    // --- CSS Injection ---
    var style = document.createElement('style');
    style.textContent = [
        '.rg-dl-btn-wrapper {',
        '  position: absolute !important;',
        '  top: 8px !important;',
        '  right: 8px !important;',
        '  z-index: 2147483647 !important;',
        '  pointer-events: none !important;',
        '}',
        '.rg-dl-btn {',
        '  all: initial !important;',
        '  position: relative !important;',
        '  padding: 6px 14px !important;',
        '  background: rgba(0,0,0,0.75) !important;',
        '  color: #fff !important;',
        '  border: none !important;',
        '  border-radius: 6px !important;',
        '  cursor: pointer !important;',
        '  font-size: 13px !important;',
        '  font-weight: 600 !important;',
        '  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif !important;',
        '  display: flex !important;',
        '  align-items: center !important;',
        '  gap: 5px !important;',
        '  pointer-events: auto !important;',
        '  box-shadow: 0 2px 10px rgba(0,0,0,0.4) !important;',
        '  backdrop-filter: blur(6px) !important;',
        '  -webkit-backdrop-filter: blur(6px) !important;',
        '  transition: all 0.2s ease !important;',
        '  white-space: nowrap !important;',
        '  line-height: 1 !important;',
        '  opacity: 0 !important;',
        '  transform: translateY(-4px) !important;',
        '  animation: rg-dl-appear 0.3s ease forwards !important;',
        '}',
        '@keyframes rg-dl-appear {',
        '  to { opacity: 1; transform: translateY(0) !important; }',
        '}',
        '.rg-dl-btn:hover {',
        '  background: rgba(255,82,82,0.9) !important;',
        '  transform: scale(1.05) !important;',
        '  box-shadow: 0 4px 14px rgba(255,82,82,0.4) !important;',
        '}',
        '.rg-dl-btn:active {',
        '  transform: scale(0.97) !important;',
        '}',
        '.rg-dl-btn.rg-downloading {',
        '  background: rgba(33,150,243,0.85) !important;',
        '  pointer-events: none !important;',
        '}',
        '.rg-dl-btn.rg-success {',
        '  background: rgba(76,175,80,0.85) !important;',
        '  pointer-events: none !important;',
        '}',
        '.rg-dl-btn.rg-error {',
        '  background: rgba(244,67,54,0.85) !important;',
        '  pointer-events: auto !important;',
        '}',
        '.rg-dl-btn.rg-done {',
        '  background: rgba(76,175,80,0.5) !important;',
        '  pointer-events: auto !important;',
        '}'
    ].join('\n');
    document.head.appendChild(style);

    // --- Video ID Extraction ---
    function sanitizeId(id) {
        if (!id) return id;
        return id
            .replace(/\.(mp4|m4s|webm|webp|jpg|jpeg|png|gif)$/i, '')
            .replace(/-(silent|mobile|large|small|poster)$/i, '');
    }

    function getVideoIdFromContainer(container) {
        if (container && container.classList && !container.classList.contains('GifPreviewV2')) {
            var gp = container.closest('.GifPreviewV2');
            if (gp) container = gp;
        }

        if (container && container.id && container.id.indexOf('gif_') === 0) {
            return sanitizeId(container.id.replace('gif_', ''));
        }

        var video = container ? container.querySelector('video') : null;
        if (video && video.poster) {
            var posterMatch = video.poster.match(/\/([^/]+)-mobile\.jpg$/);
            if (posterMatch && posterMatch[1]) return sanitizeId(posterMatch[1]);
        }

        if (window.location.pathname.indexOf('/watch/') !== -1) {
            var urlMatch = window.location.pathname.match(/\/watch\/([^/?]+)/);
            if (urlMatch && urlMatch[1]) return sanitizeId(urlMatch[1]);
        }

        var metas = document.querySelectorAll('meta[property="og:url"], meta[property="og:video"]');
        for (var i = 0; i < metas.length; i++) {
            var content = metas[i].getAttribute('content');
            if (content) {
                var metaMatch = content.match(/\/([^/]+?)(?:\/hd|\/|$)/);
                if (metaMatch && metaMatch[1] && metaMatch[1].length > 3) {
                    return sanitizeId(metaMatch[1]);
                }
            }
        }

        if (container) {
            var dataEls = container.querySelectorAll('[data-id], [data-gif-id]');
            for (var j = 0; j < dataEls.length; j++) {
                var dataId = dataEls[j].getAttribute('data-id') || dataEls[j].getAttribute('data-gif-id');
                if (dataId) return sanitizeId(dataId);
            }
        }

        if (container) {
            var imgs = container.querySelectorAll('img[alt]');
            for (var k = 0; k < imgs.length; k++) {
                var alt = imgs[k].getAttribute('alt');
                if (alt && alt.indexOf('Poster for ') === 0) {
                    var aid = alt.replace('Poster for ', '');
                    if (aid && aid.indexOf(' ') === -1) return sanitizeId(aid);
                }
            }
        }

        if (container) {
            var sources = container.querySelectorAll('source[src]');
            for (var s = 0; s < sources.length; s++) {
                var src = sources[s].getAttribute('src');
                if (src) {
                    var srcMatch = src.match(/\/([A-Za-z][A-Za-z0-9]+)\.(mp4|m4s)/);
                    if (srcMatch && srcMatch[1]) return sanitizeId(srcMatch[1]);
                }
            }
        }

        return null;
    }

    function getVideoTitle(videoId) {
        return 'redgifs_' + (videoId || 'video');
    }

    // --- Button State ---
    function setBtnState(btn, state, text) {
        btn.className = 'rg-dl-btn' + (state ? ' rg-' + state : '');
        btn.textContent = text;
    }

    function resetBtn(btn) {
        setTimeout(function() {
            setBtnState(btn, null, '\u2B07 Download');
            btn.style.pointerEvents = 'auto';
        }, 2500);
    }

    // --- Download Handler ---
    function handleDownload(e) {
        e.preventDefault();
        e.stopPropagation();

        var btn = e.currentTarget;
        var wrapper = btn.closest('.rg-dl-btn-wrapper');
        var container = wrapper ? wrapper.parentElement : btn.closest('.GifPreviewV2, .TapTracker, .PlayerV2');

        if (!container) return;

        var videoId = getVideoIdFromContainer(container);
        if (!videoId) {
            setBtnState(btn, 'error', '\u274C No ID');
            resetBtn(btn);
            return;
        }

        setBtnState(btn, 'downloading', '\u23F3 Fetching\u2026');
        btn.style.pointerEvents = 'none';

        pendingDownloads[videoId] = {
            btn: btn,
            startTime: Date.now()
        };

        try {
            Android.downloadVideo(videoId, getVideoTitle(videoId));
        } catch(err) {
            setBtnState(btn, 'error', '\u274C Failed');
            delete pendingDownloads[videoId];
            resetBtn(btn);
        }
    }

    // --- JS Callbacks from native ---
    window.__onDownloadProgress = function(videoId, percent) {
        var dl = pendingDownloads[videoId];
        if (dl && dl.btn) {
            setBtnState(dl.btn, 'downloading', '\u23F3 ' + percent + '%');
        }
    };

    window.__onDownloadComplete = function(videoId, filename) {
        var dl = pendingDownloads[videoId];
        if (dl && dl.btn) {
            setBtnState(dl.btn, 'success', '\u2705 Downloaded');
            resetBtn(dl.btn);
        }
        delete pendingDownloads[videoId];
    };

    window.__onDownloadError = function(videoId, error) {
        var dl = pendingDownloads[videoId];
        if (dl && dl.btn) {
            setBtnState(dl.btn, 'error', '\u274C Failed');
            resetBtn(dl.btn);
        }
        delete pendingDownloads[videoId];
    };

    // --- Button Injection ---
    function addDownloadButton(container) {
        if (!container) return;
        if (processedPlayers.has(container)) return;
        processedPlayers.add(container);

        if (container.querySelector('.rg-dl-btn-wrapper')) return;

        if (!container.id) {
            container.id = 'rg-c-' + Math.random().toString(36).substring(2, 9);
        }

        var wrapper = document.createElement('div');
        wrapper.className = 'rg-dl-btn-wrapper';

        var btn = document.createElement('button');
        btn.className = 'rg-dl-btn';
        btn.textContent = '\u2B07 Download';

        var videoId = getVideoIdFromContainer(container);
        if (videoId) {
            btn.setAttribute('data-rg-id', videoId);
        }

        btn.addEventListener('click', handleDownload);
        wrapper.appendChild(btn);
        container.appendChild(wrapper);
    }

    // --- Element Processing ---
    function processElement(element) {
        if (!element || !element.classList) return;

        var selectors = ['GifPreviewV2', 'TapTracker', 'PlayerV2'];
        for (var i = 0; i < selectors.length; i++) {
            if (element.classList.contains(selectors[i])) {
                if (!processedPlayers.has(element)) {
                    addDownloadButton(element);
                }
                return;
            }
        }

        var inner = element.querySelector('.GifPreviewV2, .TapTracker, .PlayerV2');
        if (inner && !processedPlayers.has(inner)) {
            addDownloadButton(inner);
        }

        if (element.tagName === 'VIDEO') {
            var playerContainer = element.closest('.GifPreviewV2, .TapTracker, .PlayerV2');
            if (playerContainer && !processedPlayers.has(playerContainer)) {
                addDownloadButton(playerContainer);
            }
        }
    }

    // --- DOM Observer ---
    function initObserver() {
        var debounceTimer = null;
        var pendingNodes = [];

        function processPending() {
            var nodes = pendingNodes;
            pendingNodes = [];
            debounceTimer = null;

            for (var i = 0; i < nodes.length; i++) {
                processElement(nodes[i]);
                if (nodes[i].querySelectorAll) {
                    var found = nodes[i].querySelectorAll('.GifPreviewV2, .TapTracker, .PlayerV2, video');
                    for (var j = 0; j < found.length; j++) {
                        processElement(found[j]);
                    }
                }
            }
        }

        function cleanupOrphaned() {
            var wrappers = document.querySelectorAll('.rg-dl-btn-wrapper');
            for (var i = 0; i < wrappers.length; i++) {
                var parent = wrappers[i].parentElement;
                if (!parent || !document.body.contains(parent)) {
                    wrappers[i].remove();
                }
            }
        }

        var observer = new MutationObserver(function(mutations) {
            var needsCleanup = false;

            for (var i = 0; i < mutations.length; i++) {
                var m = mutations[i];
                if (m.type !== 'childList') continue;

                if (m.removedNodes.length > 0) needsCleanup = true;

                for (var j = 0; j < m.addedNodes.length; j++) {
                    var node = m.addedNodes[j];
                    if (node.nodeType === 1) {
                        pendingNodes.push(node);
                    }
                }
            }

            if (pendingNodes.length > 0 && !debounceTimer) {
                debounceTimer = setTimeout(processPending, INJECT_DEBOUNCE);
            }

            if (needsCleanup) cleanupOrphaned();
        });

        observer.observe(document.body, {
            childList: true,
            subtree: true
        });

        var existing = document.querySelectorAll('.GifPreviewV2, .TapTracker, .PlayerV2');
        for (var k = 0; k < existing.length; k++) {
            processElement(existing[k]);
        }

        setInterval(cleanupOrphaned, 5000);
    }

    // --- Video Element Source Watcher ---
    function watchVideoSources() {
        var observer = new MutationObserver(function(mutations) {
            for (var i = 0; i < mutations.length; i++) {
                var m = mutations[i];
                if (m.type === 'attributes' && m.attributeName === 'src') {
                    var video = m.target;
                    if (video.tagName === 'VIDEO') {
                        var playerContainer = video.closest('.GifPreviewV2, .TapTracker, .PlayerV2');
                        if (playerContainer && !processedPlayers.has(playerContainer)) {
                            addDownloadButton(playerContainer);
                        }
                    }
                }
            }
        });

        document.querySelectorAll('video').forEach(function(video) {
            observer.observe(video, { attributes: true, attributeFilter: ['src'] });
        });

        var videoObserver = new MutationObserver(function(mutations) {
            for (var i = 0; i < mutations.length; i++) {
                for (var j = 0; j < mutations[i].addedNodes.length; j++) {
                    var node = mutations[i].addedNodes[j];
                    if (node.tagName === 'VIDEO') {
                        observer.observe(node, { attributes: true, attributeFilter: ['src'] });
                    }
                    if (node.querySelectorAll) {
                        node.querySelectorAll('video').forEach(function(v) {
                            observer.observe(v, { attributes: true, attributeFilter: ['src'] });
                        });
                    }
                }
            }
        });

        videoObserver.observe(document.body, { childList: true, subtree: true });
    }

    // --- Initialization ---
    function init() {
        var autoInject = true;
        try {
            autoInject = Android.isHdOnly !== undefined;
        } catch(e) {}

        initObserver();
        watchVideoSources();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

    // Re-inject on SPA navigation
    var lastUrl = location.href;
    new MutationObserver(function() {
        if (location.href !== lastUrl) {
            lastUrl = location.href;
            setTimeout(function() {
                var existing = document.querySelectorAll('.GifPreviewV2, .TapTracker, .PlayerV2');
                for (var k = 0; k < existing.length; k++) {
                    processElement(existing[k]);
                }
            }, 500);
        }
    }).observe(document, { subtree: true, childList: true });

})();
