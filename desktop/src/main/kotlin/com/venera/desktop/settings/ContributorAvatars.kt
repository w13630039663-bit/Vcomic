package com.venera.desktop.settings

/**
 * 贡献者头像加载器：头像图片已外置至 `resources/contributors/<login>.img`，
 * 离线从 classpath 读取字节，避免在 Kotlin 源码中内嵌巨型 Base64 字符串。
 */
internal object ContributorAvatars {
    private val knownLogins = setOf(
        "wgh136",
        "ynyxx",
        "lings03",
        "deltamaya",
        "UjuiUjuMandan",
        "pkuislm",
        "w13630039663-bit",
        "leimi",
    )

    /** 读取指定 GitHub 登录名对应的编码图像字节；未收录或读取失败返回 null。 */
    fun bytesOf(login: String): ByteArray? {
        if (login !in knownLogins) return null
        return ContributorAvatars::class.java.classLoader
            ?.getResourceAsStream("contributors/$login.img")
            ?.use { it.readBytes() }
    }
}
