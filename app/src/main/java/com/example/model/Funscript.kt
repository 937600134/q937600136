package com.example.model

import android.net.Uri

/**
 * Data model for Funscript specification (v1.0)
 * Fully compatible with The-Handy, ScriptPlayer, Whirligig, and HandyControl
 */
data class FunscriptData(
    val version: String = "1.0",
    val inverted: Boolean = false,
    val range: Int = 90,
    val actions: List<FunscriptAction> = emptyList(),
    val metadata: FunscriptMetadata? = null
)

data class FunscriptMetadata(
    val creator: String = "FunscriptDancer Mobile",
    val title: String = "",
    val durationMs: Long = 0L,
    val scriptType: String = "the-handy-sync"
)

data class FunscriptAction(
    val at: Long,    // Timestamp in milliseconds
    val pos: Int     // Position between 0 and 100
)

/**
 * Strategy for handling strokes that go out of bounds (<0 or >100)
 * Core algorithmic feature of FunscriptDancer
 */
enum class StrokeStrategy(val displayName: String, val description: String) {
    CROP(
        displayName = "Crop (裁切)",
        description = "将超出 [0, 100] 的位置直接截断在 0 或 100。动作平滑安全，适合保护设备电机。"
    ),
    BOUNCE(
        displayName = "Bounce (反弹)",
        description = "将越界冲程按越界距离反向弹回中心，保留全部行程距离。带来高动态与强烈的节奏反打。"
    ),
    FOLD(
        displayName = "Fold (折叠)",
        description = "将超限行程折返至中点后重新延展，在紧凑行程内释放全量节奏能量与微步分段。"
    )
}

/**
 * Subdivision mode for beat stroke generation
 */
enum class BeatSubdivision(val displayName: String, val multiplier: Float) {
    HALF_SPEED("1/2 拍 (缓动)", 2.0f),
    FULL_BEAT("1 拍 (标准)", 1.0f),
    DOUBLE_BEAT("双倍速 (急促)", 0.5f),
    QUAD_BEAT("四倍速 (高频)", 0.25f)
}

/**
 * Visual dance choreography style
 */
enum class DanceStyle(val displayName: String, val description: String) {
    CLASSIC_BEAT("标准线性", "纯粹遵循冲程位置的垂直律动与节拍跳跃"),
    ELECTRO_POPPING("电子街舞 (Popping)", "强烈的节拍震点、瞬态折叠锁点与手部几何控制"),
    HIPHOP_GROOVE("嘻哈律动 (Groove)", "沉重的低音下潜蹲伏、大幅度身体躯干摆动与脚步律动"),
    KPOP_WAVE("韩系波浪 (Wave)", "平滑流畅的贝塞尔身体波浪、优美的手臂画圆与重心转移"),
    ROBOTIC("机械舞 (Robot)", "分段步进的顿挫感、精准关节定格与高频震颤")
}

/**
 * Configuration parameters matching FunscriptDancer engine & The-Handy controller
 */
data class GeneratorConfig(
    val strategy: StrokeStrategy = StrokeStrategy.BOUNCE,
    val subdivision: BeatSubdivision = BeatSubdivision.FULL_BEAT,
    val baseCenter: Float = 50f,            // Default center pos (0-100)
    val pitchInfluence: Float = 0.6f,       // How strongly pitch shifts center (0..1)
    val energyInfluence: Float = 0.8f,      // How strongly energy expands stroke amplitude (0..1)
    val minStroke: Float = 20f,             // Minimum stroke travel on low energy
    val maxStroke: Float = 95f,             // Maximum stroke travel on high energy
    val energyPowerCurve: Float = 1.2f,     // Non-linear power curve for RMS response
    val speedLimit: Float = 400f,           // Max allowed speed in pos units / second (The-Handy safe limit: 400mm/s)
    val inverted: Boolean = false,
    val danceStyle: DanceStyle = DanceStyle.ELECTRO_POPPING,
    val handyStrokeRange: Int = 90,         // The-Handy device stroke range (0..100)
    val handyMinLimit: Int = 0,             // Lower hardware clamp
    val handyMaxLimit: Int = 100            // Upper hardware clamp
)

/**
 * Beat analysis event from audio/video engine
 */
data class BeatEvent(
    val timestampMs: Long,
    val bpm: Float,
    val energy: Float,           // 0.0 .. 1.0 RMS energy
    val pitchHz: Float,          // Dominant frequency / pitch
    val isDropOrPeak: Boolean = false
)

/**
 * Out-of-bounds trigger record for statistics and visualization
 */
data class OobEvent(
    val timestampMs: Long,
    val rawPos: Float,
    val resolvedPos: Float,
    val strategy: StrokeStrategy
)

/**
 * Generation summary statistics
 */
data class ScriptStatistics(
    val totalActions: Int = 0,
    val durationMs: Long = 0,
    val averageSpeed: Float = 0f,      // Units per second
    val maxSpeed: Float = 0f,
    val totalTravelDistance: Float = 0f,
    val cropCount: Int = 0,
    val bounceCount: Int = 0,
    val foldCount: Int = 0
)

/**
 * Audio demo track description
 */
data class AudioTrackPreset(
    val id: String,
    val name: String,
    val genre: String,
    val bpm: Int,
    val durationSeconds: Int,
    val description: String
)

/**
 * Source media type
 */
enum class MediaSourceType {
    SYNTH_PRESET,
    IMPORTED_AUDIO,
    IMPORTED_VIDEO
}

/**
 * Loaded media representation
 */
data class LoadedMediaItem(
    val type: MediaSourceType = MediaSourceType.SYNTH_PRESET,
    val uri: Uri? = null,
    val title: String = "内置音轨",
    val isVideo: Boolean = false,
    val durationMs: Long = 60000L,
    val estimatedBpm: Float = 124f
)
