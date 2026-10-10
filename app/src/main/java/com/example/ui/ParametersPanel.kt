package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.FunscriptAlgorithm
import com.example.model.AudioTrackPreset
import com.example.model.BeatSubdivision
import com.example.model.DanceStyle
import com.example.model.FunscriptData
import com.example.model.GeneratorConfig
import com.example.model.LoadedMediaItem
import com.example.model.MediaSourceType
import com.example.model.ScriptStatistics
import com.example.model.StrokeStrategy

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ParametersPanel(
    config: GeneratorConfig,
    stats: ScriptStatistics,
    funscriptData: FunscriptData,
    currentMedia: LoadedMediaItem,
    presets: List<AudioTrackPreset>,
    isMicListening: Boolean,
    detectedBpm: Float,
    isShowingVideoView: Boolean,
    onConfigChange: (GeneratorConfig) -> Unit,
    onTrackSelect: (AudioTrackPreset) -> Unit,
    onPickAudio: () -> Unit,
    onPickVideo: () -> Unit,
    onToggleVideoView: () -> Unit,
    onToggleMic: () -> Unit,
    onTapTempo: () -> Unit,
    onExportTheHandyScript: () -> Unit,
    onExportCsv: () -> Unit,
    onShowAlgorithmInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("算法策略", "映射参数", "音视频导入", "The-Handy导出")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF131024))
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color(0xFF181430),
            contentColor = Color(0xFF00E5FF),
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = Color(0xFF00E5FF)
                )
            }
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == index) Color(0xFF00E5FF) else Color(0xFF9E9AC2)
                        )
                    },
                    modifier = Modifier.testTag("tab_$index")
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            when (selectedTab) {
                0 -> StrategySection(
                    config = config,
                    onConfigChange = onConfigChange,
                    onShowAlgorithmInfo = onShowAlgorithmInfo
                )
                1 -> MappingSection(
                    config = config,
                    onConfigChange = onConfigChange
                )
                2 -> MediaImportSection(
                    currentMedia = currentMedia,
                    presets = presets,
                    isMicListening = isMicListening,
                    detectedBpm = detectedBpm,
                    isShowingVideoView = isShowingVideoView,
                    onTrackSelect = onTrackSelect,
                    onPickAudio = onPickAudio,
                    onPickVideo = onPickVideo,
                    onToggleVideoView = onToggleVideoView,
                    onToggleMic = onToggleMic,
                    onTapTempo = onTapTempo
                )
                3 -> TheHandyExportSection(
                    config = config,
                    stats = stats,
                    funscriptData = funscriptData,
                    currentMedia = currentMedia,
                    onConfigChange = onConfigChange,
                    onExportTheHandyScript = onExportTheHandyScript,
                    onExportCsv = onExportCsv,
                    onCopyClipboard = {
                        val json = FunscriptAlgorithm.exportToJson(funscriptData, title = currentMedia.title)
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("TheHandyFunscript", json))
                        Toast.makeText(context, "已复制 The-Handy Funscript 到剪贴板", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StrategySection(
    config: GeneratorConfig,
    onConfigChange: (GeneratorConfig) -> Unit,
    onShowAlgorithmInfo: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "FunscriptDancer 核心越界策略",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        IconButton(
            onClick = onShowAlgorithmInfo,
            modifier = Modifier.testTag("btn_algo_info")
        ) {
            Icon(
                imageVector = Icons.Default.Psychology,
                contentDescription = "算法原理",
                tint = Color(0xFF00E5FF)
            )
        }
    }

    Text(
        text = "当基于音高与能量计算的冲程目标超出 [0, 100] 物理区间时的处理算法：",
        fontSize = 12.sp,
        color = Color(0xFF9E9AC2),
        modifier = Modifier.padding(bottom = 12.dp)
    )

    StrokeStrategy.values().forEach { strategy ->
        val isSelected = config.strategy == strategy
        val accentColor = when (strategy) {
            StrokeStrategy.BOUNCE -> Color(0xFF00E5FF)
            StrokeStrategy.CROP -> Color(0xFFFFB300)
            StrokeStrategy.FOLD -> Color(0xFFFF4081)
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable { onConfigChange(config.copy(strategy = strategy)) }
                .testTag("strategy_card_${strategy.name}"),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected) Color(0xFF221C42) else Color(0xFF17132B)
            ),
            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, accentColor) else null
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = strategy.displayName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) accentColor else Color.White
                    )
                    if (isSelected) {
                        Text(
                            text = "● 激活中",
                            fontSize = 11.sp,
                            color = accentColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = strategy.description,
                    fontSize = 11.sp,
                    color = Color(0xFFB0ACD0),
                    lineHeight = 16.sp
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider(color = Color(0xFF2B2550))
    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "舞台动作风格表现 (Dancer Style)",
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White
    )
    Spacer(modifier = Modifier.height(8.dp))

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DanceStyle.values().forEach { style ->
            FilterChip(
                selected = config.danceStyle == style,
                onClick = { onConfigChange(config.copy(danceStyle = style)) },
                label = { Text(style.displayName, fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF00E5FF).copy(alpha = 0.2f),
                    selectedLabelColor = Color(0xFF00E5FF),
                    containerColor = Color(0xFF1E1A38),
                    labelColor = Color(0xFFCCCCCC)
                ),
                modifier = Modifier.testTag("chip_style_${style.name}")
            )
        }
    }
}

