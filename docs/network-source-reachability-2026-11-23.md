# 源站直连可达性实测（2026-11-23）

## 结论速览

- 漫画 34 个源：直连能正常拿到响应的 6 个；通但被站方防护挡住的 6 个；直连彻底不通的 17 个；与网络无关的 5 个（含 3 个本地自建）。
- 图库 7 个源：直连可用的 0 个；能握手但被 Cloudflare 挑战页挡住的 2 个（Safebooru、Konachan）；其余 5 个超时或被重置。
- `GallerySite.kt` 里"国内裸连可达 / 直连 200"等注释来自 2026-10 的实测，本次复测已不成立，按注释做降级判断会踩空。

## 环境与方法

- 机器：本机 Windows，Clash Verge **直连模式**（界面「直连」高亮），TUN 开启、DNS fake-ip（`198.18.0.0/16`、`2001:2::/64`）。因此所有探测的出口都是本机公网 IP，没有节点参与。
- 探测：`curl`（schannel），单域超时 6–20 s；先测根路径，再对关键站补测真实 API 路径与图床域名。
- 判定口径：
  - `tls` 有值 + 有 HTTP 码 = 握手成功；
  - `Connection was reset` = 握手阶段被重置（快）；
  - `Connection timed out`，20 s 无响应 = 丢包型阻断；
  - 403 且响应体含 CF 挑战特征 = 通但被盾拦，业务不可用。

## 漫画源（34）

### A. 直连可用（6）

| 源 | 域名 | 结果 |
| --- | --- | --- |
| Comick | comick.art | 200 |
| Ikmmh | www.ikmmh.com | 200 |
| 漫画人 | www.manhuaren.com | 200 |
| 再漫画 | account-api.zaimanhua.com | 200 |
| 少年ジャンプ＋ | shonenjumpplus.com | 200，但响应为 HTML，需过盾/token |
| MangaDex | api.mangadex.org | 308（重定向，通），同样拿到 HTML 盾页 |

### B. 握手成功但被站方挡住（6）

| 源 | 域名 | 结果 |
| --- | --- | --- |
| 包子漫画 | appcn.baozimh.com | 403 |
| ComicWalker | mobileapp.comic-walker.com | 403 |
| Goda | v2.apikk.top | 403 |
| MyComic | mycomic.com | 403 |
| Ykmh | www.ykmh.net | 403，人机验证 |
| 漫画1234 | b.amh1234.com | 根路径 200；证书有效期至 2027-02-10，当前有效 |

### C. 直连不通（17）

- 重置型：Komiic（komiic.com）、H-Comic（h-comic.com）、紳士漫畫（wn01.link）、热辣漫画（www.manga2026.com）。
- 超时型：ehentai（e-hentai.org）、Gelbooru（gelbooru.com）、Hitomi（hitomi.la）、jcomic（jcomic.net）、漫画柜（www.manhuagui.com）、漫蛙吧（mwuu.cc）、漫小肆（www.mxshm.top）、18漫画（18mh.org）、nhentai（nhentai.net）、Picacg（picaapi.picacomic.com）、CCC（api.creative-comic.tw）。
- 拷贝漫画两条腿：api.copy2000.online 连不上端口，api.copy-manga.com 超时。

### D. 与网络无关（5）

- 嗨皮漫画：`baseUrl` 为 `m.happymh.com`，该域当前无 DNS 记录，属源配置过期。
- 禁漫：接口返回 502（站方故障）；其图床 `cdn-msp.jmapinodeudzn.net` 反而 200。
- Kavita / Komga / Lanraragi：本地自建服务，需要用户自己的服务器地址，不适用公网可达性判断。

## 图库（7 个内置源 + 辅助域）

| 源 | 主域 | 直连结果 | 相关域 |
| --- | --- | --- | --- |
| yande.re | yande.re | 超时（6 s / 15 s 各复测，3 次均无响应） | — |
| Gelbooru | gelbooru.com | 超时 | img4.gelbooru.com 超时 |
| Safebooru | safebooru.donmai.us | 403 Cloudflare 挑战页 | cdn.donmai.us 同为挑战页 |
| Pixiv | www.pixiv.net | 超时 | app-api.pixiv.net、oauth.secure.pixiv.net、i.pximg.net 全超时 |
| Konachan | konachan.net | 403 Cloudflare 挑战页 | — |
| Konachan R18 | konachan.com | 超时 | — |
| TBIB | tbib.org | 连接被重置 | — |

辅助域：danbooru.donmai.us 超时；saucenao.com 连接被重置。

补充：`safebooru.donmai.us/posts.json`、`konachan.net/post.json`、`cdn.donmai.us` 三个真实接口路径均返回 CF 挑战页（响应体 5.3–5.5 KB，含挑战特征），不是站点级 403。

## 与既有记录的差异

| 记录位置 | 原记录 | 本次实测 |
| --- | --- | --- |
| `GallerySite.kt:47-48` | Safebooru 走 CF、国内裸连可达；danbooru 主站不可达 | 前半失效（现为 403 挑战页）；后半仍成立 |
| `GallerySite.kt:102` | Konachan `.net` 匿名直连 200 | 现为 403 挑战页 |
| `GallerySite.kt:68,83` | pixiv 匿名搜索直连 200 | 网页/App API/OAuth/图床全域超时 |
| 2026-11-23 00:58 运行日志 | yande.re 腿成功（推荐首屏 131 张） | 同一时段之后连续 3 次复测全超时，属时通时断 |

