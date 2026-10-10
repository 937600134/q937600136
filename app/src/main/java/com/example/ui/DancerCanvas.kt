package com.example.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dancer.DanceMotionRig
import com.example.dancer.DancerPose
import com.example.model.DanceStyle
import com.example.model.StrokeStrategy
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun DancerCanvas(
    currentPos: Float,             // 0..100 Funscript stroke position
    velocity: Float,               // units / sec
    energy: Float,                 // 0..1 audio energy
    pitchHz: Float,
    beatPhase: Float,              // 0..1 within beat
    style: DanceStyle,
    strategy: StrokeStrategy,
    bpm: Float,
    isOobActive: Boolean,
    modifier: Modifier = Modifier
) {
    // Ambient light animation
    val infiniteTransition = rememberInfiniteTransition(label = "stageLights")
    val ambientPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ambientPhase"
    )

    Box(
        modifier = modifier
            .background(Color(0xFF0C0A19))
            .testTag("dancer_canvas_container")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("dancer_canvas")
        ) {
            val width = size.width
            val height = size.height

            // 1. Draw stage background & perspective floor grid
            drawStageBackground(width, height, energy, ambientPhase)

            // 2. Compute dynamic dancer pose
            val pose = DanceMotionRig.computePose(
                pos = currentPos,
                velocity = velocity,
                energy = energy,
                beatPhase = beatPhase,
                style = style,
                canvasWidth = width,
                canvasHeight = height
            )

            // 3. Draw stage floor reflection and shadow
            drawStageShadow(pose)

            // 4. Draw Beat Ripple Rings on stage floor
            drawBeatRipples(pose.hipCenter.x, height * 0.72f, beatPhase, energy)

            // 5. Draw Skeletal Animated Dancer Figure
            drawDancerCharacter(pose, currentPos, energy, style)

            // 6. Draw Vertical Funscript Position Gauge (0..100) on side
            drawPositionGauge(width, height, currentPos, strategy)
        }

        // Strategy & OOB Live Overlay Badge
        if (isOobActive) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .testTag("oob_active_badge"),
                shape = RoundedCornerShape(12.dp),
                color = when (strategy) {
                    StrokeStrategy.BOUNCE -> Color(0xFF00E5FF).copy(alpha = 0.9f)
                    StrokeStrategy.CROP -> Color(0xFFFFB300).copy(alpha = 0.9f)
                    StrokeStrategy.FOLD -> Color(0xFFFF4081).copy(alpha = 0.9f)
                },
                shadowElevation = 8.dp
            ) {
                Text(
                    text = when (strategy) {
                        StrokeStrategy.BOUNCE -> "⚡ BOUNCE! (反弹)"
                        StrokeStrategy.CROP -> "✂️ CROP! (截断)"
                        StrokeStrategy.FOLD -> "🔀 FOLD! (折叠)"
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

private fun DrawScope.drawStageBackground(
    width: Float,
    height: Float,
    energy: Float,
    ambientPhase: Float
) {
    // Stage gradient backwall
    val bgBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF090714),
            Color(0xFF130F2A),
            Color(0xFF1E1742)
        ),
        startY = 0f,
        endY = height * 0.72f
    )
    drawRect(brush = bgBrush, size = Size(width, height * 0.72f))

    // Spotlight beams from top
    val spotLeftX = width * (0.25f + sin(ambientPhase * 2f * PI.toFloat()) * 0.1f)
    val spotRightX = width * (0.75f - cos(ambientPhase * 2f * PI.toFloat()) * 0.1f)

    val spotBrushLeft = Brush.radialGradient(
        colors = listOf(
            Color(0xFFB388FF).copy(alpha = 0.22f + energy * 0.25f),
            Color.Transparent
        ),
        center = Offset(spotLeftX, height * 0.72f),
        radius = width * 0.45f
    )
    drawCircle(brush = spotBrushLeft, radius = width * 0.45f, center = Offset(spotLeftX, height * 0.72f))

    val spotBrushRight = Brush.radialGradient(
        colors = listOf(
            Color(0xFF00E5FF).copy(alpha = 0.18f + energy * 0.22f),
            Color.Transparent
        ),
        center = Offset(spotRightX, height * 0.72f),
        radius = width * 0.45f
    )
    drawCircle(brush = spotBrushRight, radius = width * 0.45f, center = Offset(spotRightX, height * 0.72f))

    // Stage Floor (Perspective)
    val groundY = height * 0.72f
    val floorBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF15112E),
            Color(0xFF0A0717)
        ),
        startY = groundY,
        endY = height
    )
    drawRect(brush = floorBrush, topLeft = Offset(0f, groundY), size = Size(width, height - groundY))

    // Floor Horizon Glow line
    drawLine(
        brush = Brush.horizontalGradient(
            listOf(
                Color.Transparent,
                Color(0xFF00E5FF).copy(alpha = 0.6f + energy * 0.4f),
                Color(0xFFFF4081).copy(alpha = 0.6f + energy * 0.4f),
                Color.Transparent
            )
        ),
        start = Offset(0f, groundY),
        end = Offset(width, groundY),
        strokeWidth = 3f
    )

    // Perspective grid lines on floor
    val vanishX = width * 0.5f
    for (i in -4..4) {
        val targetX = vanishX + (i * width * 0.22f)
        drawLine(
            color = Color(0xFF6C5CE7).copy(alpha = 0.2f),
            start = Offset(vanishX, groundY),
            end = Offset(targetX, height),
            strokeWidth = 1.5f
        )
    }
}

