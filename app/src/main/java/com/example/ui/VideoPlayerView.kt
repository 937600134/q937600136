package com.example.ui

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun VideoPlayerView(
    videoUri: Uri,
    isPlaying: Boolean,
    currentTimeMs: Long,
    onVideoPrepared: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val videoView = remember {
        VideoView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    DisposableEffect(videoUri) {
        videoView.setVideoURI(videoUri)
        videoView.setOnPreparedListener { mp ->
            mp.isLooping = true
            onVideoPrepared(videoView.duration.toLong().coerceAtLeast(1000L))
            if (isPlaying) {
                videoView.start()
            }
        }
        onDispose {
            videoView.stopPlayback()
        }
    }

    DisposableEffect(isPlaying) {
        if (isPlaying) {
            if (!videoView.isPlaying) {
                videoView.start()
            }
        } else {
            if (videoView.isPlaying) {
                videoView.pause()
            }
        }
        onDispose {}
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("video_player_view")
    ) {
        AndroidView(
            factory = { videoView },
            modifier = Modifier.fillMaxSize()
        )

        // Floating media badge
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF00E5FF).copy(alpha = 0.85f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        ) {
            Text(
                text = "▶ 本地视频已同步",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}