## 后续

1. 切「规则/全局」+ 节点后用同一套探测复测，得到直连/代理对照表，区分"节点可恢复"与"站点自身故障"。
2. 建议把 `GallerySite.kt` 中相关注释改成带日期的复测记录，避免按过期结论设计降级策略。
3. 客户端侧不改：这类失败已统一走 `platform/DesktopErrorCopy.kt` 归档并上报 `DesktopErrorBus`，屏上不再出现异常原文。

## 追加：Cloudflare 优先实测

问题：把解析改到 Cloudflare 边缘地址（"CF 优选"）能不能救回被直连阻断的源？

### 第一步：先看谁真的在 CF 后面

用阿里 DoH（`dns.alidns.com`，JSON）拿真实 A 记录。结果分两类：

| 类别 | 域名 | 解析结果 |
| --- | --- | --- |
| 真实 CF 地址 | komiic.com、tbib.org、www.manga2026.com、wn01.link、saucenao.com、safebooru.donmai.us、konachan.net、www.ykmh.net、appcn.baozimh.com、www/api.mangacopy.com、comick.art | 104.2x / 172.6x 段 |
| 明显被污染 | hitomi.la、nhentai.net、e-hentai.org、danbooru.donmai.us、18mh.org、www.pixiv.net → Facebook 段；yande.re → Twitter 段；i.pximg.net → Yahoo 段；www.manhuagui.com → Facebook 段；jcomic.net / gelbooru.com / mwuu.cc / api.copy* → 各类无关段 | 与站点无关的第三方地址 |

补充：`cloudflare-dns.com` 自身的 DoH 端点在国内直连也被重置，所以连"用 CF 的 DoH 查 CF 的站"这条路都走不通。

### 第二步：用真实 CF 地址 + 正确 SNI 复测

| 域名 | CF 地址 | 结果 |
| --- | --- | --- |
| comick.art | 104.21.77.17 | 200 |
| www.ykmh.net | 104.21.61.188 | 403 挑战页 |
| appcn.baozimh.com | 104.20.20.239 | 403 挑战页 |
| safebooru.donmai.us | 104.26.11.39 | 403 挑战页 |
| konachan.net | 104.21.85.89 | 403 挑战页 |
| komiic.com | 104.26.9.161 | 连接被重置 |
| www.manga2026.com | 104.21.11.252 | 连接被重置 |
| wn01.link | 104.21.95.168 | 连接被重置 |
| saucenao.com | 104.26.15.28 | 连接被重置 |
| tbib.org | 104.21.88.140 | 连接被重置 |
| api.mangacopy.com | 104.21.50.160 | 连接被重置 |
| www.mangacopy.com | 172.67.164.36 | 连接被重置 |

### 第三步：同域名换 8 组 CF 边缘地址

`www.mangacopy.com` 与 `komiic.com`，各对 8 个 CF 地址（104.16.132.229 / 104.17.0.1 / 104.18.0.1 / 172.64.0.1 / 172.67.0.1 / 162.159.0.1 / 188.114.96.1 / 104.21.50.160）复测：**16 次全部重置或超时，0 次成功**。

### 结论

- 阻断绑定的对象是 **SNI 域名**，不是 CF 的 IP：同一批 CF 地址上，`ykmh`/`safebooru`/`konachan.net`/`包子`/`comick` 能握手并返回 403/200，而 `komiic`/`manga2026`/`wn01`/`saucenao`/`tbib`/`mangacopy` 在同一地址上直接重置；同一域名换 8 组 CF 地址又全灭。
- 因此 **CF 优选净增 0 个源**：能过的本来就是"域名未被列入阻断名单"的那几个，它们的问题在 CF 的 WAF 挑战，而不是 IP；被阻断的域名换多少 CF 地址都过不去。
- `www.mangacopy.com` 之前直连 200，与"它解析在 CF 前"的事实不一致，说明本机解析给它的不是这组 CF 地址；强行钉到 CF 边缘地址后同样被重置。
- 真正能改变结果的是换出口（代理节点），不是优选 IP。

### 复测（同夜稍后，环境未变）

环境复核：无 TUN 虚拟网卡、`198.18.x` 无路由、系统代理关闭、7897 端口在监听但直连模式只放行；google 超时、github 半通。即仍是纯直连。

全量重跑（漫画 30 项 + 图库 16 项）与首测一致：可用 7（Comick、漫画人、Ikmmh、MangaDex、漫画1234 的 `b.amh1234.com`、再漫画、少年ジャンプ＋），403 盾页 5，重置 4，超时 11，连不上端口 2，DNS 无记录 1；图库仍只有 Safebooru / Konachan / cdn.donmai.us 返回挑战页。

两处变化值得记：

- `www.mangacopy.com`：首测 200，复测连续三次全部重置——官网本身也开始掉线，说明阻断状态是波动的，不是固定名单。
- 漫画1234：源默认域 `b.amh1234.com` 稳定 200，证书有效期至 2027-02-10；此前日志里的 `certificate_expired` 不是这条默认域当前状态。

## 复测命令

```powershell
curl.exe -sS -o NUL -w "code=%{http_code} tls=%{time_appconnect} total=%{time_total}" --max-time 20 https://yande.re/
```