private fun DrawScope.drawStageShadow(pose: DancerPose) {
    val floorY = pose.leftFoot.y.coerceAtLeast(pose.rightFoot.y)
    drawOval(
        color = Color(0xFF03020A).copy(alpha = 0.75f),
        topLeft = Offset(pose.hipCenter.x - pose.shadowWidth / 2f, floorY - 8f),
        size = Size(pose.shadowWidth, 18f)
    )
}

private fun DrawScope.drawBeatRipples(
    centerX: Float,
    groundY: Float,
    beatPhase: Float,
    energy: Float
) {
    if (energy < 0.25f) return
    val radius = (beatPhase * 160f) * (0.6f + energy * 0.4f)
    val alpha = ((1f - beatPhase) * 0.5f * energy).coerceIn(0f, 1f)

    drawOval(
        color = Color(0xFF00E5FF).copy(alpha = alpha),
        topLeft = Offset(centerX - radius, groundY - (radius * 0.22f)),
        size = Size(radius * 2f, radius * 0.44f),
        style = Stroke(width = 2.5f)
    )
}

private fun DrawScope.drawDancerCharacter(
    pose: DancerPose,
    pos: Float,
    energy: Float,
    style: DanceStyle
) {
    // Dynamic color accents based on position and energy
    val neonCyan = Color(0xFF00E5FF)
    val neonMagenta = Color(0xFFFF4081)
    val neonPurple = Color(0xFFB388FF)
    val neonYellow = Color(0xFFFFD700)

    val mainAccent = if (pos > 60f) neonCyan else if (pos > 30f) neonPurple else neonMagenta
    val limbStrokeWidth = 14f

    // 1. Legs (Thigh & Shin)
    // Left Leg
    drawLine(
        brush = Brush.verticalGradient(listOf(neonPurple, mainAccent)),
        start = pose.leftHip,
        end = pose.leftKnee,
        strokeWidth = limbStrokeWidth,
        cap = StrokeCap.Round
    )
    drawLine(
        color = mainAccent,
        start = pose.leftKnee,
        end = pose.leftFoot,
        strokeWidth = limbStrokeWidth * 0.85f,
        cap = StrokeCap.Round
    )

    // Right Leg
    drawLine(
        brush = Brush.verticalGradient(listOf(neonPurple, mainAccent)),
        start = pose.rightHip,
        end = pose.rightKnee,
        strokeWidth = limbStrokeWidth,
        cap = StrokeCap.Round
    )
    drawLine(
        color = mainAccent,
        start = pose.rightKnee,
        end = pose.rightFoot,
        strokeWidth = limbStrokeWidth * 0.85f,
        cap = StrokeCap.Round
    )

    // Feet caps / sneakers
    drawCircle(color = neonYellow, radius = 9f, center = pose.leftFoot)
    drawCircle(color = neonYellow, radius = 9f, center = pose.rightFoot)

    // 2. Torso & Spine
    val torsoPath = Path().apply {
        moveTo(pose.leftShoulder.x, pose.leftShoulder.y)
        lineTo(pose.rightShoulder.x, pose.rightShoulder.y)
        lineTo(pose.rightHip.x, pose.rightHip.y)
        lineTo(pose.leftHip.x, pose.leftHip.y)
        close()
    }
    drawPath(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF2E2460), Color(0xFF161233)),
            startY = pose.neck.y,
            endY = pose.hipCenter.y
        ),
        path = torsoPath
    )
    drawPath(
        brush = Brush.linearGradient(listOf(mainAccent, neonMagenta)),
        path = torsoPath,
        style = Stroke(width = 3.5f, join = StrokeJoin.Round)
    )

    // Center chest core energy reactor
    drawCircle(
        color = Color.White,
        radius = 7f + (energy * 3f),
        center = pose.spineMid
    )
    drawCircle(
        color = mainAccent.copy(alpha = 0.5f + energy * 0.5f),
        radius = 16f + (energy * 8f),
        center = pose.spineMid,
        style = Stroke(width = 2.5f)
    )

    // 3. Arms (Shoulder, Elbow, Hand)
    // Left Arm
    drawLine(
        color = neonPurple,
        start = pose.leftShoulder,
        end = pose.leftElbow,
        strokeWidth = limbStrokeWidth * 0.8f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = mainAccent,
        start = pose.leftElbow,
        end = pose.leftHand,
        strokeWidth = limbStrokeWidth * 0.7f,
        cap = StrokeCap.Round
    )
    drawCircle(color = Color.White, radius = 7f, center = pose.leftHand)

    // Right Arm
    drawLine(
        color = neonPurple,
        start = pose.rightShoulder,
        end = pose.rightElbow,
        strokeWidth = limbStrokeWidth * 0.8f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = mainAccent,
        start = pose.rightElbow,
        end = pose.rightHand,
        strokeWidth = limbStrokeWidth * 0.7f,
        cap = StrokeCap.Round
    )
    drawCircle(color = Color.White, radius = 7f, center = pose.rightHand)

    // Joint glowing pins
    drawCircle(color = neonYellow, radius = 6f, center = pose.leftElbow)
    drawCircle(color = neonYellow, radius = 6f, center = pose.rightElbow)
    drawCircle(color = neonYellow, radius = 6f, center = pose.leftKnee)
    drawCircle(color = neonYellow, radius = 6f, center = pose.rightKnee)

    // 4. Neck & Head
    drawLine(
        color = Color(0xFF2E2460),
        start = pose.neck,
        end = Offset(pose.headCenter.x, pose.headCenter.y + 12f),
        strokeWidth = 9f,
        cap = StrokeCap.Round
    )

    // Head base silhouette
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF3B2F7D), Color(0xFF191338)),
            center = pose.headCenter,
            radius = pose.headRadius
        ),
        radius = pose.headRadius,
        center = pose.headCenter
    )
    drawCircle(
        color = mainAccent,
        radius = pose.headRadius,
        center = pose.headCenter,
        style = Stroke(width = 3f)
    )

    // Cyber visor / glowing glasses
    val visorW = pose.headRadius * 1.3f
    val visorH = 10f
    drawRoundRect(
        color = if (energy > 0.6f) neonYellow else neonCyan,
        topLeft = Offset(pose.headCenter.x - (visorW / 2f), pose.headCenter.y - 4f),
        size = Size(visorW, visorH),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
    )
}

