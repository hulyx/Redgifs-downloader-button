(function() {
    'use strict';

    if (window.__redgifsDownloaderInjected) return;
    window.__redgifsDownloaderInjected = true;

    var currentVideoId = null;
    var checkInterval = null;

    // --- Extract video ID from the page ---
    function extractVideoId() {
        // 1. URL path: /watch/VIDEO_ID
        var urlMatch = window.location.pathname.match(/\/watch\/([A-Za-z0-9]+)/);
        if (urlMatch) return urlMatch[1];

        // 2. data-feed-item-id on any ancestor of a playing video
        var feedItems = document.querySelectorAll('[data-feed-item-id]');
        for (var i = 0; i < feedItems.length; i++) {
            var video = feedItems[i].querySelector('video');
            if (video && !video.paused && video.readyState > 2) {
                return feedItems[i].getAttribute('data-feed-item-id');
            }
        }

        // 3. From video src URL patterns
        var videos = document.querySelectorAll('video');
        for (var j = 0; j < videos.length; j++) {
            var v = videos[j];
            if (v.paused || v.readyState < 2) continue;

            // Check src attribute
            var src = v.src || v.getAttribute('src') || '';
            if (!src) {
                var source = v.querySelector('source');
                if (source) src = source.getAttribute('src') || '';
            }

            // Pattern: /VIDEO_ID.hd.mp4 or /VIDEO_ID.sd.mp4
            var srcMatch = src.match(/\/([A-Za-z][A-Za-z0-9]{5,})\.(?:hd|sd|mobile)\.(?:mp4|m4s)/);
            if (srcMatch) return srcMatch[1];

            // Pattern: /VIDEO_ID.mp4 or /VIDEO_ID.m4s
            var srcMatch2 = src.match(/\/([A-Za-z][A-Za-z0-9]{5,})\.(?:mp4|m4s)/);
            if (srcMatch2) return srcMatch2[1];

            // Pattern: /watch/VIDEO_ID
            var srcMatch3 = src.match(/\/watch\/([A-Za-z0-9]+)/);
            if (srcMatch3) return srcMatch3[1];
        }

        // 4. From meta tags
        var metas = document.querySelectorAll('meta[property="og:url"], meta[property="og:video"]');
        for (var k = 0; k < metas.length; k++) {
            var content = metas[k].getAttribute('content');
            if (content) {
                var metaMatch = content.match(/\/watch\/([A-Za-z0-9]+)/);
                if (metaMatch) return metaMatch[1];
            }
        }

        // 5. From any element with data-id or data-gif-id
        var dataEls = document.querySelectorAll('[data-id], [data-gif-id]');
        for (var m = 0; m < dataEls.length; m++) {
            var did = dataEls[m].getAttribute('data-id') || dataEls[m].getAttribute('data-gif-id');
            if (did) return did;
        }

        // 6. From video poster: Poster for VIDEO_ID
        var imgs = document.querySelectorAll('img[alt]');
        for (var n = 0; n < imgs.length; n++) {
            var alt = imgs[n].getAttribute('alt');
            if (alt && alt.indexOf('Poster for ') === 0) {
                var aid = alt.replace('Poster for ', '');
                if (aid && aid.indexOf(' ') === -1 && aid.length > 3) return aid;
            }
        }

        return null;
    }

    // --- Detect if any video is actively playing ---
    function detectPlayingVideo() {
        var videos = document.querySelectorAll('video');
        for (var i = 0; i < videos.length; i++) {
            var v = videos[i];
            if (!v.paused && v.readyState > 2 && v.currentTime > 0.5) {
                return true;
            }
        }
        return false;
    }

    // --- Check and report ---
    function checkVideo() {
        var isPlaying = detectPlayingVideo();
        var videoId = isPlaying ? extractVideoId() : null;

        if (isPlaying && videoId && videoId !== currentVideoId) {
            currentVideoId = videoId;
            try { Android.onVideoDetected(videoId); } catch(e) {}
        } else if (!isPlaying && currentVideoId) {
            currentVideoId = null;
            try { Android.onVideoLost(); } catch(e) {}
        }
    }

    // --- Start monitoring ---
    function startMonitoring() {
        if (checkInterval) return;
        checkInterval = setInterval(checkVideo, 1500);

        // Also listen for play events on existing videos
        document.addEventListener('play', function(e) {
            if (e.target && e.target.tagName === 'VIDEO') {
                setTimeout(checkVideo, 300);
            }
        }, true);

        // Re-bind when new videos are added
        var observer = new MutationObserver(function(mutations) {
            for (var i = 0; i < mutations.length; i++) {
                for (var j = 0; j < mutations[i].addedNodes.length; j++) {
                    var node = mutations[i].addedNodes[j];
                    if (node.tagName === 'VIDEO') {
                        node.addEventListener('play', function() {
                            setTimeout(checkVideo, 300);
                        });
                    }
                    if (node.querySelectorAll) {
                        var vids = node.querySelectorAll('video');
                        for (var k = 0; k < vids.length; k++) {
                            vids[k].addEventListener('play', function() {
                                setTimeout(checkVideo, 300);
                            });
                        }
                    }
                }
            }
        });

        observer.observe(document.body, { childList: true, subtree: true });

        // Bind to existing videos
        document.querySelectorAll('video').forEach(function(v) {
            v.addEventListener('play', function() { setTimeout(checkVideo, 300); });
            v.addEventListener('pause', function() { setTimeout(checkVideo, 500); });
            v.addEventListener('ended', function() { setTimeout(checkVideo, 500); });
        });

        // Re-init on SPA navigation
        var lastUrl = location.href;
        new MutationObserver(function() {
            if (location.href !== lastUrl) {
                lastUrl = location.href;
                currentVideoId = null;
                try { Android.onVideoLost(); } catch(e) {}
                setTimeout(function() {
                    document.querySelectorAll('video').forEach(function(v) {
                        v.addEventListener('play', function() { setTimeout(checkVideo, 300); });
                        v.addEventListener('pause', function() { setTimeout(checkVideo, 500); });
                        v.addEventListener('ended', function() { setTimeout(checkVideo, 500); });
                    });
                }, 1000);
            }
        }).observe(document, { subtree: true, childList: true });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', startMonitoring);
    } else {
        startMonitoring();
    }

})();
