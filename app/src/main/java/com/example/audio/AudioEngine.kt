package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import com.example.model.AudioTrackPreset
import com.example.model.BeatEvent
import com.example.model.LoadedMediaItem
import com.example.model.MediaSourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class AudioEngine(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Default)

    // Preset Tracks
    val presets = listOf(
        AudioTrackPreset(
            id = "cyber_synth",
            name = "Cyber Synthwave 2088",
            genre = "Synthwave / Electro",
            bpm = 124,
            durationSeconds = 60,
            description = "驱动力十足的 4/4 鼓点与起伏低音合成器，适合高反弹与流畅律动"
        ),
        AudioTrackPreset(
            id = "edm_bounce",
            name = "Neon Festival Drop",
            genre = "EDM / Club Bounce",
            bpm = 128,
            durationSeconds = 60,
            description = "极具冲击力的高能电子节奏，频繁触发越界反弹与高能折叠"
        ),
        AudioTrackPreset(
            id = "hiphop_boom",
            name = "Midnight 808 Groove",
            genre = "Boom-Bap / Hip-Hop",
            bpm = 92,
            durationSeconds = 60,
            description = "下沉的重低音 Kick 与切分军鼓，展现深度下潜蹲伏与肢体控制"
        ),
        AudioTrackPreset(
            id = "breakbeat_frenzy",
            name = "Hyper Breakbeat",
            genre = "Drum & Bass / Breaks",
            bpm = 140,
            durationSeconds = 60,
            description = "急速连击的快速碎拍，考验动作的高频折叠与限速控制"
        ),
        AudioTrackPreset(
            id = "chill_lofi",
            name = "Warm Vinyl Cafe",
            genre = "Chill Lo-Fi Beat",
            bpm = 80,
            durationSeconds = 60,
            description = "温和柔顺的舒缓摇摆节拍，适合 Crop 裁切模式下的平稳动作"
        )
    )

    private val _currentTrack = MutableStateFlow(presets[0])
    val currentTrack = _currentTrack.asStateFlow()

    private val _currentMedia = MutableStateFlow(
        LoadedMediaItem(
            type = MediaSourceType.SYNTH_PRESET,
            title = presets[0].name,
            durationMs = presets[0].durationSeconds * 1000L,
            estimatedBpm = presets[0].bpm.toFloat()
        )
    )
    val currentMedia = _currentMedia.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _playbackTimeMs = MutableStateFlow(0L)
    val playbackTimeMs = _playbackTimeMs.asStateFlow()

    private val _currentEnergy = MutableStateFlow(0.4f)
    val currentEnergy = _currentEnergy.asStateFlow()

    private val _currentPitchHz = MutableStateFlow(220f)
    val currentPitchHz = _currentPitchHz.asStateFlow()

    private val _isMicListening = MutableStateFlow(false)
    val isMicListening = _isMicListening.asStateFlow()

    // Realtime beat event flow
    private val _liveBeatFlow = MutableSharedFlow<BeatEvent>(extraBufferCapacity = 16)
    val liveBeatFlow = _liveBeatFlow.asSharedFlow()

    private var playbackJob: Job? = null
    private var synthAudioTrack: AudioTrack? = null
    private var mediaPlayer: MediaPlayer? = null
    private var micRecordJob: Job? = null

    // Tap tempo records
    private val tapHistory = mutableListOf<Long>()
    private val _detectedBpm = MutableStateFlow(124f)
    val detectedBpm = _detectedBpm.asStateFlow()

    fun selectPreset(preset: AudioTrackPreset) {
        val wasPlaying = _isPlaying.value
        pause()
        releaseMediaPlayer()
        _currentTrack.value = preset
        _currentMedia.value = LoadedMediaItem(
            type = MediaSourceType.SYNTH_PRESET,
            title = preset.name,
            durationMs = preset.durationSeconds * 1000L,
            estimatedBpm = preset.bpm.toFloat()
        )
        _playbackTimeMs.value = 0L
        _detectedBpm.value = preset.bpm.toFloat()
        if (wasPlaying) {
            play()
        }
    }

    fun loadExternalMedia(uri: Uri, isVideo: Boolean, title: String) {
        pause()
        releaseMediaPlayer()

        var durationMs = 60000L
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (durStr != null) {
                durationMs = durStr.toLong().coerceAtLeast(1000L)
            }
            retriever.release()
        } catch (_: Exception) {}

        _currentMedia.value = LoadedMediaItem(
            type = if (isVideo) MediaSourceType.IMPORTED_VIDEO else MediaSourceType.IMPORTED_AUDIO,
            uri = uri,
            title = title,
            isVideo = isVideo,
            durationMs = durationMs,
            estimatedBpm = _detectedBpm.value
        )
        _playbackTimeMs.value = 0L

        if (!isVideo) {
            // Set up MediaPlayer for audio
            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(context, uri)
                    isLooping = true
                    prepare()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun play() {
        if (_isPlaying.value) return
        _isPlaying.value = true

        val media = _currentMedia.value
        if (media.type == MediaSourceType.SYNTH_PRESET) {
            startSynthesizedPlayback()
        } else if (media.type == MediaSourceType.IMPORTED_AUDIO) {
            startAudioFilePlayback()
        } else {
            // Video playback tracking
            startVideoTimerSync()
        }
    }

    fun pause() {
        _isPlaying.value = false
        playbackJob?.cancel()
        playbackJob = null
        try {
            synthAudioTrack?.pause()
            synthAudioTrack?.flush()
        } catch (_: Exception) {}
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        } catch (_: Exception) {}
    }

    fun seekTo(timeMs: Long) {
        val maxMs = _currentMedia.value.durationMs
        val target = timeMs.coerceIn(0L, maxMs)
        _playbackTimeMs.value = target

        try {
            mediaPlayer?.seekTo(target.toInt())
        } catch (_: Exception) {}
    }

    fun setDuration(durationMs: Long) {
        if (durationMs > 0L) {
            _currentMedia.value = _currentMedia.value.copy(durationMs = durationMs)
        }
    }

    private fun startAudioFilePlayback() {
        playbackJob?.cancel()
        playbackJob = scope.launch(Dispatchers.IO) {
            try {
                mediaPlayer?.start()
                val bpm = _detectedBpm.value
                val beatInterval = (60000f / bpm).toLong()

                while (isActive && _isPlaying.value) {
                    val pos = mediaPlayer?.currentPosition?.toLong() ?: _playbackTimeMs.value
                    _playbackTimeMs.value = pos

                    // Simulate rhythmic pulse based on beat interval
                    val beatFrac = (pos % beatInterval).toFloat() / beatInterval
                    val dynamicEnergy = ((1f - beatFrac * 0.7f) * 0.6f + 0.35f).coerceIn(0.1f, 1.0f)
                    val pitchCurve = 180f + ((pos / 600L) % 8) * 60f

                    _currentEnergy.value = dynamicEnergy
                    _currentPitchHz.value = pitchCurve

                    delay(20)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun startVideoTimerSync() {
        playbackJob?.cancel()
        playbackJob = scope.launch(Dispatchers.IO) {
            var current = _playbackTimeMs.value
            val max = _currentMedia.value.durationMs
            val bpm = _detectedBpm.value
            val beatInterval = (60000f / bpm).toLong()

            while (isActive && _isPlaying.value) {
                current += 25
                if (current >= max) {
                    current = 0L
                }
                _playbackTimeMs.value = current

                val beatFrac = (current % beatInterval).toFloat() / beatInterval
                val dynamicEnergy = ((1f - beatFrac * 0.7f) * 0.6f + 0.35f).coerceIn(0.1f, 1.0f)
                val pitchCurve = 200f + ((current / 500L) % 7) * 70f

                _currentEnergy.value = dynamicEnergy
                _currentPitchHz.value = pitchCurve

                delay(25)
            }
        }
    }

    private fun startSynthesizedPlayback() {
        playbackJob?.cancel()
        playbackJob = scope.launch(Dispatchers.IO) {
            val sampleRate = 44100
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            synthAudioTrack = track
            track.play()

            val shortBuffer = ShortArray(bufferSize / 2)
            var currentMs = _playbackTimeMs.value
            val maxMs = _currentMedia.value.durationMs

            var beatSampleCounter = 0
            val bpm = _currentTrack.value.bpm
            val samplesPerBeat = (sampleRate * 60f / bpm).toInt()

            var synthPhase = 0.0

            while (isActive && _isPlaying.value) {
                for (i in shortBuffer.indices) {
                    val beatIndex = (beatSampleCounter / samplesPerBeat) % 16
                    val sampleInBeat = beatSampleCounter % samplesPerBeat
                    val beatProgress = sampleInBeat.toFloat() / samplesPerBeat

                    var sampleVal = 0f

                    val hasKick = when (_currentTrack.value.id) {
                        "hiphop_boom" -> (beatIndex == 0 || beatIndex == 6 || beatIndex == 8 || beatIndex == 11)
                        "breakbeat_frenzy" -> (beatIndex == 0 || beatIndex == 3 || beatIndex == 7 || beatIndex == 10)
                        else -> (beatIndex % 4 == 0)
                    }

                    if (hasKick) {
                        val decay = (1f - (beatProgress * 2.5f)).coerceAtLeast(0f)
                        val kickFreq = 120.0 * (1.0 - beatProgress * 0.7)
                        sampleVal += (sin(beatProgress * kickFreq * 2.0 * PI) * decay * 0.7f).toFloat()
                    }

                    val hasSnare = (beatIndex == 4 || beatIndex == 12)
                    if (hasSnare) {
                        val decay = (1f - (beatProgress * 2.0f)).coerceAtLeast(0f)
                        val noise = ((Math.random() * 2.0 - 1.0) * 0.45f).toFloat()
                        val tone = (sin(beatProgress * 180.0 * 2.0 * PI) * 0.35f).toFloat()
                        sampleVal += (noise + tone) * decay
                    }

                    val hatSub = (beatSampleCounter % (samplesPerBeat / 2)).toFloat() / (samplesPerBeat / 2)
                    val hatDecay = (1f - (hatSub * 4.0f)).coerceAtLeast(0f)
                    val hatNoise = ((Math.random() * 2.0 - 1.0) * 0.2f).toFloat()
                    sampleVal += hatNoise * hatDecay

                    val chordRoot = when (beatIndex / 4) {
                        0 -> 130.81
                        1 -> 155.56
                        2 -> 174.61
                        else -> 196.0
                    }
                    val synthFreq = if (beatIndex % 2 == 0) chordRoot else chordRoot * 1.5
                    synthPhase += 2.0 * PI * synthFreq / sampleRate
                    if (synthPhase > 2.0 * PI) synthPhase -= 2.0 * PI
                    val bass = (sin(synthPhase) * 0.25f).toFloat()
                    sampleVal += bass

                    shortBuffer[i] = (sampleVal.coerceIn(-1f, 1f) * 28000).toInt().toShort()
                    beatSampleCounter++
                }

                track.write(shortBuffer, 0, shortBuffer.size)

                val chunkDurationMs = (shortBuffer.size * 1000L) / sampleRate
                currentMs += chunkDurationMs

                if (currentMs >= maxMs) {
                    currentMs = 0L
                }
                _playbackTimeMs.value = currentMs

                val beatFrac = (currentMs % (60000L / bpm)).toFloat() / (60000L / bpm)
                val dynamicEnergy = ((1f - beatFrac * 0.7f) * 0.6f + 0.35f).coerceIn(0.1f, 1.0f)
                val pitchCurve = 180f + ((currentMs / 500L) % 8) * 65f

                _currentEnergy.value = dynamicEnergy
                _currentPitchHz.value = pitchCurve
            }

            try {
                track.stop()
                track.release()
            } catch (_: Exception) {}
        }
    }

    /**
     * Generates a complete sequence of pre-computed beat events for any media item
     */
    fun generateBeatsForMedia(totalMs: Long, bpm: Float): List<BeatEvent> {
        val beats = mutableListOf<BeatEvent>()
        val beatIntervalMs = (60_000f / bpm).toLong().coerceAtLeast(100L)

        var t = 0L
        var beatIndex = 0

        while (t < totalMs) {
            val barIndex = (beatIndex / 4) % 16
            val isDropOrPeak = (barIndex == 7 || barIndex == 15)
            val baseEnergy = when {
                isDropOrPeak -> 0.95f
                barIndex in 4..6 -> 0.8f
                barIndex in 12..14 -> 0.85f
                else -> 0.6f
            }

            val beatEnergy = if (beatIndex % 4 == 0) {
                baseEnergy
            } else if (beatIndex % 2 == 0) {
                baseEnergy * 0.85f
            } else {
                baseEnergy * 0.7f
            }

            val chordStep = (barIndex % 4)
            val pitch = when (chordStep) {
                0 -> 110f + (beatIndex % 4) * 50f
                1 -> 160f + (beatIndex % 4) * 80f
                2 -> 220f + (beatIndex % 4) * 110f
                else -> 330f + (beatIndex % 4) * 140f
            }

            beats.add(
                BeatEvent(
                    timestampMs = t,
                    bpm = bpm,
                    energy = beatEnergy.coerceIn(0.1f, 1.0f),
                    pitchHz = pitch,
                    isDropOrPeak = isDropOrPeak
                )
            )

            t += beatIntervalMs
            beatIndex++
        }

        return beats
    }

    fun registerTap() {
        val now = System.currentTimeMillis()
        tapHistory.add(now)
        if (tapHistory.size > 8) tapHistory.removeAt(0)
        while (tapHistory.size > 2 && (now - tapHistory.first()) > 4000) {
            tapHistory.removeAt(0)
        }

        if (tapHistory.size >= 2) {
            val intervals = mutableListOf<Long>()
            for (i in 1 until tapHistory.size) {
                intervals.add(tapHistory[i] - tapHistory[i - 1])
            }
            val avgInterval = intervals.average()
            if (avgInterval > 150 && avgInterval < 2000) {
                val bpm = (60_000.0 / avgInterval).toFloat().coerceIn(40f, 240f)
                _detectedBpm.value = bpm
            }
        }
    }

    fun toggleMicListening(onPermissionRequired: () -> Unit) {
        if (_isMicListening.value) {
            stopMicListening()
        } else {
            startMicListening(onPermissionRequired)
        }
    }

    private fun startMicListening(onPermissionRequired: () -> Unit) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            onPermissionRequired()
            return
        }

        _isMicListening.value = true
        pause()

        micRecordJob?.cancel()
        micRecordJob = scope.launch(Dispatchers.IO) {
            val sampleRate = 44100
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            var recorder: AudioRecord? = null
            try {
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf
                )
                recorder.startRecording()
                val audioBuffer = ShortArray(1024)

                var lastBeatTime = System.currentTimeMillis()
                var energyHistory = FloatArray(16) { 0.1f }
                var historyIdx = 0

                while (isActive && _isMicListening.value) {
                    val read = recorder.read(audioBuffer, 0, audioBuffer.size)
                    if (read > 0) {
                        var sumSq = 0.0
                        var zeroCrossings = 0
                        for (i in 0 until read) {
                            val v = audioBuffer[i].toDouble()
                            sumSq += v * v
                            if (i > 0 && ((audioBuffer[i] >= 0 && audioBuffer[i - 1] < 0) || (audioBuffer[i] < 0 && audioBuffer[i - 1] >= 0))) {
                                zeroCrossings++
                            }
                        }
                        val rms = sqrt(sumSq / read) / 32768.0
                        val normEnergy = (rms * 4.5).toFloat().coerceIn(0.05f, 1.0f)
                        _currentEnergy.value = normEnergy

                        val zcrHz = (zeroCrossings * sampleRate) / (2f * read)
                        _currentPitchHz.value = zcrHz.coerceIn(80f, 1500f)

                        val avgHistory = energyHistory.average().toFloat()
                        val isOnset = normEnergy > (avgHistory * 1.55f) && (normEnergy > 0.25f)
                        val now = System.currentTimeMillis()

                        if (isOnset && (now - lastBeatTime) > 220) {
                            val interval = now - lastBeatTime
                            lastBeatTime = now
                            val bpm = (60_000f / interval).coerceIn(60f, 200f)
                            _detectedBpm.value = bpm

                            val beat = BeatEvent(
                                timestampMs = now,
                                bpm = bpm,
                                energy = normEnergy,
                                pitchHz = zcrHz,
                                isDropOrPeak = normEnergy > 0.7f
                            )
                            _liveBeatFlow.emit(beat)
                        }

                        energyHistory[historyIdx] = normEnergy
                        historyIdx = (historyIdx + 1) % energyHistory.size
                    }
                    delay(15)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    recorder?.stop()
                    recorder?.release()
                } catch (_: Exception) {}
                _isMicListening.value = false
            }
        }
    }

    fun stopMicListening() {
        _isMicListening.value = false
        micRecordJob?.cancel()
        micRecordJob = null
    }

    private fun releaseMediaPlayer() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (_: Exception) {}
    }

    fun release() {
        pause()
        releaseMediaPlayer()
        stopMicListening()
    }
}
