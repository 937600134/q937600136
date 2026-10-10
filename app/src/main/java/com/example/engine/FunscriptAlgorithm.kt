package com.example.engine

import com.example.model.BeatEvent
import com.example.model.BeatSubdivision
import com.example.model.FunscriptAction
import com.example.model.FunscriptData
import com.example.model.GeneratorConfig
import com.example.model.OobEvent
import com.example.model.ScriptStatistics
import com.example.model.StrokeStrategy
import kotlin.math.abs
import kotlin.math.pow

object FunscriptAlgorithm {

    /**
     * Maps pitch frequency (Hz) to a normalized range [0.0, 1.0]
     * Sub-bass (50Hz) -> 0.0, Bright treble (1200Hz) -> 1.0
     */
    fun normalizePitch(pitchHz: Float, minHz: Float = 60f, maxHz: Float = 1200f): Float {
        val clamped = pitchHz.coerceIn(minHz, maxHz)
        // Logarithmic frequency perception (cents/octaves)
        val logMin = kotlin.math.ln(minHz)
        val logMax = kotlin.math.ln(maxHz)
        val logVal = kotlin.math.ln(clamped)
        return ((logVal - logMin) / (logMax - logMin)).coerceIn(0f, 1f)
    }

    /**
     * Calculates stroke center position from pitch
     */
    fun calculateCenterPosition(pitchNormalized: Float, config: GeneratorConfig): Float {
        val shift = (pitchNormalized - 0.5f) * 60f * config.pitchInfluence
        return (config.baseCenter + shift).coerceIn(10f, 90f)
    }

    /**
     * Calculates stroke amplitude (travel span) from audio energy
     */
    fun calculateStrokeAmplitude(energy: Float, config: GeneratorConfig): Float {
        val shapedEnergy = energy.coerceIn(0f, 1f).pow(config.energyPowerCurve)
        val travel = config.minStroke + (config.maxStroke - config.minStroke) * shapedEnergy * config.energyInfluence
        return travel.coerceAtLeast(10f)
    }

    /**
     * Handles out-of-bounds positions according to FunscriptDancer's Crop / Bounce / Fold strategy
     */
    fun resolveOutOfBounds(
        rawPos: Float,
        prevPos: Float,
        center: Float,
        strategy: StrokeStrategy
    ): Pair<Float, Boolean> {
        val isOob = rawPos < 0f || rawPos > 100f
        if (!isOob) {
            return Pair(rawPos, false)
        }

        val resolved = when (strategy) {
            StrokeStrategy.CROP -> {
                // Crop simply sets to 0 or 100, losing excessive travel
                rawPos.coerceIn(0f, 100f)
            }
            StrokeStrategy.BOUNCE -> {
                // Bounce inverts the out-of-bounds portion back towards center, preserving travel
                var bounced = if (rawPos > 100f) {
                    val overage = rawPos - 100f
                    100f - overage
                } else {
                    val underage = 0f - rawPos
                    0f + underage
                }
                // If bounce still overflows (extreme overage), crop it
                bounced.coerceIn(0f, 100f)
            }
            StrokeStrategy.FOLD -> {
                // Fold redirects the stroke towards its halfway point to preserve compactness
                val halfway = (prevPos + (if (rawPos > 100f) 100f else 0f)) / 2f
                val folded = if (rawPos > 100f) {
                    (halfway + (100f - halfway) * 0.7f)
                } else {
                    (halfway - halfway * 0.7f)
                }
                folded.coerceIn(0f, 100f)
            }
        }
        return Pair(resolved, true)
    }

