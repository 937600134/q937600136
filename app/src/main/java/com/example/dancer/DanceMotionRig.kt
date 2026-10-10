package com.example.dancer

import androidx.compose.ui.geometry.Offset
import com.example.model.DanceStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 2D Skeletal joint pose coordinates normalized relative to dancer origin (0, 0)
 */
data class DancerPose(
    val headCenter: Offset,
    val headRadius: Float,
    val headTiltAngle: Float,

    val neck: Offset,
    val spineMid: Offset,
    val hipCenter: Offset,

    val leftShoulder: Offset,
    val leftElbow: Offset,
    val leftHand: Offset,

    val rightShoulder: Offset,
    val rightElbow: Offset,
    val rightHand: Offset,

    val leftHip: Offset,
    val leftKnee: Offset,
    val leftFoot: Offset,

    val rightHip: Offset,
    val rightKnee: Offset,
    val rightFoot: Offset,

    val energyGlow: Float,
    val bounceCompression: Float,
    val shadowWidth: Float
)

object DanceMotionRig {

    /**
     * Computes full skeletal pose from Funscript position [0..100], audio energy, and dance style
     */
    fun computePose(
        pos: Float,                 // 0..100 Funscript stroke position
        velocity: Float,            // units per second
        energy: Float,              // 0..1 audio energy
        beatPhase: Float,           // 0..1 progress through current beat
        style: DanceStyle,
        canvasWidth: Float,
        canvasHeight: Float
    ): DancerPose {
        val centerX = canvasWidth * 0.5f
        val baseY = canvasHeight * 0.72f // Stage ground level

        // Normalized pos: 0.0 (bottom/crouch) to 1.0 (top/jump/reach)
        val normPos = (pos / 100f).coerceIn(0f, 1f)

        // Beat bounce bounce-down effect (accent on beat)
        val beatBounce = sin(beatPhase * PI.toFloat()).coerceAtLeast(0f) * energy

        // Base vertical elevation driven by Funscript pos
        // 0 = low crouch (-40px), 100 = upright/leap (+120px)
        val verticalTravel = (normPos - 0.5f) * 160f * (0.8f + energy * 0.4f)
        val hipY = baseY - 120f - verticalTravel + (beatBounce * 16f)

        // Hip lateral sway based on style
        val swayCycle = sin(beatPhase * 2f * PI.toFloat())
        val hipSwayX = when (style) {
            DanceStyle.KPOP_WAVE -> swayCycle * 32f * (0.5f + normPos * 0.5f)
            DanceStyle.HIPHOP_GROOVE -> swayCycle * 24f * energy
            DanceStyle.ELECTRO_POPPING -> if (beatBounce > 0.6f) (if (swayCycle > 0) 12f else -12f) else 0f
            DanceStyle.ROBOTIC -> ((pos.toInt() / 25) % 2 * 2 - 1) * 8f
            DanceStyle.CLASSIC_BEAT -> swayCycle * 8f
        }

        val hipCenter = Offset(centerX + hipSwayX, hipY)

        // Spine and Torso
        val spineLean = when (style) {
            DanceStyle.HIPHOP_GROOVE -> (0.5f - normPos) * 20f
            DanceStyle.KPOP_WAVE -> -hipSwayX * 0.4f
            DanceStyle.ELECTRO_POPPING -> if (velocity > 50f) -12f else 12f
            else -> 0f
        }
        val spineMid = Offset(centerX + (hipSwayX * 0.6f) + spineLean, hipY - 65f)
        val neck = Offset(centerX + (hipSwayX * 0.2f) + (spineLean * 1.4f), hipY - 130f)

        // Head with natural nod
        val headTilt = when (style) {
            DanceStyle.HIPHOP_GROOVE -> (beatBounce * 15f) - (velocity * 0.02f)
            DanceStyle.ELECTRO_POPPING -> if (beatBounce > 0.5f) 10f else -5f
            DanceStyle.KPOP_WAVE -> sin(beatPhase * PI.toFloat()) * 10f
            else -> beatBounce * 8f
        }
        val headCenter = Offset(neck.x, neck.y - 30f - (beatBounce * 4f))
        val headRadius = 24f + (energy * 4f)

        // Shoulders
        val shoulderWidth = 44f
        val leftShoulder = Offset(neck.x - shoulderWidth, neck.y + 12f)
        val rightShoulder = Offset(neck.x + shoulderWidth, neck.y + 12f)

        // Arms Kinematics according to style & Funscript position
        // Arms lift up as Funscript position goes high, pump down as it goes low
        val armElevation = (normPos - 0.5f) * 2.2f // -1.1 (low) to +1.1 (high)

        val (lHand, lElbow, rHand, rElbow) = computeArms(
            leftShoulder,
            rightShoulder,
            armElevation,
            energy,
            beatPhase,
            style,
            velocity
        )

        // Legs Kinematics (Feet stay planted near ground while knees bend to accommodate hipY)
        val hipWidth = 28f
        val leftHip = Offset(hipCenter.x - hipWidth, hipCenter.y)
        val rightHip = Offset(hipCenter.x + hipWidth, hipCenter.y)

        val (lFoot, lKnee, rFoot, rKnee) = computeLegs(
            leftHip,
            rightHip,
            baseY,
            normPos,
            energy,
            beatPhase,
            style
        )

        val bounceCompression = (1f - normPos).coerceIn(0f, 1f)
        val shadowW = (80f + (bounceCompression * 40f) - (verticalTravel * 0.2f)).coerceAtLeast(40f)

        return DancerPose(
            headCenter = headCenter,
            headRadius = headRadius,
            headTiltAngle = headTilt,
            neck = neck,
            spineMid = spineMid,
            hipCenter = hipCenter,
            leftShoulder = leftShoulder,
            leftElbow = lElbow,
            leftHand = lHand,
            rightShoulder = rightShoulder,
            rightElbow = rElbow,
            rightHand = rHand,
            leftHip = leftHip,
            leftKnee = lKnee,
            leftFoot = lFoot,
            rightHip = rightHip,
            rightKnee = rKnee,
            rightFoot = rFoot,
            energyGlow = energy,
            bounceCompression = bounceCompression,
            shadowWidth = shadowW
        )
    }

