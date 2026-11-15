(function () {
  if (window.__tt) return;
  window.__tt = true;

  /* ---------- 1. Phát nền: luôn báo trang đang "hiển thị" ---------- */
  try {
    Object.defineProperty(document, 'hidden', { get: function () { return false; }, configurable: true });
    Object.defineProperty(document, 'visibilityState', { get: function () { return 'visible'; }, configurable: true });
    Object.defineProperty(document, 'webkitHidden', { get: function () { return false; }, configurable: true });
    Object.defineProperty(document, 'webkitVisibilityState', { get: function () { return 'visible'; }, configurable: true });
  } catch (e) {}
  var stop = function (e) { e.stopImmediatePropagation(); };
  ['visibilitychange', 'webkitvisibilitychange', 'pagehide', 'freeze'].forEach(function (ev) {
    window.addEventListener(ev, stop, true);
    document.addEventListener(ev, stop, true);
  });
  window.addEventListener('blur', function (e) { if (e.target === window) e.stopImmediatePropagation(); }, true);

  /* ---------- 2. Xóa dữ liệu quảng cáo trong JSON của YouTube ---------- */
  var AD_KEYS = ['adPlacements', 'playerAds', 'adSlots', 'adBreakHeartbeatParams'];
  function strip(o) {
    try {
      if (o && typeof o === 'object') {
        for (var i = 0; i < AD_KEYS.length; i++) {
          if (AD_KEYS[i] in o) delete o[AD_KEYS[i]];
        }
        if (o.playerResponse) strip(o.playerResponse);
      }
    } catch (e) {}
    return o;
  }
  var origParse = JSON.parse;
  JSON.parse = function () { return strip(origParse.apply(this, arguments)); };
  if (window.Response && Response.prototype.json) {
    var origJson = Response.prototype.json;
    Response.prototype.json = function () { return origJson.call(this).then(strip); };
  }
  try {
    var ipr;
    Object.defineProperty(window, 'ytInitialPlayerResponse', {
      configurable: true,
      get: function () { return ipr; },
      set: function (v) { ipr = strip(v); }
    });
  } catch (e) {}

  /* ---------- 3. Ẩn khung quảng cáo / banner ---------- */
  var CSS = [
    'ytm-promoted-sparkles-web-renderer', 'ytm-promoted-video-renderer',
    'ytm-companion-ad-renderer', 'ad-slot-renderer', 'ytm-ad-slot-renderer',
    'ytm-display-ad-renderer', 'ytm-in-feed-ad-layout-renderer',
    'ytm-statement-banner-renderer', 'ytm-mealbar-promo-renderer',
    'ytm-banner-promo-renderer', 'ytm-paid-content-overlay-renderer',
    'ytm-merch-shelf-renderer', '.ytp-ad-module', '.video-ads',
    '.ytp-ad-overlay-container', 'ytd-ad-slot-renderer',
    'ytm-promoted-sparkles-text-search-renderer', 'ytm-upsell-dialog-renderer'
  ].join(',') + '{display:none!important}';
  function addCss() {
    if (document.getElementById('__ttcss')) return;
    var root = document.head || document.documentElement;
    if (!root) return;
    var s = document.createElement('style');
    s.id = '__ttcss';
    s.textContent = CSS;
    root.appendChild(s);
  }

  /* ---------- 4. Tự tua qua quảng cáo video nếu còn lọt ---------- */
  var mutedByAd = false;
  setInterval(function () {
    addCss();
    var player = document.querySelector('.html5-video-player');
    var v = document.querySelector('video');
    if (player && v) {
      if (player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting')) {
        if (!v.muted) { v.muted = true; mutedByAd = true; }
        if (isFinite(v.duration) && v.duration > 0) v.currentTime = v.duration;
      } else if (mutedByAd) {
        v.muted = false;
        mutedByAd = false;
      }
    }
    var btns = document.querySelectorAll(
      '.ytp-ad-skip-button,.ytp-ad-skip-button-modern,.ytp-skip-ad-button,.ytp-ad-skip-button-container button'
    );
    for (var i = 0; i < btns.length; i++) btns[i].click();

    /* Báo trạng thái phát cho app (để hiện thông báo điều khiển) */
    if (window.TanTube && v) {
      var st = v.paused ? 0 : 1;
      var title = (document.title || '').replace(/ - YouTube$/, '');
      if (st !== window.__ttSt || title !== window.__ttTitle) {
        window.__ttSt = st;
        window.__ttTitle = title;
        try { TanTube.onState(st, title); } catch (e) {}
      }
    }
  }, 300);

  /* ---------- Báo khi hết video (cho hẹn giờ "hết video") ---------- */
  function currentId() {
    try { return new URL(location.href).searchParams.get('v'); } catch (e) { return null; }
  }
  function playNext() {
    var cur = currentId();
    var links = document.querySelectorAll('a[href*="/watch?v="]');
    for (var i = 0; i < links.length; i++) {
      var a = links[i];
      if (a.offsetParent === null) continue; // bỏ qua link bị ẩn (quảng cáo)
      var id = null;
      try { id = new URL(a.href, location.href).searchParams.get('v'); } catch (e) {}
      if (id && id !== cur) { a.click(); return; }
    }
  }
  document.addEventListener('ended', function (e) {
    if (!e.target || e.target.tagName !== 'VIDEO') return;
    if (document.querySelector('.html5-video-player.ad-showing')) return;
    var closing = false, auto = true;
    if (window.TanTube) {
      try { closing = TanTube.onEnded(); } catch (x) {}
      try { auto = TanTube.autoNext(); } catch (x) {}
    }
    if (closing || !auto) return;
    var id = currentId();
    // Chờ YouTube tự chuyển (danh sách phát / tự động phát); nếu không thì tự mở video kế
    setTimeout(function () { if (currentId() === id) playNext(); }, 2500);
  }, true);

  /* ---------- Tự trả lời "Bạn vẫn đang xem chứ?" ---------- */
  setInterval(function () {
    var btns = document.querySelectorAll('button, [role="button"]');
    for (var i = 0; i < btns.length; i++) {
      var t = (btns[i].innerText || '').trim().toLowerCase();
      if (t !== 'có' && t !== 'yes' && t !== 'tiếp tục xem') continue;
      var box = btns[i].closest('[role="dialog"], tp-yt-paper-dialog, ytm-dialog, .dialog, bottom-sheet-container');
      var txt = box ? (box.innerText || '').toLowerCase() : '';
      if (txt.indexOf('vẫn đang xem') >= 0 || txt.indexOf('still watching') >= 0 || txt.indexOf('tiếp tục xem') >= 0) {
        btns[i].click();
        var v = document.querySelector('video');
        if (v && v.paused) v.play();
      }
    }
  }, 2000);

  /* ---------- 5. Lệnh từ thông báo ---------- */
  window.__ttToggle = function () {
    var v = document.querySelector('video');
    if (!v) return;
    if (v.paused) v.play(); else v.pause();
  };
  window.__ttPause = function () {
    var v = document.querySelector('video');
    if (v) v.pause();
  };
})();
