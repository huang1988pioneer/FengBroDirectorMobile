package com.fengbro.director.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fengbro.director.core.export.ExportPlan
import com.fengbro.director.core.model.ClipKind
import com.fengbro.director.core.model.ExportTarget
import com.fengbro.director.core.time.TimeUtil
import com.fengbro.director.ui.EditorUiState
import com.fengbro.director.ui.EditorViewModel
import com.fengbro.director.ui.theme.BgInput
import com.fengbro.director.ui.theme.BgPanel
import com.fengbro.director.ui.theme.BgPanel2
import com.fengbro.director.ui.theme.Ink
import com.fengbro.director.ui.theme.Line
import com.fengbro.director.ui.theme.Muted
import com.fengbro.director.ui.theme.Primary
import com.fengbro.director.ui.theme.PrimaryInk

@Composable
fun InspectorPanel(
    state: EditorUiState,
    vm: EditorViewModel,
    modifier: Modifier = Modifier,
) {
    val clip = state.selectedClip
    var name by remember(state.projectName) { mutableStateOf(state.projectName) }
    Column(
        modifier = modifier
            .background(BgPanel)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
    ) {
        Text(
            if (clip == null) "專案" else clip.name,
            color = Ink,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            if (clip == null) "${state.width}×${state.height}  ${"%.0f".format(30.0)} fps" else kindLabel(clip.kind),
            color = Muted,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(10.dp))
        if (clip == null) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("專案名稱") },
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            TextButton(onClick = { vm.saveNamed(name) }) { Text("儲存", color = Primary) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("浮水印", color = Ink, modifier = Modifier.weight(1f), fontSize = 13.sp)
                Switch(
                    checked = state.watermark,
                    onCheckedChange = vm::setWatermark,
                    colors = SwitchDefaults.colors(checkedTrackColor = Primary, checkedThumbColor = PrimaryInk),
                )
            }
            Text("鋒兄 / Papaya Feng / パパイヤ フェン", color = Muted, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            Text("畫面比例", color = Muted, fontSize = 12.sp)
            AspectRow(state, vm)
        } else {
            if (clip.kind == ClipKind.Subtitle || clip.kind == ClipKind.Title) {
                var text by remember(clip.id, state.generation) { mutableStateOf(clip.text) }
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        vm.updateSelectedText(it)
                    },
                    label = { Text("字幕文字") },
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text("開始 ${TimeUtil.formatClock(clip.start)}", color = Muted, fontSize = 12.sp)
            Text("時長 ${TimeUtil.formatClock(clip.duration)}", color = Muted, fontSize = 12.sp)
            Slider(
                value = clip.duration.toFloat().coerceIn(0.05f, 300f),
                onValueChange = { vm.updateSelectedDuration(it.toDouble()) },
                valueRange = 0.05f..maxOf(60f, clip.duration.toFloat() + 30f),
                colors = SliderDefaults.colors(thumbColor = Primary, activeTrackColor = Primary),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.nudgeSelected(-0.5) }, shape = RoundedCornerShape(8.dp)) {
                    Text("往左 0.5s", color = Ink, fontSize = 12.sp)
                }
                OutlinedButton(onClick = { vm.nudgeSelected(0.5) }, shape = RoundedCornerShape(8.dp)) {
                    Text("往右 0.5s", color = Ink, fontSize = 12.sp)
                }
            }
            Row {
                TextButton(onClick = vm::duplicate) { Text("再放一段", color = Ink) }
                TextButton(onClick = vm::deleteSelected) { Text("刪除", color = Ink) }
                TextButton(onClick = vm::split) { Text("分割", color = Ink) }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AspectRow(state: EditorUiState, vm: EditorViewModel) {
    val options = listOf(
        ExportTarget.YouTube1080 to "橫向 16:9",
        ExportTarget.TikTokVertical to "直向 9:16",
        ExportTarget.InstagramSquare to "方形 1:1",
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (target, label) ->
            val (w, h) = ExportPlan.preset(target)
            val selected = state.width == w && state.height == h
            FilterChip(
                selected = selected,
                onClick = { vm.setAspect(target) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Primary,
                    selectedLabelColor = PrimaryInk,
                    containerColor = BgPanel2,
                    labelColor = Ink,
                ),
            )
        }
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Ink,
    unfocusedTextColor = Ink,
    focusedBorderColor = Primary,
    unfocusedBorderColor = Line,
    focusedContainerColor = BgInput,
    unfocusedContainerColor = BgInput,
    focusedLabelColor = Muted,
    unfocusedLabelColor = Muted,
    cursorColor = Primary,
)

private fun kindLabel(kind: ClipKind): String = when (kind) {
    ClipKind.Audio -> "聲音"
    ClipKind.Subtitle, ClipKind.Title -> "字幕"
    ClipKind.Image -> "照片"
    else -> "畫面"
}
