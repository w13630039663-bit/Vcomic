package com.venera.desktop.platform

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Keeps one desktop process as the callback owner.
 *
 * Browser OAuth starts a second JVM with `pixiv://account...`. That process cannot exchange the
 * code itself because the PKCE verifier lives only in the running app, so it forwards its launch
 * arguments over loopback and exits. The file lock selects the primary; the random token prevents
 * unrelated local processes from injecting a callback.
 */
internal class DesktopSingleInstance private constructor(
    private val lockChannel: FileChannel,
    private val lock: FileLock,
    private val serverSocket: ServerSocket?,
    private val executor: ExecutorService,
    private val expectedToken: String,
) : Closeable {

    private val listeners = CopyOnWriteArrayList<(List<String>) -> Unit>()

    fun addLaunchListener(listener: (List<String>) -> Unit): Closeable {
        listeners += listener
        return Closeable { listeners -= listener }
    }

    private fun startListening() {
        val socket = serverSocket ?: return
        executor.execute {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: continue
                runCatching { handleClient(client) }
            }
        }
    }

    private fun handleClient(socket: Socket) {
        socket.use { client ->
            client.soTimeout = SocketTimeoutMillis
            val reader = BufferedReader(client.getInputStream().reader(StandardCharsets.UTF_8))
            val writer = BufferedWriter(client.getOutputStream().writer(StandardCharsets.UTF_8))
            val payload = readPayload(reader)
            when {
                payload == null -> writer.write("INVALID\n")
                !constantTimeEquals(payload.token, expectedToken) -> writer.write("FORBIDDEN\n")
                listeners.isEmpty() -> writer.write("BUSY\n")
                else -> {
                    listeners.forEach { listener -> runCatching { listener(payload.arguments) } }
                    writer.write("OK\n")
                }
            }
            writer.flush()
        }
    }

    override fun close() {
        runCatching { serverSocket?.close() }
        executor.shutdownNow()
        runCatching { executor.awaitTermination(1, TimeUnit.SECONDS) }
        runCatching { lock.release() }
        runCatching { lockChannel.close() }
    }

    sealed interface Acquisition {
        data class Primary(val coordinator: DesktopSingleInstance) : Acquisition
        data object ForwardedToPrimary : Acquisition
        data class Unavailable(val reason: String) : Acquisition
    }

    companion object {
        // 主实例绑定空闲端口并写入 dataRoot 端口文件，按数据根隔离多实例与测试，避免固定端口冲突。
        private const val DefaultPort = 45_963
        private const val ConnectTimeoutMillis = 1_500
        private const val SocketTimeoutMillis = 3_000
        private const val MaxArguments = 32
        private const val MaxArgumentLength = 8_192
        private const val Header = "VENERA_LAUNCH_V1"
        private const val ForwardAttempts = 20

        fun acquireOrForward(arguments: List<String>, dataRoot: File): Acquisition {
            val instanceDir = File(dataRoot, "instance").apply { mkdirs() }
            val lockPath = File(instanceDir, "venera-desktop.lock")
            val tokenPath = File(instanceDir, "venera-desktop.token")
            val portPath = File(instanceDir, "venera-desktop.port")
            val channel = runCatching {
                FileChannel.open(
                    lockPath.toPath(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                )
            }.getOrElse { return Acquisition.Unavailable("打不开单实例锁：${it.message.orEmpty()}") }

            val lock = runCatching { channel.tryLock() }.getOrNull()
            if (lock == null) {
                channel.close()
                return if (forward(arguments, tokenPath, portPath)) {
                    Acquisition.ForwardedToPrimary
                } else {
                    Acquisition.Unavailable("已有实例在运行，但这次回调没有送达。")
                }
            }

            val socket = runCatching {
                ServerSocket().apply {
                    reuseAddress = true
                    // 0 = 交给系统分配空闲端口，避免与同时存在的其它实例抢那一枚固定口。
                    bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
                }
            }.getOrNull()
            // 把真实端口落到 dataRoot 内的端口文件，转发方据此连接；绑定失败（socket 为空）时清掉旧口避免读到僵尸端口。
            runCatching {
                if (socket != null) {
                    portPath.writeText(socket.localPort.toString(), Charsets.UTF_8)
                } else {
                    portPath.delete()
                }
            }
            val token = generateToken()
            runCatching {
                tokenPath.writeText(token, Charsets.UTF_8)
            }.getOrElse {
                runCatching { socket?.close() }
                runCatching { lock.release() }
                runCatching { channel.close() }
                return Acquisition.Unavailable("单实例鉴权令牌写不进盘：${it.message.orEmpty()}")
            }

            val coordinator = DesktopSingleInstance(
                lockChannel = channel,
                lock = lock,
                serverSocket = socket,
                executor = Executors.newSingleThreadExecutor { runnable ->
                    Thread(runnable, "venera-launch-listener").apply { isDaemon = true }
                },
                expectedToken = token,
            )
            coordinator.startListening()
            return Acquisition.Primary(coordinator)
        }

        private fun generateToken(): String {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }

        private fun constantTimeEquals(a: String, b: String): Boolean =
            MessageDigest.isEqual(
                a.toByteArray(StandardCharsets.UTF_8),
                b.toByteArray(StandardCharsets.UTF_8),
            )

        private fun forward(arguments: List<String>, tokenPath: File, portPath: File): Boolean {
            val token = runCatching { tokenPath.readText(Charsets.UTF_8).trim() }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?: return false
            // 端口文件读不到时退回历史固定口，兼容升级前已在运行、尚未写端口文件的旧主实例。
            val port = runCatching { portPath.readText(Charsets.UTF_8).trim().toIntOrNull() }
                .getOrNull()
                ?: DefaultPort
            repeat(ForwardAttempts) { attempt ->
                val forwarded = runCatching {
                    Socket().use { socket ->
                        socket.connect(
                            InetSocketAddress(InetAddress.getByName("127.0.0.1"), port),
                            ConnectTimeoutMillis,
                        )
                        socket.soTimeout = SocketTimeoutMillis
                        val writer = BufferedWriter(socket.getOutputStream().writer(StandardCharsets.UTF_8))
                        writer.write(Header)
                        writer.newLine()
                        writer.write(token)
                        writer.newLine()
                        arguments.take(MaxArguments).forEach { argument ->
                            require(
                                argument.length <= MaxArgumentLength &&
                                    !argument.contains('\n') &&
                                    !argument.contains('\r'),
                            ) { "Invalid launch argument" }
                            writer.write(argument)
                            writer.newLine()
                        }
                        writer.newLine()
                        writer.flush()
                        BufferedReader(socket.getInputStream().reader(StandardCharsets.UTF_8)).readLine() == "OK"
                    }
                }.getOrDefault(false)
                if (forwarded) return true
                if (attempt < ForwardAttempts - 1) Thread.sleep(250)
            }
            return false
        }

        private data class Payload(val token: String, val arguments: List<String>)

        private fun readPayload(reader: BufferedReader): Payload? {
            if (reader.readLine() != Header) return null
            val token = reader.readLine()?.takeIf { it.isNotEmpty() && it.length <= 128 } ?: return null
            val arguments = mutableListOf<String>()
            while (true) {
                val argument = reader.readLine() ?: return null
                if (argument.isEmpty()) return Payload(token, arguments)
                if (arguments.size >= MaxArguments || argument.length > MaxArgumentLength) return null
                arguments += argument
            }
        }
    }
}
