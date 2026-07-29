(function() {
    'use strict';

    if (window.__redgifsDownloaderInjected) return;
    window.__redgifsDownloaderInjected = true;

    var currentVideoId = null;
    var lastUrl = location.href;
    var checkInterval = null;

    // --- SPA URL change detection (pushState override + popstate) ---
    function onUrlChanged() {
        if (location.href === lastUrl) return;
        lastUrl = location.href;
        currentVideoId = null;
        try { Android.onVideoLost(); } catch(e) {}
        setTimeout(function() {
            document.querySelectorAll('video').forEach(function(v) {
                v.addEventListener('play', function() { setTimeout(checkVideo, 300); });
                v.addEventListener('pause', function() { setTimeout(checkVideo, 500); });
                v.addEventListener('ended', function() { setTimeout(checkVideo, 500); });
            });
            checkVideo();
        }, 1000);
    }

    (function() {
        var origPushState = history.pushState;
        var origReplaceState = history.replaceState;
        history.pushState = function() { origPushState.apply(this, arguments); onUrlChanged(); };
        history.replaceState = function() { origReplaceState.apply(this, arguments); onUrlChanged(); };
    })();
    window.addEventListener('popstate', onUrlChanged);

    // --- Extract video ID from a specific video element ---
    function extractIdFromVideo(v) {
        if (!v) return null;

        // 1. Walk up ancestors looking for data-id / data-gif-id / data-feed-item-id
        var el = v.parentElement;
        while (el && el !== document.body) {
            var did = el.getAttribute('data-id') || el.getAttribute('data-gif-id') || el.getAttribute('data-feed-item-id');
            if (did) return did;
            el = el.parentElement;
        }

        // 2. Check poster attribute
        var poster = v.getAttribute('poster');
        if (poster) {
            // thumbs.redgifs.com/VIDEOID.jpg or VIDEOID-thumb.jpg
            var pMatch = poster.match(/\/([A-Za-z0-9_-]{5,})[-._]/);
            if (pMatch) return pMatch[1].replace(/^thumb[-_]/, '');
        }

        // 3. Check src / source URLs
        var src = v.src || v.getAttribute('src') || '';
        if (!src) {
            var source = v.querySelector('source');
            if (source) src = source.getAttribute('src') || '';
        }
        if (src && !src.startsWith('blob:')) {
            var s1 = src.match(/\/([A-Za-z][A-Za-z0-9]{5,})\.(?:hd|sd|mobile)\.(?:mp4|m4s)/);
            if (s1) return s1[1];
            var s2 = src.match(/\/([A-Za-z][A-Za-z0-9]{5,})\.(?:mp4|m4s)/);
            if (s2) return s2[1];
            var s3 = src.match(/\/watch\/([A-Za-z0-9]+)/);
            if (s3) return s3[1];
        }

        // 4. Check for nearby thumbnail images (thumbs.redgifs.com)
        var container = v.parentElement;
        while (container && container !== document.body) {
            var imgs = container.querySelectorAll('img[src*="thumbs.redgifs.com"]');
            for (var i = 0; i < imgs.length; i++) {
                var imgSrc = imgs[i].getAttribute('src') || '';
                var tMatch = imgSrc.match(/thumbs\.redgifs\.com\/([A-Za-z0-9_-]+)\./);
                if (tMatch) return tMatch[1].replace(/[-_].*$/, '');
            }
            container = container.parentElement;
        }

        return null;
    }

    // --- Extract video ID from the page ---
    function extractVideoId() {
        // 0. URL path (watch pages)
        var urlMatch = window.location.pathname.match(/\/watch\/([A-Za-z0-9]+)/);
        if (urlMatch) return urlMatch[1];

        // 1. Meta tags
        var metas = document.querySelectorAll('meta[property="og:url"], meta[property="og:video"]');
        for (var k = 0; k < metas.length; k++) {
            var content = metas[k].getAttribute('content');
            if (content) {
                var metaMatch = content.match(/\/watch\/([A-Za-z0-9]+)/);
                if (metaMatch) return metaMatch[1];
            }
        }

        // 2. Images with alt="Poster for VIDEOID"
        var imgs = document.querySelectorAll('img[alt]');
        for (var n = 0; n < imgs.length; n++) {
            var alt = imgs[n].getAttribute('alt');
            if (alt && alt.indexOf('Poster for ') === 0) {
                var aid = alt.replace('Poster for ', '');
                if (aid && aid.indexOf(' ') === -1 && aid.length > 3) return aid;
            }
        }

        // 3. Elements with data-id / data-gif-id
        var dataEls = document.querySelectorAll('[data-id], [data-gif-id]');
        for (var m = 0; m < dataEls.length; m++) {
            var did = dataEls[m].getAttribute('data-id') || dataEls[m].getAttribute('data-gif-id');
            if (did) return did;
        }

        // 4. From any playing video element
        var videos = document.querySelectorAll('video');
        for (var j = 0; j < videos.length; j++) {
            if (videos[j].paused || videos[j].readyState < 2) continue;
            var id = extractIdFromVideo(videos[j]);
            if (id) return id;
        }

        // 5. From any thumbnails on the page
        var allThumbs = document.querySelectorAll('img[src*="thumbs.redgifs.com"]');
        for (var t = 0; t < allThumbs.length; t++) {
            var ts = allThumbs[t].getAttribute('src') || '';
            var tm = ts.match(/thumbs\.redgifs\.com\/([A-Za-z0-9_-]+)\./);
            if (tm) return tm[1].replace(/[-_].*$/, '');
        }

        return null;
    }

    // --- Get first actively playing video element ---
    function getPlayingVideo() {
        var videos = document.querySelectorAll('video');
        for (var i = 0; i < videos.length; i++) {
            var v = videos[i];
            if (!v.paused && v.readyState > 2 && v.currentTime > 0.5) {
                return v;
            }
        }
        return null;
    }

    // --- Check and report ---
    function checkVideo() {
        if (location.href !== lastUrl) {
            onUrlChanged();
            return;
        }

        var playingVideo = getPlayingVideo();
        var videoId = null;
        if (playingVideo) {
            videoId = extractIdFromVideo(playingVideo);
            if (!videoId) videoId = extractVideoId();
        }

        if (videoId && videoId !== currentVideoId) {
            currentVideoId = videoId;
            try { Android.onVideoDetected(videoId); } catch(e) {}
        } else if (!videoId && currentVideoId) {
            currentVideoId = null;
            try { Android.onVideoLost(); } catch(e) {}
        }
    }

    // --- Start monitoring ---
    function startMonitoring() {
        if (checkInterval) return;
        checkInterval = setInterval(checkVideo, 1500);

        // Listen for play events
        document.addEventListener('play', function(e) {
            if (e.target && e.target.tagName === 'VIDEO') {
                setTimeout(checkVideo, 300);
            }
        }, true);

        // Bind to existing + new videos
        var mutationObserver = new MutationObserver(function() {
            document.querySelectorAll('video').forEach(function(v) {
                if (!v.__rdBound) {
                    v.__rdBound = true;
                    v.addEventListener('play', function() { setTimeout(checkVideo, 300); });
                    v.addEventListener('pause', function() { setTimeout(checkVideo, 500); });
                    v.addEventListener('ended', function() { setTimeout(checkVideo, 500); });
                }
            });
        });
        mutationObserver.observe(document.body, { childList: true, subtree: true });

        // Bind to all current videos
        document.querySelectorAll('video').forEach(function(v) {
            v.__rdBound = true;
            v.addEventListener('play', function() { setTimeout(checkVideo, 300); });
            v.addEventListener('pause', function() { setTimeout(checkVideo, 500); });
            v.addEventListener('ended', function() { setTimeout(checkVideo, 500); });
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', startMonitoring);
    } else {
        startMonitoring();
    }

})();