    /**
     * Generates Funscript actions and statistics from a sequence of beat events
     */
    fun generateScript(
        beats: List<BeatEvent>,
        config: GeneratorConfig
    ): Pair<FunscriptData, ScriptStatistics> {
        if (beats.isEmpty()) {
            return Pair(FunscriptData(), ScriptStatistics())
        }

        val actions = mutableListOf<FunscriptAction>()
        val oobRecords = mutableListOf<OobEvent>()

        var cropCount = 0
        var bounceCount = 0
        var foldCount = 0
        var totalDistance = 0f
        var maxSpeed = 0f

        var currentPos = config.baseCenter
        var isMovingUp = true
        var prevTimestamp = 0L

        // Initial resting position
        actions.add(FunscriptAction(at = 0L, pos = currentPos.toInt()))

        for (i in beats.indices) {
            val beat = beats[i]
            val timestamp = beat.timestampMs
            if (timestamp <= prevTimestamp) continue

            val pitchNorm = normalizePitch(beat.pitchHz)
            val center = calculateCenterPosition(pitchNorm, config)
            val amplitude = calculateStrokeAmplitude(beat.energy, config)

            // Half-stroke / alternating stroke target
            val rawTarget = if (isMovingUp) {
                center + (amplitude / 2f)
            } else {
                center - (amplitude / 2f)
            }

            val (resolvedTarget, wasOob) = resolveOutOfBounds(rawTarget, currentPos, center, config.strategy)

            if (wasOob) {
                when (config.strategy) {
                    StrokeStrategy.CROP -> cropCount++
                    StrokeStrategy.BOUNCE -> bounceCount++
                    StrokeStrategy.FOLD -> foldCount++
                }
                oobRecords.add(
                    OobEvent(
                        timestampMs = timestamp,
                        rawPos = rawTarget,
                        resolvedPos = resolvedTarget,
                        strategy = config.strategy
                    )
                )
            }

            // Apply speed limit constraint
            val dtSeconds = (timestamp - prevTimestamp) / 1000f
            var finalTarget = resolvedTarget
            if (dtSeconds > 0.001f && config.speedLimit > 0f) {
                val maxDelta = config.speedLimit * dtSeconds
                val requestedDelta = abs(finalTarget - currentPos)
                if (requestedDelta > maxDelta) {
                    finalTarget = if (finalTarget > currentPos) currentPos + maxDelta else currentPos - maxDelta
                }
            }

            // Invert if requested
            val finalPosForAction = if (config.inverted) {
                (100f - finalTarget).coerceIn(0f, 100f)
            } else {
                finalTarget.coerceIn(0f, 100f)
            }

            // Fold multi-action subdivision: when fold triggers and interval allows, insert intermediate fold
            if (wasOob && config.strategy == StrokeStrategy.FOLD && (timestamp - prevTimestamp) > 120) {
                val midTime = prevTimestamp + (timestamp - prevTimestamp) / 2
                val midPos = ((currentPos + finalPosForAction) / 2f).toInt()
                actions.add(FunscriptAction(at = midTime, pos = midPos))
            }

            val posInt = finalPosForAction.toInt().coerceIn(config.handyMinLimit, config.handyMaxLimit)
            actions.add(FunscriptAction(at = timestamp, pos = posInt))

            val dist = abs(posInt - currentPos)
            totalDistance += dist
            if (dtSeconds > 0.001f) {
                val speed = dist / dtSeconds
                if (speed > maxSpeed) maxSpeed = speed
            }

            currentPos = finalTarget
            prevTimestamp = timestamp
            isMovingUp = !isMovingUp
        }

        val duration = if (actions.isNotEmpty()) actions.last().at else 0L
        val avgSpeed = if (duration > 0) (totalDistance / (duration / 1000f)) else 0f

        val stats = ScriptStatistics(
            totalActions = actions.size,
            durationMs = duration,
            averageSpeed = avgSpeed,
            maxSpeed = maxSpeed,
            totalTravelDistance = totalDistance,
            cropCount = cropCount,
            bounceCount = bounceCount,
            foldCount = foldCount
        )

        val funscript = FunscriptData(
            version = "1.0",
            inverted = config.inverted,
            range = config.handyStrokeRange,
            actions = actions
        )

        return Pair(funscript, stats)
    }

    /**
     * Evaluates interpolated position at any arbitrary playback timestamp
     */
    fun evaluatePositionAt(actions: List<FunscriptAction>, timeMs: Long): Float {
        if (actions.isEmpty()) return 50f
        if (timeMs <= actions.first().at) return actions.first().pos.toFloat()
        if (timeMs >= actions.last().at) return actions.last().pos.toFloat()

        // Binary search for surrounding keyframes
        var low = 0
        var high = actions.size - 1

        while (low <= high) {
            val mid = (low + high) ushr 1
            val action = actions[mid]
            if (action.at < timeMs) {
                low = mid + 1
            } else if (action.at > timeMs) {
                high = mid - 1
            } else {
                return action.pos.toFloat()
            }
        }

        val i1 = (low - 1).coerceAtLeast(0)
        val i2 = low.coerceAtMost(actions.size - 1)

        val a1 = actions[i1]
        val a2 = actions[i2]

        val dt = (a2.at - a1.at).toFloat()
        if (dt <= 0f) return a1.pos.toFloat()

        val progress = ((timeMs - a1.at) / dt).coerceIn(0f, 1f)
        // Smooth Hermite / Cosine interpolation for organic dance motion
        val smoothProgress = (1f - kotlin.math.cos(progress * Math.PI.toFloat())) / 2f
        return a1.pos + (a2.pos - a1.pos) * smoothProgress
    }

    /**
     * Serializes FunscriptData to standard JSON string compliant with The Handy specification
     */
    fun exportToJson(data: FunscriptData, title: String = "The Handy Dance Track"): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"version\": \"${data.version}\",\n")
        sb.append("  \"inverted\": ${data.inverted},\n")
        sb.append("  \"range\": ${data.range},\n")
        sb.append("  \"metadata\": {\n")
        sb.append("    \"creator\": \"FunscriptDancer Mobile\",\n")
        sb.append("    \"title\": \"$title\",\n")
        sb.append("    \"type\": \"the-handy-sync\"\n")
        sb.append("  },\n")
        sb.append("  \"actions\": [\n")
        for (i in data.actions.indices) {
            val a = data.actions[i]
            sb.append("    {\"at\": ${a.at}, \"pos\": ${a.pos}}")
            if (i < data.actions.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("  ]\n")
        sb.append("}")
        return sb.toString()
    }

    /**
     * Serializes to CSV format
     */
    fun exportToCsv(data: FunscriptData): String {
        val sb = StringBuilder()
        sb.append("timestamp_ms,position\n")
        for (a in data.actions) {
            sb.append("${a.at},${a.pos}\n")
        }
        return sb.toString()
    }
}
