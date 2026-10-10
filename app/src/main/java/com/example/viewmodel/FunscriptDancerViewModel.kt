package com.example.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioEngine
import com.example.engine.FunscriptAlgorithm
import com.example.model.AudioTrackPreset
import com.example.model.BeatEvent
import com.example.model.FunscriptData
import com.example.model.GeneratorConfig
import com.example.model.MediaSourceType
import com.example.model.ScriptStatistics
import com.example.model.StrokeStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FunscriptDancerViewModel(application: Application) : AndroidViewModel(application) {

    val audioEngine = AudioEngine(application)

    private val _config = MutableStateFlow(GeneratorConfig())
    val config = _config.asStateFlow()

    private val _funscriptData = MutableStateFlow(FunscriptData())
    val funscriptData = _funscriptData.asStateFlow()

    private val _stats = MutableStateFlow(ScriptStatistics())
    val stats = _stats.asStateFlow()

    private val _currentBeats = MutableStateFlow<List<BeatEvent>>(emptyList())
    val currentBeats = _currentBeats.asStateFlow()

    // Real-time animation states
    private val _currentPos = MutableStateFlow(50f)
    val currentPos = _currentPos.asStateFlow()

    private val _velocity = MutableStateFlow(0f)
    val velocity = _velocity.asStateFlow()

    private val _beatPhase = MutableStateFlow(0f)
    val beatPhase = _beatPhase.asStateFlow()

    private val _isOobTriggered = MutableStateFlow(false)
    val isOobTriggered = _isOobTriggered.asStateFlow()

    private val _showInfoDialog = MutableStateFlow(false)
    val showInfoDialog = _showInfoDialog.asStateFlow()

    private val _isShowingVideoView = MutableStateFlow(true)
    val isShowingVideoView = _isShowingVideoView.asStateFlow()

    private var animationLoopJob: Job? = null
    private var liveBeatsBuffer = mutableListOf<BeatEvent>()

    init {
        // Load initial track beats & generate script
        regenerateScriptForCurrentMedia()

        // Observe live beats from microphone if active
        viewModelScope.launch {
            audioEngine.liveBeatFlow.collect { beat ->
                handleLiveBeat(beat)
            }
        }

        // Start high-frame-rate rendering loop (60 FPS)
        startAnimationLoop()
    }

    fun updateConfig(newConfig: GeneratorConfig) {
        _config.value = newConfig
        regenerateScriptForCurrentMedia()
    }

    fun selectTrack(preset: AudioTrackPreset) {
        audioEngine.selectPreset(preset)
        _isShowingVideoView.value = false
        regenerateScriptForCurrentMedia()
    }

    fun loadExternalMedia(uri: Uri, isVideo: Boolean, title: String) {
        audioEngine.loadExternalMedia(uri, isVideo, title)
        if (isVideo) {
            _isShowingVideoView.value = true
        }
        regenerateScriptForCurrentMedia()
    }

    fun toggleVideoView() {
        _isShowingVideoView.value = !_isShowingVideoView.value
    }

    fun onVideoPrepared(durationMs: Long) {
        audioEngine.setDuration(durationMs)
        regenerateScriptForCurrentMedia()
    }

    private fun regenerateScriptForCurrentMedia() {
        viewModelScope.launch(Dispatchers.Default) {
            val media = audioEngine.currentMedia.value
            val beats = audioEngine.generateBeatsForMedia(media.durationMs, audioEngine.detectedBpm.value)
            _currentBeats.value = beats
            val (script, stats) = FunscriptAlgorithm.generateScript(beats, _config.value)
            _funscriptData.value = script
            _stats.value = stats
        }
    }

    private fun handleLiveBeat(beat: BeatEvent) {
        liveBeatsBuffer.add(beat)
        if (liveBeatsBuffer.size > 200) {
            liveBeatsBuffer.removeAt(0)
        }
        val (script, stats) = FunscriptAlgorithm.generateScript(liveBeatsBuffer, _config.value)
        _funscriptData.value = script
        _stats.value = stats
        _currentBeats.value = liveBeatsBuffer.toList()
    }

    fun togglePlayPause() {
        if (audioEngine.isPlaying.value) {
            audioEngine.pause()
        } else {
            audioEngine.play()
        }
    }

    fun seekTo(timeMs: Long) {
        audioEngine.seekTo(timeMs)
    }

    fun toggleMicListening(onPermissionRequired: () -> Unit) {
        audioEngine.toggleMicListening(onPermissionRequired)
    }

    fun tapTempo() {
        audioEngine.registerTap()
        regenerateScriptForCurrentMedia()
    }

    fun setInfoDialogVisible(visible: Boolean) {
        _showInfoDialog.value = visible
    }

    private fun startAnimationLoop() {
        animationLoopJob?.cancel()
        animationLoopJob = viewModelScope.launch(Dispatchers.Default) {
            var prevPos = 50f
            var prevTime = System.currentTimeMillis()

            while (isActive) {
                val now = System.currentTimeMillis()
                val dt = (now - prevTime).coerceAtLeast(1L)
                prevTime = now

                val playheadMs = audioEngine.playbackTimeMs.value
                val actions = _funscriptData.value.actions

                val targetPos = if (actions.isNotEmpty()) {
                    FunscriptAlgorithm.evaluatePositionAt(actions, playheadMs)
                } else {
                    50f
                }

                val currentVel = ((targetPos - prevPos) / (dt / 1000f)).coerceIn(-1000f, 1000f)
                prevPos = targetPos

                _currentPos.value = targetPos
                _velocity.value = currentVel

                val bpm = audioEngine.detectedBpm.value.coerceAtLeast(40f)
                val beatDurationMs = (60_000f / bpm).toLong()
                val phase = ((playheadMs % beatDurationMs).toFloat() / beatDurationMs).coerceIn(0f, 1f)
                _beatPhase.value = phase

                val isNearBounds = targetPos >= 96f || targetPos <= 4f
                _isOobTriggered.value = isNearBounds

                delay(16)
            }
        }
    }

    fun exportTheHandyScript(onShare: (Intent) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val title = audioEngine.currentMedia.value.title
            val json = FunscriptAlgorithm.exportToJson(_funscriptData.value, title = title)

            // Normalize filename so The Handy and HandyControl players match it with video:
            // e.g. "my_video.mp4" -> "my_video.funscript"
            val cleanTitle = title.replace(".mp4", "", ignoreCase = true)
                .replace(".mkv", "", ignoreCase = true)
                .replace(".avi", "", ignoreCase = true)
                .replace(".mp3", "", ignoreCase = true)
                .replace(".wav", "", ignoreCase = true)
                .replace(" ", "_")
                .trim()
                .ifEmpty { "the_handy_script" }

            val fileName = "${cleanTitle}.funscript"

            val cacheDir = getApplication<Application>().cacheDir
            val file = File(cacheDir, fileName)
            file.writeText(json)

            val uri = FileProvider.getUriForFile(
                getApplication(),
                "${getApplication<Application>().packageName}.provider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "$fileName - The Handy 动作脚本")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            withContext(Dispatchers.Main) {
                onShare(Intent.createChooser(intent, "导出 The-Handy 动作脚本 ($fileName)"))
            }
        }
    }

    fun exportCsvFile(onShare: (Intent) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val csv = FunscriptAlgorithm.exportToCsv(_funscriptData.value)
            val title = audioEngine.currentMedia.value.title.replace(" ", "_").lowercase()
            val fileName = "${title}_strokes.csv"

            val cacheDir = getApplication<Application>().cacheDir
            val file = File(cacheDir, fileName)
            file.writeText(csv)

            val uri = FileProvider.getUriForFile(
                getApplication(),
                "${getApplication<Application>().packageName}.provider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            withContext(Dispatchers.Main) {
                onShare(Intent.createChooser(intent, "导出 CSV 数据"))
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioEngine.release()
        animationLoopJob?.cancel()
    }
}
