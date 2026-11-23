package com.venera.desktop.reader

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * AI 超分辨率与画质增强组件状态。
 */
internal sealed interface AiEngineStatus {
    data object NotInstalled : AiEngineStatus
    data class Downloading(val progressPercent: Int) : AiEngineStatus
    data class Ready(val localPath: String, val sizeBytes: Long) : AiEngineStatus
    data class Failed(val error: String) : AiEngineStatus
}

/**
 * AI 超分与去网纹引擎按需下载管理器。
 */
internal object DesktopAiUpscaleManager {
    private val _status = MutableStateFlow<AiEngineStatus>(AiEngineStatus.NotInstalled)
    val status: StateFlow<AiEngineStatus> = _status.asStateFlow()

    private const val ESTIMATED_TOTAL_MB = 25

    /** 获取引擎组件存储根目录。 */
    fun getEngineDir(): File {
        val userHome = System.getProperty("user.home")
        val dir = File(userHome, ".venera/ai_engine")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** 初始化并检查本地组件就绪状态。 */
    fun checkLocalState() {
        val dir = getEngineDir()
        val executable = File(dir, if (System.getProperty("os.name").lowercase().contains("win")) "realesrgan-ncnn-vulkan.exe" else "realesrgan-ncnn-vulkan")
        if (executable.exists() && executable.length() > 0) {
            val totalSize = dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
            _status.value = AiEngineStatus.Ready(executable.absolutePath, totalSize)
        } else {
            _status.value = AiEngineStatus.NotInstalled
        }
    }

    /** 卸载并清理本地模型与引擎文件。 */
    fun cleanEngine() {
        val dir = getEngineDir()
        if (dir.exists()) {
            dir.deleteRecursively()
        }
        _status.value = AiEngineStatus.NotInstalled
    }

    /** 预估体积文本描述。 */
    fun estimatedSizeText(): String = "~${ESTIMATED_TOTAL_MB} MB"
}