    private fun computeArms(
        lShoulder: Offset,
        rShoulder: Offset,
        armElevation: Float, // -1.1 .. 1.1
        energy: Float,
        beatPhase: Float,
        style: DanceStyle,
        velocity: Float
    ): QuadOffsets {
        val upperArmLen = 52f
        val foreArmLen = 50f

        return when (style) {
            DanceStyle.ELECTRO_POPPING -> {
                // Popping: Sharp right angles and locked positions
                val lockHigh = armElevation > 0f
                val lElbow = if (lockHigh) {
                    Offset(lShoulder.x - upperArmLen * 0.9f, lShoulder.y - upperArmLen * 0.4f)
                } else {
                    Offset(lShoulder.x - upperArmLen * 0.6f, lShoulder.y + upperArmLen * 0.8f)
                }
                val lHand = if (lockHigh) {
                    Offset(lElbow.x, lElbow.y - foreArmLen)
                } else {
                    Offset(lElbow.x - foreArmLen, lElbow.y)
                }

                val rElbow = if (lockHigh) {
                    Offset(rShoulder.x + upperArmLen * 0.9f, rShoulder.y - upperArmLen * 0.4f)
                } else {
                    Offset(rShoulder.x + upperArmLen * 0.6f, rShoulder.y + upperArmLen * 0.8f)
                }
                val rHand = if (lockHigh) {
                    Offset(rElbow.x + foreArmLen * 0.8f, rElbow.y - foreArmLen * 0.6f)
                } else {
                    Offset(rElbow.x, rElbow.y + foreArmLen)
                }
                QuadOffsets(lHand, lElbow, rHand, rElbow)
            }

            DanceStyle.KPOP_WAVE -> {
                // Wave: smooth sinusoidal arm waves
                val wavePhase = beatPhase * 2f * PI.toFloat()
                val lAngle = armElevation + sin(wavePhase) * 0.6f
                val rAngle = armElevation + cos(wavePhase) * 0.6f

                val lElbow = Offset(
                    lShoulder.x - cos(lAngle) * upperArmLen,
                    lShoulder.y - sin(lAngle) * upperArmLen
                )
                val lHand = Offset(
                    lElbow.x - cos(lAngle + 0.3f) * foreArmLen,
                    lElbow.y - sin(lAngle + 0.3f) * foreArmLen
                )

                val rElbow = Offset(
                    rShoulder.x + cos(rAngle) * upperArmLen,
                    rShoulder.y - sin(rAngle) * upperArmLen
                )
                val rHand = Offset(
                    rElbow.x + cos(rAngle + 0.3f) * foreArmLen,
                    rElbow.y - sin(rAngle + 0.3f) * foreArmLen
                )
                QuadOffsets(lHand, lElbow, rHand, rElbow)
            }

            DanceStyle.HIPHOP_GROOVE -> {
                // Low pump / chest pump groove
                val lElbow = Offset(lShoulder.x - 36f, lShoulder.y + 40f + armElevation * 20f)
                val lHand = Offset(lShoulder.x - 12f, lElbow.y + 35f - energy * 25f)

                val rElbow = Offset(rShoulder.x + 36f, rShoulder.y + 40f + armElevation * 20f)
                val rHand = Offset(rShoulder.x + 12f, rElbow.y + 35f - energy * 25f)
                QuadOffsets(lHand, lElbow, rHand, rElbow)
            }

            DanceStyle.ROBOTIC -> {
                // Stepped 90 degree robotic angles
                val angleStep = ((armElevation * 3).toInt() * 30).toFloat() * (PI.toFloat() / 180f)
                val lElbow = Offset(lShoulder.x - 48f, lShoulder.y)
                val lHand = Offset(lElbow.x, lElbow.y - 48f * cos(angleStep))

                val rElbow = Offset(rShoulder.x + 48f, rShoulder.y)
                val rHand = Offset(rElbow.x, rElbow.y + 48f * sin(angleStep))
                QuadOffsets(lHand, lElbow, rHand, rElbow)
            }

            DanceStyle.CLASSIC_BEAT -> {
                // Linear stroke tracker: Hands track Funscript stroke height directly!
                val handLiftY = -armElevation * 75f
                val lElbow = Offset(lShoulder.x - 42f, lShoulder.y + 25f + handLiftY * 0.5f)
                val lHand = Offset(lShoulder.x - 48f, lShoulder.y + handLiftY)

                val rElbow = Offset(rShoulder.x + 42f, rShoulder.y + 25f + handLiftY * 0.5f)
                val rHand = Offset(rShoulder.x + 48f, rShoulder.y + handLiftY)
                QuadOffsets(lHand, lElbow, rHand, rElbow)
            }
        }
    }

