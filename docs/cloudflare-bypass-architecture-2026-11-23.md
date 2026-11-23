# Cloudflare 过盾方案与问题复盘（2026-11-23）

## 一、现状：双轨过盾架构

桌面端目前按站点防护强度分两条路处理 Cloudflare 挑战：

### 1. Edge + CDP 抓 Cookie 共享中心（主供漫画源、图床与静默请求）

- **组件**：`engine-probe/DesktopCloudflareCenter.kt` + `platform/DesktopCloudflareBridge.kt` + `DesktopCookieJar`
- **接入范围**：34 个漫画源（`DesktopJsHost`）、全局图片加载（`DesktopImagePipeline`）与画廊标准引擎（`DesktopHttpEngine`）。
- **工作流**：
  1. 平时走 `OkHttp` 高并发请求。
  2. 收到 HTTP `403/503` 且命中 CF 挑战特征（`Cf-Mitigated: challenge`、`Just a moment`、`cf_chl_`、`cf-turnstile`、`challenge-platform`）时拦截。
  3. 豁免规则：禁漫域全族豁免（S3 预签名 403 非 CF 盾）；带 `NoInteractiveBypassTag` 的请求（画廊日榜、标签联想）允许复用已缓存的 `cf_clearance` 与配套 UA，但撞盾时不弹窗。
  4. 过盾桥：`.json/.xml` 接口自动转为同域根路径 HTML 导航；先无头 Edge 跑 6.5 秒（必须真实抓到 `cf_clearance` 才算通过），未过则弹出独立交互小窗（90 秒超时）；启动参数跟随应用内手动/直连代理配置。
  5. 抓回 `HttpOnly` Cookie 与浏览器 `User-Agent` 写回落盘 `DesktopCookieJar`，`OkHttp` 带同套 UA + Cookie 重试。

### 2. 内嵌 WebView2 页内同源 XHR 桥（专攻“绑定浏览器 TLS 指纹”的图库）

- **组件**：`gallery/data/DesktopKonachanBridge.kt`（壳根挂载隐形 `WebView2` 宿主 `DesktopKonachanWebViewHost`）
- **接入范围**：
  - `Konachan R18`（`konachan.com`）：`DesktopKonachanComClient` 主搜索与详情
  - `Konachan`（`konachan.net`）：`DesktopKonachanClient` 主动搜索与详情
  - `Safebooru`（`safebooru.donmai.us`）：`SafebooruBoard` 在 `danbooru.donmai.us` 主站失败后自动回退并记忆切桥
- **工作流**：
  1. 通过 `Mutex` 串行排队，首次或跨站切换时先导航至目标站轻量页 `/robots.txt`（避免加载整页图片拖慢首屏）。
  2. 在 `WebView2` 页面上下文内直接发起同源 `XMLHttpRequest` 拉取 JSON，请求全程由 Chromium 内核发出，TLS 指纹与 `cf_clearance` 100% 匹配。
  3. 若 `/robots.txt` 未触发过盾导致 XHR 回 `403/503`，自动跳转至站点首页并把宿主盒放大居中亮出（`zIndex(100f)` + 半透明遮罩拦截穿透点击），用户点完验证框后自动重发 XHR。
  4. 闲置保活 5 分钟，连续搜索与翻页零冷启动；`DesktopKonachanWebViewHost` 为 `WebView` 兜底提供 `LocalAwtWindow`。

---

## 二、典型坑点与关键约束

1. **TLS 指纹绑定问题**：`konachan.com` 等站点的 CF 盾把 `cf_clearance` 与浏览器 TLS 指纹（JA3/JA4）绑定。Edge 拿到的 `cf_clearance` 转交给 Java `OkHttp` 重试时仍会回 403，必须由真浏览器内核（`WebView2`）在页内发请求。
2. **不能直接拿 `.json` URL 过盾 + 必须校验 `cf_clearance`**：Edge 直接打开 `/post.json` 等 API 路径时常只拿到 1 枚站方普通 Cookie（而非 `cf_clearance`）就退出，导致后续重试继续撞 403。
3. **12s 预算截断过盾**：`GalleryLegGuard` 默认 12 秒预算给日榜并发，单站首次过盾（无头 + 人工点击 + 重试）需放宽至 45 秒。
4. **`WebView2` 初始化与挂载层级**：
   - 修改 Compose 状态（`mountRequested` / `challengeVisible`）必须切回 `Dispatchers.Main.immediate`，避免 IO 线程延迟触发重组。
   - `compose-webview2` 依赖 `LocalAwtWindow.current`，在部分桌面壳层可能为空，需显式兜底提供可见 AWT `Frame`；且遇到 `st.error != null` 应立即中断等待并报出。
   - 宿主组件在 `DesktopLargeScreenChrome` 中必须配高 `zIndex` 与遮罩，否则会被后绘制的画廊瀑布流图片盖住导致人机验证框无法点击。
