package com.example.ui

import android.Manifest
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MediaSourceType
import com.example.model.StrokeStrategy
import com.example.viewmodel.FunscriptDancerViewModel

private fun queryMediaFileName(context: Context, uri: Uri): String {
    var name = "imported_media"
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index != -1 && cursor.moveToFirst()) {
                name = cursor.getString(index)
            }
        }
    } catch (_: Exception) {
        name = uri.lastPathSegment ?: "imported_media"
    }
    return name
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FunscriptDancerApp(viewModel: FunscriptDancerViewModel) {
    val context = LocalContext.current

    val config by viewModel.config.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val funscriptData by viewModel.funscriptData.collectAsState()
    val currentBeats by viewModel.currentBeats.collectAsState()

    val currentPos by viewModel.currentPos.collectAsState()
    val velocity by viewModel.velocity.collectAsState()
    val beatPhase by viewModel.beatPhase.collectAsState()
    val isOobTriggered by viewModel.isOobTriggered.collectAsState()
    val showInfoDialog by viewModel.showInfoDialog.collectAsState()
    val isShowingVideoView by viewModel.isShowingVideoView.collectAsState()

    val currentMedia by viewModel.audioEngine.currentMedia.collectAsState()
    val isPlaying by viewModel.audioEngine.isPlaying.collectAsState()
    val playbackTimeMs by viewModel.audioEngine.playbackTimeMs.collectAsState()
    val currentEnergy by viewModel.audioEngine.currentEnergy.collectAsState()
    val currentPitchHz by viewModel.audioEngine.currentPitchHz.collectAsState()
    val isMicListening by viewModel.audioEngine.isMicListening.collectAsState()
    val detectedBpm by viewModel.audioEngine.detectedBpm.collectAsState()

    // Video Picker
    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = queryMediaFileName(context, it)
            viewModel.loadExternalMedia(it, isVideo = true, title = fileName)
            Toast.makeText(context, "已载入视频: $fileName", Toast.LENGTH_SHORT).show()
        }
    }

    // Audio Picker
    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = queryMediaFileName(context, it)
            viewModel.loadExternalMedia(it, isVideo = false, title = fileName)
            Toast.makeText(context, "已载入音频: $fileName", Toast.LENGTH_SHORT).show()
        }
    }

    // Microphone permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.toggleMicListening {}
        } else {
            Toast.makeText(context, "需要麦克风权限以实时分析声音节拍", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF00E5FF).copy(alpha = 0.2f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (currentMedia.isVideo) Icons.Default.Movie else Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Funscript Dancer",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "${currentMedia.title} • ${config.strategy.displayName.split(" ")[0]}",
                                fontSize = 11.sp,
                                color = Color(0xFF9E9AC2),
                                maxLines = 1
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.setInfoDialogVisible(true) },
                        modifier = Modifier.testTag("btn_top_info")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "算法原理",
                            tint = Color(0xFF00E5FF)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0E0B1F),
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF0E0B1F)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Upper Stage Area: Video View or Dancer Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.0f)
            ) {
                if (currentMedia.isVideo && isShowingVideoView && currentMedia.uri != null) {
                    VideoPlayerView(
                        videoUri = currentMedia.uri!!,
                        isPlaying = isPlaying,
                        currentTimeMs = playbackTimeMs,
                        onVideoPrepared = { viewModel.onVideoPrepared(it) },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    DancerCanvas(
                        currentPos = currentPos,
                        velocity = velocity,
                        energy = currentEnergy,
                        pitchHz = currentPitchHz,
                        beatPhase = beatPhase,
                        style = config.danceStyle,
                        strategy = config.strategy,
                        bpm = detectedBpm,
                        isOobActive = isOobTriggered,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Top Floating Status HUD (BPM & Pos)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF140F2D).copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "POS: ${currentPos.toInt()}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E5FF)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${detectedBpm.toInt()} BPM",
                            fontSize = 11.sp,
                            color = Color(0xFFFFD700)
                        )
                    }
                }
            }

            // Transport Control Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF15102C)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.togglePlayPause() },
                            modifier = Modifier
                                .size(42.dp)
                                .background(Color(0xFF00E5FF), CircleShape)
                                .testTag("btn_play_pause")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "暂停" else "播放",
                                tint = Color.Black
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        IconButton(
                            onClick = { viewModel.seekTo(0L) },
                            modifier = Modifier.testTag("btn_restart")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay,
                                contentDescription = "重新开始",
                                tint = Color(0xFF9E9AC2)
                            )
                        }
                    }

                    // Playback time display
                    val totalSec = (currentMedia.durationMs / 1000).toInt()
                    val currentSec = (playbackTimeMs / 1000).toInt()
                    Text(
                        text = String.format("%02d:%02d / %02d:%02d", currentSec / 60, currentSec % 60, totalSec / 60, totalSec % 60),
                        fontSize = 13.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )

                    // Current Strategy Tag
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when (config.strategy) {
                            StrokeStrategy.BOUNCE -> Color(0xFF00E5FF).copy(alpha = 0.2f)
                            StrokeStrategy.CROP -> Color(0xFFFFB300).copy(alpha = 0.2f)
                            StrokeStrategy.FOLD -> Color(0xFFFF4081).copy(alpha = 0.2f)
                        }
                    ) {
                        Text(
                            text = config.strategy.displayName.split(" ")[0],
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (config.strategy) {
                                StrokeStrategy.BOUNCE -> Color(0xFF00E5FF)
                                StrokeStrategy.CROP -> Color(0xFFFFB300)
                                StrokeStrategy.FOLD -> Color(0xFFFF4081)
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Funscript Motion Trajectory Curve & Timeline
            FunscriptTimelineView(
                actions = funscriptData.actions,
                beats = currentBeats,
                currentTimeMs = playbackTimeMs,
                totalDurationMs = currentMedia.durationMs,
                strategy = config.strategy,
                onSeek = { viewModel.seekTo(it) }
            )

            // Lower Parameters & Deck Tabs
            ParametersPanel(
                config = config,
                stats = stats,
                funscriptData = funscriptData,
                currentMedia = currentMedia,
                presets = viewModel.audioEngine.presets,
                isMicListening = isMicListening,
                detectedBpm = detectedBpm,
                isShowingVideoView = isShowingVideoView,
                onConfigChange = { viewModel.updateConfig(it) },
                onTrackSelect = { viewModel.selectTrack(it) },
                onPickAudio = { audioPicker.launch("audio/*") },
                onPickVideo = { videoPicker.launch("video/*") },
                onToggleVideoView = { viewModel.toggleVideoView() },
                onToggleMic = {
                    viewModel.toggleMicListening {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onTapTempo = { viewModel.tapTempo() },
                onExportTheHandyScript = {
                    viewModel.exportTheHandyScript { shareIntent ->
                        context.startActivity(shareIntent)
                    }
                },
                onExportCsv = {
                    viewModel.exportCsvFile { shareIntent ->
                        context.startActivity(shareIntent)
                    }
                },
                onShowAlgorithmInfo = { viewModel.setInfoDialogVisible(true) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.95f)
            )
        }
    }

    if (showInfoDialog) {
        AlgorithmInfoDialog(onDismiss = { viewModel.setInfoDialogVisible(false) })
    }
}