@Composable
private fun MappingSection(
    config: GeneratorConfig,
    onConfigChange: (GeneratorConfig) -> Unit
) {
    Text(
        text = "音视频特征映射配置",
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White
    )
    Text(
        text = "音高控制中心位置，声音能量动态扩展冲程幅度。",
        fontSize = 12.sp,
        color = Color(0xFF9E9AC2),
        modifier = Modifier.padding(bottom = 12.dp)
    )

    Text(
        text = "音高影响权重 (Pitch Influence): ${(config.pitchInfluence * 100).toInt()}%",
        fontSize = 12.sp,
        color = Color.White
    )
    Slider(
        value = config.pitchInfluence,
        onValueChange = { onConfigChange(config.copy(pitchInfluence = it)) },
        valueRange = 0f..1f,
        colors = SliderDefaults.colors(
            thumbColor = Color(0xFF00E5FF),
            activeTrackColor = Color(0xFF00E5FF)
        ),
        modifier = Modifier.testTag("slider_pitch_influence")
    )

    Text(
        text = "能量影响幅度 (Energy Amplitude): ${(config.energyInfluence * 100).toInt()}%",
        fontSize = 12.sp,
        color = Color.White
    )
    Slider(
        value = config.energyInfluence,
        onValueChange = { onConfigChange(config.copy(energyInfluence = it)) },
        valueRange = 0f..1f,
        colors = SliderDefaults.colors(
            thumbColor = Color(0xFFFF4081),
            activeTrackColor = Color(0xFFFF4081)
        ),
        modifier = Modifier.testTag("slider_energy_influence")
    )

    Text(
        text = "最大行程幅度 (Max Travel): ${config.maxStroke.toInt()}",
        fontSize = 12.sp,
        color = Color.White
    )
    Slider(
        value = config.maxStroke,
        onValueChange = { onConfigChange(config.copy(maxStroke = it)) },
        valueRange = 50f..120f,
        colors = SliderDefaults.colors(
            thumbColor = Color(0xFFFFD700),
            activeTrackColor = Color(0xFFFFD700)
        ),
        modifier = Modifier.testTag("slider_max_stroke")
    )

    Text(
        text = "平滑最高速度 (Speed Limit): ${config.speedLimit.toInt()} 单位/秒",
        fontSize = 12.sp,
        color = Color.White
    )
    Slider(
        value = config.speedLimit,
        onValueChange = { onConfigChange(config.copy(speedLimit = it)) },
        valueRange = 150f..800f,
        colors = SliderDefaults.colors(
            thumbColor = Color(0xFFB388FF),
            activeTrackColor = Color(0xFFB388FF)
        ),
        modifier = Modifier.testTag("slider_speed_limit")
    )
}

