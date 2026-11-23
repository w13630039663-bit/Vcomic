# 随包字体（2026-11-09 字体统一批）

**本目录已随包 MiSans Global 四档**（各约 7.7 MB，合计 30.7 MB；jar 从 11 MB → 33 MB）。
替换或升级字体时，文件名必须是：

```
MiSans-Regular.ttf     → FontWeight.Normal
MiSans-Medium.ttf      → FontWeight.Medium
MiSans-SemiBold.ttf    → FontWeight.SemiBold
MiSans-Bold.ttf        → FontWeight.Bold
```

四个齐才启用（见 `ui/theme/VeneraTypography.kt` 的 `VeneraFonts`）：
只放一部分会让缺的那档被 Skia 伪加粗，中文字形会糊，所以宁可整体回退系统字体。
`DesktopBundledFontTest` 钉着「四档在 classpath 上且真被启用」，删了会红。

⚠️ 打包白名单：`desktop/build.gradle.kts` 的
`sourceSets["main"].resources.include(...)` 里有 `"fonts/*.ttf"` 一颗 ——
漏了不会报错，只会静默回退系统字体（该文件第 444 行的注释早就警告过这条）。

## 字体来源与授权

- 字体：**MiSans Global**（小米 HyperOS 官方字体，含简体中文 / 繁体 / 拉丁 / 数字 / 标点）。
- 获取：https://hyperos.mi.com/font/ （官方下载页，选择 MiSans Global，TTF 格式）。
- 授权：小米官方字体许可允许**免费商用与随软件分发**，但不得单独售卖字体本体、
  不得修改字形后再以 MiSans 名义分发。分发本软件时须保留本说明与字体原始授权文本。
- 若后续替换为其它字体（如思源黑体 / Noto Sans SC），只需替换本目录四个文件并在
  「关于与更新」页的第三方许可段同步更新来源与授权。

## 回退行为

本目录为空 ⇒ 用系统默认字体（Windows: Segoe UI + 微软雅黑中文回退；macOS: 苹方）。
CI 与干净 checkout 不会因为缺字体而崩，只是观感不是设计稿那套（`VeneraFonts.bundled` 会报 false）。
