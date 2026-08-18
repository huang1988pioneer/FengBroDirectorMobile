package com.fengbro.director.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.fengbro.director.core.layout.EditorWindowSpec
import com.fengbro.director.core.model.ExportTarget
import com.fengbro.director.core.store.RecentProject
import com.fengbro.director.ui.components.fieldColors
import com.fengbro.director.ui.theme.BgApp
import com.fengbro.director.ui.theme.BgPanel
import com.fengbro.director.ui.theme.BgPanel2
import com.fengbro.director.ui.theme.Ink
import com.fengbro.director.ui.theme.Muted
import com.fengbro.director.ui.theme.Primary
import com.fengbro.director.ui.theme.PrimaryInk

@Composable
fun StartupScreen(
    state: EditorUiState,
    spec: EditorWindowSpec,
    onStart: (String, ExportTarget) -> Unit,
    onImport: () -> Unit,
    onOpenRecent: (String) -> Unit,
) {
    var name by remember { mutableStateOf(state.projectName.ifBlank { "未命名專案" }) }
    var target by remember { mutableStateOf(ExportTarget.YouTube1080) }
    if (spec.showStartupTwoPane) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(BgApp)
                .padding(28.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            WelcomeColumn(
                name = name,
                onName = { name = it },
                target = target,
                onTarget = { target = it },
                onStart = { onStart(name, target) },
                onImport = onImport,
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
            )
            RecentsColumn(
                recents = state.recents,
                onOpen = onOpenRecent,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(BgPanel, RoundedCornerShape(12.dp))
                    .padding(20.dp),
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgApp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            WelcomeColumn(
                name = name,
                onName = { name = it },
                target = target,
                onTarget = { target = it },
                onStart = { onStart(name, target) },
                onImport = onImport,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
            )
            Spacer(Modifier.height(20.dp))
            RecentsColumn(recents = state.recents, onOpen = onOpenRecent, modifier = Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WelcomeColumn(
    name: String,
    onName: (String) -> Unit,
    target: ExportTarget,
    onTarget: (ExportTarget) -> Unit,
    onStart: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier,
) {
    val options = listOf(
        ExportTarget.YouTube1080 to "橫向 1080p",
        ExportTarget.TikTokVertical to "直向（手機）",
        ExportTarget.InstagramSquare to "方形",
        ExportTarget.YouTube4K to "橫向 4K",
    )
    Column(modifier = modifier, verticalArrangement = Arrangement.Center) {
        Text("鋒兄導演", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("匯入媒體、排到時間軸、加上字幕，再匯出成影片。", color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = name,
            onValueChange = onName,
            label = { Text("專案名稱") },
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, label) ->
                FilterChip(
                    selected = target == value,
                    onClick = { onTarget(value) },
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
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onStart,
            colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = PrimaryInk),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text("開始新專案", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onImport, shape = RoundedCornerShape(8.dp)) {
            Text("匯入媒體後開始", color = Ink)
        }
    }
}

@Composable
private fun RecentsColumn(
    recents: List<RecentProject>,
    onOpen: (String) -> Unit,
    modifier: Modifier,
) {
    Column(modifier) {
        Text("最近的專案", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        if (recents.isEmpty()) {
            Text("還沒有專案。按「開始新專案」即可。", color = Muted, fontSize = 13.sp)
        } else {
            recents.forEach { recent ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(recent.path) }
                        .padding(vertical = 10.dp),
                ) {
                    Text(recent.name, color = Ink, fontSize = 14.sp)
                    Text(recent.path.substringAfterLast('/'), color = Muted, fontSize = 11.sp)
                }
            }
        }
    }
}
