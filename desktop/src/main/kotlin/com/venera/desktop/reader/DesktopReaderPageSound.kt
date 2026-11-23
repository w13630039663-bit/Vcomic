package com.venera.desktop.reader

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip

/**
 * 翻页音效：首次播放时合成一段 90ms 的沙沙声，之后复用同一个 [Clip]。
 *
 * 不加载外部音频：翻页声只是短促噪声，合成出来的没有版权负担，
 * 也不必往安装包里塞几百 KB 素材。
 */
internal object DesktopReaderPageSound {

    @Volatile
    private var enabled = true

    /** 音量 0..100（prefs.soundVolume）；合成波形幅度按比例缩放。 */
    @Volatile
    private var volumePercent = 80

    /** 混音器不可用（无声卡 / 被独占）时置位，之后不再重试，免得每次翻页都抛一次。 */
    private var unavailable = false

    private var clip: Clip? = null

    /** 跟随设置的「翻页音效」开关。 */
    fun setEnabled(value: Boolean) {
        enabled = value
    }

    /** 跟随设置的音量档（0..100）。 */
    fun setVolume(percent: Int) {
        volumePercent = percent.coerceIn(0, 100)
    }

    /** 播放一次；同一时刻只保留一段，连续翻页不会叠成噪音。 */
    fun play() {
        if (!enabled || unavailable || volumePercent <= 0) return
        val audio = clip ?: synthesize()?.also { clip = it } ?: return
        runCatching {
            if (audio.isRunning) audio.stop()
            audio.framePosition = 0
            audio.start()
        }
    }

    private fun synthesize(): Clip? {
        val result = runCatching {
            val sampleRate = 22_050
            val format = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)
            val frames = (sampleRate * 0.09f).toInt()
            val data = ByteArray(frames * 2)
            val gain = volumePercent / 100f
            var lowpass = 0.0
            var seed = 0x9E3779B97F4A7C15uL.toLong()
            for (i in 0 until frames) {
                val t = i.toDouble() / frames
                // 起音 12ms 冲起来，之后指数衰落，像纸页从指尖滑过。
                val attack = (t / 0.12).coerceAtMost(1.0)
                val envelope = attack * kotlin.math.exp(-7.0 * t)
                seed = seed * 6364136223846793005L + 1442695040888963407L
                val noise = ((seed ushr 40).toInt() / 8_388_608.0) - 1.0
                lowpass = lowpass * 0.55 + noise * 0.45
                val sample = (lowpass * envelope * 0.35 * gain * Short.MAX_VALUE)
                    .toInt()
                    .coerceIn(-32_768, 32_767)
                data[i * 2] = (sample and 0xFF).toByte()
                data[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
            }
            AudioSystem.getClip().apply { open(format, data, 0, data.size) }
        }.getOrNull()
        if (result == null) unavailable = true
        return result
    }
}
