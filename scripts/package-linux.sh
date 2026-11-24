#!/usr/bin/env bash
# ==============================================================================
# venera-desktop Linux 打包脚本（AppImage + 便携绿色包 tar.gz）
#
# 依赖环境：
#   - JDK 25 (由 Gradle Toolchain 自动管理)
#   - bash, tar, gzip, curl
#   - 可选：appimagetool（未安装时自动尝试获取独立运行时或提示构建便携包）
# ==============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

cd "${ROOT_DIR}"

echo "==> [1/4] 构建 Linux 独立应用镜像 (createDistributable)..."
./gradlew :desktop:createDistributable --no-daemon

APP_IMAGE_DIR="${ROOT_DIR}/desktop/build/compose/binaries/main/app/venera-desktop"
OUT_DIR="${ROOT_DIR}/desktop/build/compose/binaries/main/installer"
mkdir -p "${OUT_DIR}"

if [ ! -d "${APP_IMAGE_DIR}" ]; then
    echo "错误：未找到构建出的应用目录: ${APP_IMAGE_DIR}" >&2
    exit 1
fi

VERSION="1.0.0"
if [ -f "${ROOT_DIR}/desktop/build.gradle.kts" ]; then
    DETECTED_VER=$(grep -o 'val desktopPackageVersion = "[^"]*"' "${ROOT_DIR}/desktop/build.gradle.kts" | cut -d'"' -f2 || true)
    if [ -n "${DETECTED_VER}" ]; then
        VERSION="${DETECTED_VER}"
    fi
fi

echo "==> [2/4] 打包便携绿色免安装包 (tar.gz)..."
TARBALL_NAME="VComic-${VERSION}-linux-x64.tar.gz"
TARBALL_PATH="${OUT_DIR}/${TARBALL_NAME}"

tar -czf "${TARBALL_PATH}" -C "${APP_IMAGE_DIR}/.." "venera-desktop"
echo "已生成便携包: ${TARBALL_PATH}"

echo "==> [3/4] 组装 AppDir 目录结构..."
APP_DIR="${ROOT_DIR}/desktop/build/tmp/AppDir"
rm -rf "${APP_DIR}"
mkdir -p "${APP_DIR}/usr/bin"
mkdir -p "${APP_DIR}/usr/lib/venera-desktop"
mkdir -p "${APP_DIR}/usr/share/icons/hicolor/256x256/apps"

cp -r "${APP_IMAGE_DIR}/"* "${APP_DIR}/usr/lib/venera-desktop/"

# 图标复制
ICON_SRC="${ROOT_DIR}/desktop/src/main/resources/vcomic_icon.png"
if [ -f "${ICON_SRC}" ]; then
    cp "${ICON_SRC}" "${APP_DIR}/vcomic_icon.png"
    cp "${ICON_SRC}" "${APP_DIR}/usr/share/icons/hicolor/256x256/apps/vcomic_icon.png"
else
    # 兜底空白或简单占位
    touch "${APP_DIR}/vcomic_icon.png"
fi

# 写入标准 .desktop 文件
cat << 'EOF' > "${APP_DIR}/venera.desktop"
[Desktop Entry]
Type=Application
Name=VComic
Comment=VComic（漫画与图库）
Exec=AppRun %u
Icon=vcomic_icon
Terminal=false
Categories=Utility;Graphics;Viewer;
MimeType=x-scheme-handler/pixiv;x-scheme-handler/venera;
StartupWMClass=venera-desktop
EOF

# 写入 AppRun 启动入口
cat << 'EOF' > "${APP_DIR}/AppRun"
#!/usr/bin/env bash
SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export PATH="${SELF_DIR}/usr/bin:${PATH}"
export LD_LIBRARY_PATH="${SELF_DIR}/usr/lib/venera-desktop/lib:${LD_LIBRARY_PATH:-}"
exec "${SELF_DIR}/usr/lib/venera-desktop/bin/venera-desktop" "$@"
EOF

chmod +x "${APP_DIR}/AppRun"
chmod +x "${APP_DIR}/usr/lib/venera-desktop/bin/venera-desktop" 2>/dev/null || true

echo "==> [4/4] 尝试生成 AppImage 单文件..."
APPIMAGE_NAME="VComic-${VERSION}-linux-x64.AppImage"
APPIMAGE_PATH="${OUT_DIR}/${APPIMAGE_NAME}"

# 寻找或下载 appimagetool
APPIMAGE_TOOL=""
if command -v appimagetool >/dev/null 2>&1; then
    APPIMAGE_TOOL="appimagetool"
elif [ -f "${ROOT_DIR}/desktop/build/tmp/appimagetool" ]; then
    APPIMAGE_TOOL="${ROOT_DIR}/desktop/build/tmp/appimagetool"
fi

if [ -n "${APPIMAGE_TOOL}" ]; then
    echo "使用 ${APPIMAGE_TOOL} 打包 AppImage..."
    ARCH=x86_64 "${APPIMAGE_TOOL}" "${APP_DIR}" "${APPIMAGE_PATH}"
    chmod +x "${APPIMAGE_PATH}"
    echo "AppImage 打包成功: ${APPIMAGE_PATH}"
else
    echo "提示：系统中未检测到 appimagetool，跳过单文件 AppImage 压缩。"
    echo "用户可在 Linux 机器安装 appimagetool 或直接使用已生成的便携包：${TARBALL_PATH}"
fi

echo "================================================================="
echo "Linux 产物打包输出完成："
ls -lh "${OUT_DIR}"
echo "================================================================="
