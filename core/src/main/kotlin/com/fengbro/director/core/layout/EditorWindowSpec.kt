package com.fengbro.director.core.layout

enum class WidthClass {
    Compact,
    Medium,
    Expanded,
}

enum class HeightClass {
    Compact,
    Medium,
    Expanded,
}

/**
 * Phone vs tablet workspace, matching the desktop bench
 * (library | preview | inspector, timeline across the bottom).
 */
data class EditorWindowSpec(
    val widthDp: Int,
    val heightDp: Int,
    val widthClass: WidthClass,
    val heightClass: HeightClass,
    val useBench: Boolean,
    val useLandscapeSplit: Boolean,
    val libraryWidthDp: Int,
    val inspectorWidthDp: Int,
    val timelineHeightDp: Int,
    val showStartupTwoPane: Boolean,
) {
    val isCompactWidth: Boolean get() = widthClass == WidthClass.Compact
    val isCompactHeight: Boolean get() = heightClass == HeightClass.Compact
    val useLibrarySheet: Boolean get() = !useBench
    val useInspectorSheet: Boolean get() = !useBench
    val useExportDialog: Boolean get() = useBench

    companion object {
        fun from(widthDp: Int, heightDp: Int): EditorWindowSpec {
            val width = widthDp.coerceAtLeast(1)
            val height = heightDp.coerceAtLeast(1)
            val widthClass = when {
                width < 600 -> WidthClass.Compact
                width < 840 -> WidthClass.Medium
                else -> WidthClass.Expanded
            }
            val heightClass = when {
                height < 480 -> HeightClass.Compact
                height < 900 -> HeightClass.Medium
                else -> HeightClass.Expanded
            }
            val useBench = widthClass != WidthClass.Compact && heightClass != HeightClass.Compact
            val useLandscapeSplit = widthClass != WidthClass.Compact && heightClass == HeightClass.Compact
            val libraryWidthDp = if (widthClass == WidthClass.Expanded) 300 else 260
            val inspectorWidthDp = if (widthClass == WidthClass.Expanded) 268 else 236
            val timelineHeightDp = when {
                useBench && heightClass == HeightClass.Expanded -> 280
                useBench -> 236
                useLandscapeSplit -> (height - 52).coerceIn(140, 220)
                else -> 188
            }
            return EditorWindowSpec(
                widthDp = width,
                heightDp = height,
                widthClass = widthClass,
                heightClass = heightClass,
                useBench = useBench,
                useLandscapeSplit = useLandscapeSplit,
                libraryWidthDp = libraryWidthDp,
                inspectorWidthDp = inspectorWidthDp,
                timelineHeightDp = timelineHeightDp,
                showStartupTwoPane = useBench,
            )
        }
    }
}