@Composable
private fun MediaImportSection(
    currentMedia: LoadedMediaItem,
    presets: List<AudioTrackPreset>,
    isMicListening: Boolean,
    detectedBpm: Float,
    isShowingVideoView: Boolean,
    onTrackSelect: (AudioTrackPreset) -> Unit,
    onPickAudio: () -> Unit,
    onPickVideo: () -> Unit,
    onToggleVideoView: () -> Unit,
    onToggleMic: () -> Unit,
    onTapTempo: () -> Unit
) {
    Text(
        text = "导入本地音视频与播放",
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White
    )
    Spacer(modifier = Modifier.height(6.dp))

    // Current Media Card
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (currentMedia.type != MediaSourceType.SYNTH_PRESET) Color(0xFF1E284A) else Color(0xFF1B1638)
        ),
        border = if (currentMedia.type != MediaSourceType.SYNTH_PRESET) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF00E5FF)) else null
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (currentMedia.isVideo) Icons.Default.Movie else Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentMedia.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White,
                        maxLines = 1
                    )
                    Text(
                        text = "时长: ${(currentMedia.durationMs / 1000)}秒 • ${detectedBpm.toInt()} BPM",
                        fontSize = 11.sp,
                        color = Color(0xFF9E9AC2)
                    )
                }

                if (currentMedia.isVideo) {
                    OutlinedButton(
                        onClick = onToggleVideoView,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (isShowingVideoView) "切回舞者" else "查看视频", fontSize = 11.sp, color = Color(0xFF00E5FF))
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Import Media Buttons
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onPickVideo,
            modifier = Modifier
                .weight(1f)
                .testTag("btn_import_video"),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("导入本地视频", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        Button(
            onClick = onPickAudio,
            modifier = Modifier
                .weight(1f)
                .testTag("btn_import_audio"),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF282050)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("导入本地音频", fontSize = 12.sp, color = Color.White)
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider(color = Color(0xFF2B2550))
    Spacer(modifier = Modifier.height(16.dp))

    // Live Tap & Mic
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ElevatedCard(
            modifier = Modifier
                .weight(1f)
                .clickable { onTapTempo() }
                .testTag("btn_tap_tempo"),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF221A45))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.TouchApp,
                    contentDescription = "Tap Tempo",
                    tint = Color(0xFFFFD700)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("Tap 节拍测速", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                Text("${detectedBpm.toInt()} BPM", fontSize = 11.sp, color = Color(0xFFFFD700))
            }
        }

        ElevatedCard(
            modifier = Modifier
                .weight(1f)
                .clickable { onToggleMic() }
                .testTag("btn_mic_listen"),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = if (isMicListening) Color(0xFF431932) else Color(0xFF1E1A38)
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = if (isMicListening) Icons.Default.Mic else Icons.Default.MicOff,
                    contentDescription = "麦克风拾音",
                    tint = if (isMicListening) Color(0xFFFF4081) else Color(0xFF9E9AC2)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (isMicListening) "麦克风监听中" else "麦克风拾音",
                    fontSize = 12.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (isMicListening) "实时触发动作" else "点击开启",
                    fontSize = 11.sp,
                    color = if (isMicListening) Color(0xFFFF4081) else Color(0xFF9E9AC2)
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = "预置高动态电音音轨",
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFFB388FF)
    )
    Spacer(modifier = Modifier.height(8.dp))

    presets.forEach { preset ->
        val isSelected = currentMedia.type == MediaSourceType.SYNTH_PRESET && currentMedia.title == preset.name
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable { onTrackSelect(preset) }
                .testTag("track_item_${preset.id}"),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected) Color(0xFF282050) else Color(0xFF17132B)
            ),
            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF00E5FF)) else null
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = if (isSelected) Color(0xFF00E5FF) else Color(0xFF756F9E),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = preset.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${preset.genre} • ${preset.bpm} BPM",
                        fontSize = 11.sp,
                        color = Color(0xFF9E9AC2)
                    )
                }
            }
        }
    }
}