    private fun computeLegs(
        lHip: Offset,
        rHip: Offset,
        groundY: Float,
        normPos: Float,
        energy: Float,
        beatPhase: Float,
        style: DanceStyle
    ): QuadOffsets {
        val footWidth = 48f
        val leftFootBaseX = lHip.x - 22f
        val rightFootBaseX = rHip.x + 22f

        // When pos is high, feet may lift off ground slightly (hop/jump)
        val jumpLift = if (normPos > 0.85f) (normPos - 0.85f) * 120f else 0f

        // Foot tap on beat
        val tap = if (beatPhase > 0.85f) (1f - beatPhase) * 40f * energy else 0f

        val lFoot = Offset(leftFootBaseX, groundY - jumpLift - tap)
        val rFoot = Offset(rightFootBaseX, groundY - jumpLift)

        // Knee IK solving: bend outward/forward to bridge hip to foot
        val lKneeX = (lHip.x + lFoot.x) / 2f - (25f * (1.2f - normPos))
        val lKneeY = (lHip.y + lFoot.y) / 2f + 4f
        val lKnee = Offset(lKneeX, lKneeY)

        val rKneeX = (rHip.x + rFoot.x) / 2f + (25f * (1.2f - normPos))
        val rKneeY = (rHip.y + rFoot.y) / 2f + 4f
        val rKnee = Offset(rKneeX, rKneeY)

        return QuadOffsets(lFoot, lKnee, rFoot, rKnee)
    }

    private data class QuadOffsets(
        val first: Offset,
        val second: Offset,
        val third: Offset,
        val fourth: Offset
    )
}
