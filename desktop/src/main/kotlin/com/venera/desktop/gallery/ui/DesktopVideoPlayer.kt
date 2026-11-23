package com.venera.desktop.gallery.ui

import com.venera.desktop.ui.theme.VeneraType
import com.venera.desktop.ui.DesktopPushButton
import com.venera.desktop.gallery.ui.GalleryTokens
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.ui.tokens.VeneraTokens
import uk.co.caprica.vlcj.player.base.State
import androidx.compose.foundation.Image
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/**
 * 桌面 booru 视频**内嵌播放器**（视频帧解码到内存、用 Compose 直接画进画面区）。
 *
 * 与旧"贴附浮窗"的分别是：这里**没有任何独立窗** —— [DesktopVideoEngine] 把 libvlc 解出的每一帧
 * 转成 [ImageBitmap] 画进这块 [Image]，加载/暂停/错误/进度全是我们自己的 Compose 控件（Venera 玻璃坞），
 * 于是视频真正成为界面的一部分、跟随主窗一起移动缩放、也不会盖住别处。
 *
 * ## 生命周期
 * 进组合建引擎起播、离组合释放（[DisposableEffect]）。翻图/换源/父层切走 ⇒ 一并停解码，绝不留后台空转。
 *
 * ## 无 VLC 如实回报
 * 引擎起播失败（本机没装 libvlc）⇒ [onUnavailable]，父层保留静帧 + 提示，不摆"点了没反应"的死画面。
 *
 * @param engineKey 变化即重建引擎重起播（详情页传 `post.uid`、播放器传 `current.uid`）。
 */
@Composable
internal fun DesktopVideoPlayer(
    mediaUrl: String,
    headers: Map<String, String>,
    engineKey: Any,
    onDismiss: () -> Unit,
    onUnavailable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens

    var phase by remember(engineKey) { mutableStateOf(State.NOTHING_SPECIAL) }
    var positionMs by remember(engineKey) { mutableLongStateOf(0L) }
    var durationMs by remember(engineKey) { mutableLongStateOf(0L) }
    var currentFrame by remember(engineKey) { mutableStateOf<ImageBitmap?>(null) }
    // 拖动进度条时：拖到的毫秒（覆盖显示 + 松手 seek），拖拽中标记（暂停轮询回写位置）。
    var scrubbing by remember { mutableStateOf(false) }
    var scrubMs by remember { mutableLongStateOf(0L) }

    val engine = remember(engineKey, mediaUrl) {
        DesktopVideoEngine(
            mediaUrl = mediaUrl,
            headers = headers,
            onPhase = { phase = it },
        )
    }

    DisposableEffect(engine) {
        onDispose { engine.release() }
    }

    // start() 与逐帧像素转换均在 Dispatchers.Default 执行，避免阻塞 EDT 冻结主窗，仅在有新帧时投递到主线程。
    LaunchedEffect(engine) {
        withContext(Dispatchers.Default) {
            val started = engine.start()
            if (!started) {
                onUnavailable()
                return@withContext
            }
            var lastTick = 0L
            while (isActive) {
                if (engine.hasNewFrame()) {
                    val bmp = engine.renderFrame()
                    if (bmp != null) currentFrame = bmp
                } else {
                    delay(8)
                }
                val now = System.nanoTime()
                if (now - lastTick > 250_000_000L) {
                    lastTick = now
                    if (!scrubbing) positionMs = engine.timeMs()
                    if (durationMs <= 0) durationMs = engine.lengthMs()
                }
                yield()
            }
        }
    }

    val connecting = phase != State.PLAYING && !engine.sawPlaying
    val failed = phase == State.ERROR || (phase == State.ENDED && !engine.sawPlaying)
    val paused = phase == State.PAUSED
    val ended = phase == State.ENDED && engine.sawPlaying

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF05080C))) {
        // 已出画的帧：直接画进界面（连接期无帧 ⇒ 由父层静帧底座透出，这里不铺不遮）。
        currentFrame?.let { img ->
            Image(
                bitmap = img,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // 加载中：小环 + 一句实话（无帧时也不空荡）。
        if (connecting && currentFrame == null) {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xCC0C1218))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(color = tokens.color.primary, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Text("正在连接视频…", color = Color.White, fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight, fontWeight = VeneraType.control.weight)
            }
        }

        // 失败/结束：念清楚 + 重播/关闭，绝不静默黑屏。
        if (failed || ended) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(GalleryTokens.Shape.Large)
                    .background(Color(0xE60C1218))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), GalleryTokens.Shape.Large)
                    .padding(horizontal = 22.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = if (ended) "播放结束" else "没能出画：本机可能没装 VLC，或这地址取不到 / 格式不支持",
                    color = Color.White,
                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                    fontWeight = VeneraType.control.weight,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (ended) {
                        DesktopPushButton(onClick = { engine.start() }) {
                            Icon(Icons.Filled.Replay, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("重播", fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight)
                        }
                    }
                    DesktopPushButton(onClick = onDismiss) { Text(if (ended) "关闭" else "返回", fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight) }
                }
            }
        }

        // ── 底部玻璃控制条：整条贴画面底部、竖向渐变压暗（不挡画面又能看清控件），一行排开
        //    播放/暂停 · 进度条（点按/拖动跳转）· 时间 · 关闭。有帧且未失败/未结束时才显。──
        AnimatedVisibility(
            visible = currentFrame != null && !failed && !ended,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            val shownMs = if (scrubbing) scrubMs else positionMs
            val dur = durationMs.coerceAtLeast(0L)
            val fraction = if (dur > 0L) (shownMs.toFloat() / dur).coerceIn(0f, 1f) else 0f
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0x66000000), Color(0xCC05080C)),
                        ),
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 进度条（先放，全宽可点/拖）。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .pointerInput(dur) {
                            awaitEachGesture {
                                if (dur <= 0L) return@awaitEachGesture
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val w = size.width.coerceAtLeast(1)
                                fun fracOf(x: Float) = (x / w).coerceIn(0f, 1f)
                                scrubbing = true
                                scrubMs = (fracOf(down.position.x) * dur).toLong()
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                    if (ch.pressed) {
                                        scrubMs = (fracOf(ch.position.x) * dur).toLong()
                                        ch.consume()
                                    } else {
                                        engine.seekTo(scrubMs)
                                        scrubbing = false
                                        break
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.22f)),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(tokens.color.primary),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // 播放/暂停：主色圆底 + 白图标（明确可读，不是裸黑圆）。
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(tokens.color.primary)
                            .clickable { engine.togglePause() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = if (paused) "播放" else "暂停",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Text(
                        text = "${clockLabel(shownMs)} / ${clockLabel(durationMs)}",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                        fontWeight = VeneraType.control.weight,
                    )
                    Spacer(Modifier.weight(1f))
                    // 关闭：透明圆底 + 描边，与其它坞图标同族。
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.10f))
                            .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "关闭视频",
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 毫秒 → `m:ss`（分钟不补零、秒补零），控制条时间用。 */
private fun clockLabel(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
