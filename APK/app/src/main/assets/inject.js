(function() {
    'use strict';

    if (window.__redgifsDownloaderInjected) return;
    window.__redgifsDownloaderInjected = true;

    var processedPlayers = new WeakSet();
    var INJECT_DEBOUNCE = 300;
    var pendingDownloads = {};

    // --- CSS Injection ---
    var style = document.createElement('style');
    style.textContent = [
        '.rg-dl-btn-wrapper {',
        '  position: absolute !important;',
        '  top: 8px !important;',
        '  right: 8px !important;',
        '  z-index: 2147483647 !important;',
        '  pointer-events: auto !important;',
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
        if (!container) return null;

        // Strategy 1: data-feed-item-id attribute (most reliable on current Redgifs)
        var feedItem = container.closest('[data-feed-item-id]');
        if (feedItem) {
            return feedItem.getAttribute('data-feed-item-id');
        }
        if (container.getAttribute && container.getAttribute('data-feed-item-id')) {
            return container.getAttribute('data-feed-item-id');
        }

        // Strategy 2: Container ID (gif_xxxxx)
        if (container.id && container.id.indexOf('gif_') === 0) {
            return sanitizeId(container.id.replace('gif_', ''));
        }

        // Strategy 3: From video poster URL
        var video = container.querySelector ? container.querySelector('video') : null;
        if (video && video.poster) {
            var posterMatch = video.poster.match(/\/([A-Za-z][A-Za-z0-9]+?)(?:-mobile|-hd)?\.jpg$/);
            if (posterMatch && posterMatch[1]) return sanitizeId(posterMatch[1]);
        }

        // Strategy 4: From img alt text ("Poster for xxx")
        var imgs = container.querySelectorAll ? container.querySelectorAll('img[alt]') : [];
        for (var k = 0; k < imgs.length; k++) {
            var alt = imgs[k].getAttribute('alt');
            if (alt && alt.indexOf('Poster for ') === 0) {
                var aid = alt.replace('Poster for ', '');
                if (aid && aid.indexOf(' ') === -1) return sanitizeId(aid);
            }
        }

        // Strategy 5: URL path for /watch/ pages
        if (window.location.pathname.indexOf('/watch/') !== -1) {
            var urlMatch = window.location.pathname.match(/\/watch\/([^/?]+)/);
            if (urlMatch && urlMatch[1]) return sanitizeId(urlMatch[1]);
        }

        // Strategy 6: Meta tags
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

        // Strategy 7: data-id / data-gif-id attributes
        var dataEls = container.querySelectorAll ? container.querySelectorAll('[data-id], [data-gif-id]') : [];
        for (var j = 0; j < dataEls.length; j++) {
            var dataId = dataEls[j].getAttribute('data-id') || dataEls[j].getAttribute('data-gif-id');
            if (dataId) return sanitizeId(dataId);
        }

        // Strategy 8: Source src attribute
        var sources = container.querySelectorAll ? container.querySelectorAll('source[src]') : [];
        for (var s = 0; s < sources.length; s++) {
            var src = sources[s].getAttribute('src');
            if (src) {
                var srcMatch = src.match(/\/([A-Za-z][A-Za-z0-9]+)\.(mp4|m4s)/);
                if (srcMatch && srcMatch[1]) return sanitizeId(srcMatch[1]);
            }
        }

        // Strategy 9: Walk up parent chain looking for data-feed-item-id
        var parent = container.parentElement;
        for (var p = 0; p < 5 && parent; p++) {
            if (parent.getAttribute && parent.getAttribute('data-feed-item-id')) {
                return parent.getAttribute('data-feed-item-id');
            }
            parent = parent.parentElement;
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
        var container = wrapper ? wrapper.parentElement : null;

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

        if (container.querySelector && container.querySelector('.rg-dl-btn-wrapper')) {
            processedPlayers.add(container);
            return;
        }

        processedPlayers.add(container);

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

        // Current Redgifs selectors: GifPreview, Player
        if (element.classList.contains('GifPreview') || element.classList.contains('Player')) {
            if (!processedPlayers.has(element)) {
                addDownloadButton(element);
            }
            return;
        }

        // Look for inner GifPreview/Player
        var inner = element.querySelector ? element.querySelector('.GifPreview, .Player') : null;
        if (inner && !processedPlayers.has(inner)) {
            addDownloadButton(inner);
        }

        // If this is a video element, find its parent GifPreview
        if (element.tagName === 'VIDEO') {
            var playerContainer = element.closest('.GifPreview');
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
                    var found = nodes[i].querySelectorAll('.GifPreview, .Player, video, [data-feed-item-id]');
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

        // Process existing elements
        var existing = document.querySelectorAll('.GifPreview, .Player, [data-feed-item-id]');
        for (var k = 0; k < existing.length; k++) {
            processElement(existing[k]);
        }

        setInterval(cleanupOrphaned, 5000);
    }

    // --- Initialization ---
    function init() {
        initObserver();

        // Re-scan periodically in case SPA navigation doesn't trigger observer properly
        setInterval(function() {
            var items = document.querySelectorAll('.GifPreview:not(.rg-processed), [data-feed-item-id]:not(.rg-processed)');
            for (var i = 0; i < items.length; i++) {
                processElement(items[i]);
            }
        }, 2000);
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
                var existing = document.querySelectorAll('.GifPreview, .Player, [data-feed-item-id]');
                for (var k = 0; k < existing.length; k++) {
                    processElement(existing[k]);
                }
            }, 1000);
        }
    }).observe(document, { subtree: true, childList: true });

})();
