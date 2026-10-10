package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BeatEvent
import com.example.model.FunscriptAction
import com.example.model.StrokeStrategy
import kotlin.math.abs

@Composable
fun FunscriptTimelineView(
    actions: List<FunscriptAction>,
    beats: List<BeatEvent>,
    currentTimeMs: Long,
    totalDurationMs: Long,
    strategy: StrokeStrategy,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    // Visible time window width in milliseconds (e.g., 4000ms centered around playhead)
    val windowDurationMs = 5000L

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp)
            .background(Color(0xFF120E24))
            .testTag("funscript_timeline_view")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(totalDurationMs) {
                    detectTapGestures { offset ->
                        val w = size.width
                        if (w > 0) {
                            val ratio = (offset.x / w).coerceIn(0f, 1f)
                            val targetTime = (ratio * totalDurationMs).toLong()
                            onSeek(targetTime)
                        }
                    }
                }
                .pointerInput(totalDurationMs) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        change.consume()
                        val w = size.width
                        if (w > 0) {
                            val dt = (dragAmount / w * totalDurationMs).toLong()
                            onSeek(currentTimeMs + dt)
                        }
                    }
                }
        ) {
            val width = size.width
            val height = size.height

            // Timeline boundary margins
            val padTop = 14f
            val padBottom = 16f
            val usableHeight = height - padTop - padBottom

            // 1. Safe zone boundary lines at 100% and 0%
            val lineTopY = padTop
            val lineBottomY = padTop + usableHeight

            drawLine(
                color = Color(0xFF322A5E),
                start = Offset(0f, lineTopY),
                end = Offset(width, lineTopY),
                strokeWidth = 1f
            )
            drawLine(
                color = Color(0xFF322A5E),
                start = Offset(0f, lineBottomY),
                end = Offset(width, lineBottomY),
                strokeWidth = 1f
            )
            // Center 50% line
            drawLine(
                color = Color(0xFF221C42),
                start = Offset(0f, padTop + usableHeight * 0.5f),
                end = Offset(width, padTop + usableHeight * 0.5f),
                strokeWidth = 1f
            )

            // Playhead sits at 35% of canvas width
            val playheadX = width * 0.35f
            val startTimeMs = currentTimeMs - (windowDurationMs * 0.35f).toLong()
            val endTimeMs = startTimeMs + windowDurationMs

            // 2. Beat grid vertical tick marks
            for (beat in beats) {
                if (beat.timestampMs in startTimeMs..endTimeMs) {
                    val progress = (beat.timestampMs - startTimeMs).toFloat() / windowDurationMs
                    val beatX = progress * width
                    val tickColor = if (beat.isDropOrPeak) Color(0xFFFFD700).copy(alpha = 0.5f) else Color(0xFF6C5CE7).copy(alpha = 0.25f)
                    val tickHeight = if (beat.isDropOrPeak) usableHeight else usableHeight * 0.4f

                    drawLine(
                        color = tickColor,
                        start = Offset(beatX, lineBottomY),
                        end = Offset(beatX, lineBottomY - tickHeight),
                        strokeWidth = if (beat.isDropOrPeak) 2f else 1f
                    )
                }
            }

            // 3. Draw Funscript curve segment within visible window
            if (actions.isNotEmpty()) {
                val visibleActions = actions.filter { it.at in (startTimeMs - 500)..(endTimeMs + 500) }
                if (visibleActions.size >= 2) {
                    val curvePath = Path()
                    var started = false

                    for (action in visibleActions) {
                        val progress = (action.at - startTimeMs).toFloat() / windowDurationMs
                        val x = progress * width
                        val y = lineBottomY - (action.pos / 100f) * usableHeight

                        if (!started) {
                            curvePath.moveTo(x, y)
                            started = true
                        } else {
                            curvePath.lineTo(x, y)
                        }
                    }

                    // Stroke trajectory
                    drawPath(
                        path = curvePath,
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color(0xFF00E5FF), Color(0xFFFF4081)),
                            startX = 0f,
                            endX = width
                        ),
                        style = Stroke(width = 3.5f, cap = StrokeCap.Round)
                    )

                    // Draw action anchor dots & OOB highlights
                    for (action in visibleActions) {
                        val progress = (action.at - startTimeMs).toFloat() / windowDurationMs
                        val x = progress * width
                        val y = lineBottomY - (action.pos / 100f) * usableHeight

                        val isEdge = action.pos >= 98 || action.pos <= 2
                        if (isEdge) {
                            // Highlight OOB strategy point
                            val oobColor = when (strategy) {
                                StrokeStrategy.BOUNCE -> Color(0xFF00E5FF)
                                StrokeStrategy.CROP -> Color(0xFFFFB300)
                                StrokeStrategy.FOLD -> Color(0xFFFF4081)
                            }
                            drawCircle(color = oobColor, radius = 5.5f, center = Offset(x, y))
                        } else {
                            drawCircle(color = Color.White.copy(alpha = 0.7f), radius = 2.5f, center = Offset(x, y))
                        }
                    }
                }
            }

            // 4. Playhead Vertical Scrubber Line
            drawLine(
                color = Color(0xFF00E5FF),
                start = Offset(playheadX, 0f),
                end = Offset(playheadX, height),
                strokeWidth = 2.5f
            )

            // Playhead glow triangle cap at top
            val capPath = Path().apply {
                moveTo(playheadX - 6f, 0f)
                lineTo(playheadX + 6f, 0f)
                lineTo(playheadX, 9f)
                close()
            }
            drawPath(path = capPath, color = Color(0xFF00E5FF))
        }

        // Labels
        Text(
            text = "100%",
            color = Color(0xFF6B6699),
            fontSize = 9.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 6.dp, top = 2.dp)
        )
        Text(
            text = "0%",
            color = Color(0xFF6B6699),
            fontSize = 9.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 6.dp, bottom = 2.dp)
        )
        Text(
            text = "${currentTimeMs / 1000}.${(currentTimeMs % 1000) / 100}s",
            color = Color(0xFF00E5FF),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 8.dp, top = 4.dp)
        )
    }
}