private fun DrawScope.drawPositionGauge(
    width: Float,
    height: Float,
    currentPos: Float,
    strategy: StrokeStrategy
) {
    val gaugeX = width - 24f
    val gaugeTop = height * 0.12f
    val gaugeHeight = height * 0.52f
    val gaugeBottom = gaugeTop + gaugeHeight

    // Background track
    drawLine(
        color = Color(0xFF272348),
        start = Offset(gaugeX, gaugeTop),
        end = Offset(gaugeX, gaugeBottom),
        strokeWidth = 6f,
        cap = StrokeCap.Round
    )

    // Active position
    val progress = (currentPos / 100f).coerceIn(0f, 1f)
    val indicatorY = gaugeBottom - (progress * gaugeHeight)

    // Active filled stroke
    drawLine(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF00E5FF), Color(0xFFFF4081)),
            startY = gaugeTop,
            endY = gaugeBottom
        ),
        start = Offset(gaugeX, gaugeBottom),
        end = Offset(gaugeX, indicatorY),
        strokeWidth = 6f,
        cap = StrokeCap.Round
    )

    // Indicator head
    drawCircle(
        color = Color.White,
        radius = 8f,
        center = Offset(gaugeX, indicatorY)
    )
    drawCircle(
        color = Color(0xFF00E5FF),
        radius = 12f,
        center = Offset(gaugeX, indicatorY),
        style = Stroke(width = 2.5f)
    )

    // Boundary marks at 0% and 100%
    drawLine(
        color = Color(0xFFFF4081),
        start = Offset(gaugeX - 8f, gaugeTop),
        end = Offset(gaugeX + 8f, gaugeTop),
        strokeWidth = 2f
    )
    drawLine(
        color = Color(0xFF00E5FF),
        start = Offset(gaugeX - 8f, gaugeBottom),
        end = Offset(gaugeX + 8f, gaugeBottom),
        strokeWidth = 2f
    )
}