@Composable
private fun TheHandyExportSection(
    config: GeneratorConfig,
    stats: ScriptStatistics,
    funscriptData: FunscriptData,
    currentMedia: LoadedMediaItem,
    onConfigChange: (GeneratorConfig) -> Unit,
    onExportTheHandyScript: () -> Unit,
    onExportCsv: () -> Unit,
    onCopyClipboard: () -> Unit
) {
    Text(
        text = "The-Handy 动作脚本规范生成与导出",
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF00E5FF)
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "生成标准 The-Handy .funscript 文件，包含 version: 1.0、range 以及毫秒级 pos 动作数组。",
        fontSize = 12.sp,
        color = Color(0xFF9E9AC2)
    )
    Spacer(modifier = Modifier.height(10.dp))

    // Hardware parameters card
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1D173C))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "The-Handy 设备硬件参数设置",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "硬件冲程范围 (Range): ${config.handyStrokeRange}% (默认推荐90%)",
                fontSize = 12.sp,
                color = Color(0xFFDDD9F3)
            )
            Slider(
                value = config.handyStrokeRange.toFloat(),
                onValueChange = { onConfigChange(config.copy(handyStrokeRange = it.toInt())) },
                valueRange = 50f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF00E5FF),
                    activeTrackColor = Color(0xFF00E5FF)
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("下限截断: ${config.handyMinLimit}%", fontSize = 11.sp, color = Color(0xFF9E9AC2))
                Text("上限截断: ${config.handyMaxLimit}%", fontSize = 11.sp, color = Color(0xFF9E9AC2))
            }
            Slider(
                value = config.handyMaxLimit.toFloat(),
                onValueChange = { onConfigChange(config.copy(handyMaxLimit = it.toInt())) },
                valueRange = 60f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFFFD700),
                    activeTrackColor = Color(0xFFFFD700)
                )
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Stats Grid
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF17132B))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(label = "总动作点", value = "${stats.totalActions}")
                StatItem(label = "平均速度", value = "${stats.averageSpeed.toInt()} u/s")
                StatItem(label = "行程总长", value = "${stats.totalTravelDistance.toInt()} u")
            }
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color(0xFF2D255A))
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(label = "Bounce反弹", value = "${stats.bounceCount}", color = Color(0xFF00E5FF))
                StatItem(label = "Crop截断", value = "${stats.cropCount}", color = Color(0xFFFFB300))
                StatItem(label = "Fold折叠", value = "${stats.foldCount}", color = Color(0xFFFF4081))
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // The-Handy Export Buttons
    Button(
        onClick = onExportTheHandyScript,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("btn_export_the_handy"),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Icon(Icons.Default.Download, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("导出 The-Handy 脚本 (.funscript)", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onExportCsv,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("导出 CSV 报表", color = Color(0xFF00E5FF), fontSize = 11.sp)
        }

        Button(
            onClick = onCopyClipboard,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF282050)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("复制 JSON", fontSize = 11.sp, color = Color.White)
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Handy matching tips card
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF140F2D)
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF00E5FF),
                modifier = Modifier.size(16.dp).padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "提示：将导出的 .funscript 文件与原视频同名存放在同目录下（例如 video.mp4 与 video.funscript），HandyControl、ScriptPlayer、Whirligig 或 DeoVR 播放器将自动加载并精确同步 The-Handy 设备。",
                fontSize = 11.sp,
                color = Color(0xFF9E9AC2),
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    color: Color = Color.White
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
        Text(text = label, fontSize = 10.sp, color = Color(0xFF8F8AA8))
    }
}
