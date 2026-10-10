package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun AlgorithmInfoDialog(
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("algorithm_info_dialog"),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF17132B)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "FunscriptDancer 算法原理",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp).testTag("btn_close_info_dialog")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "FunscriptDancer 是由开源社区开发的节拍驱动动作生成工具，核心在于将音乐音频信号精准翻译为节奏冲程（Stroke）与肢体舞蹈动作。",
                    fontSize = 12.sp,
                    color = Color(0xFFC7C3E2),
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                InfoSectionCard(
                    title = "1. 音频特征映射机制",
                    color = Color(0xFF00E5FF),
                    items = listOf(
                        "🎵 节拍周期 (Beats)：每个音乐重音或节拍触发一次完整的往复冲程（Stroke）。",
                        "🎼 基础中心 (Center)：由音频音高（Pitch / 频谱质心）决定。高音使舞者重心上浮，低音（Sub-Bass）使重心下沉蹲伏。",
                        "⚡ 冲程幅度 (Amplitude)：由音频 RMS 能量/响度决定。强奏触发大开大合的动作，弱奏形成细腻微颤。"
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                InfoSectionCard(
                    title = "2. 三大越界处理策略 (OOB Strategies)",
                    color = Color(0xFFFF4081),
                    items = listOf(
                        "✂️ Crop (截断)：当计算出的动作位置超出 [0, 100] 时，直接截断在 0 或 100。动作平稳安全，但损失部分极限冲程距离。",
                        "⚡ Bounce (反弹)：当动作超出边界时，将超出的幅度向中心反弹（Over-range 反折）。完全保留了预期的动作总行程距离，形成强烈的反弹节奏冲击！",
                        "🔀 Fold (折叠)：当动作超限时，将行程折返至中点后重新延展。在有限的物理空间中紧凑释放全量动能，呈现精巧紧凑的肢体节奏。"
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                InfoSectionCard(
                    title = "3. 骨骼动力学与舞蹈流派",
                    color = Color(0xFFFFD700),
                    items = listOf(
                        "🕺 垂直律动：冲程位置直接驱动躯干起伏、屈膝下蹲与滞空腾跃。",
                        "💃 肢体姿态：依据选择的流派（Popping/Groove/Wave/Robot）赋予手臂逆运动学（IK）、身体波浪与卡点震颤。",
                        "✨ 舞台反馈：实时根据冲程速度和重音激活动态光晕、地表反射与节拍冲击波。"
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_got_it"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("了解并返回", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun InfoSectionCard(
    title: String,
    color: Color,
    items: List<String>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF201B3B))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(modifier = Modifier.height(6.dp))
            items.forEach { item ->
                Text(
                    text = item,
                    fontSize = 11.sp,
                    color = Color(0xFFDDD9F3),
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}
