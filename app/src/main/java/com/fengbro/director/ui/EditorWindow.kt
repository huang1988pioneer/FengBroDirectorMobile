package com.fengbro.director.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import com.fengbro.director.core.layout.EditorWindowSpec

@Composable
fun rememberEditorWindowSpec(): EditorWindowSpec {
    val configuration = LocalConfiguration.current
    return remember(configuration.screenWidthDp, configuration.screenHeightDp, configuration.orientation) {
        EditorWindowSpec.from(configuration.screenWidthDp, configuration.screenHeightDp)
    }
}

fun Configuration.toEditorWindowSpec(): EditorWindowSpec =
    EditorWindowSpec.from(screenWidthDp, screenHeightDp)
