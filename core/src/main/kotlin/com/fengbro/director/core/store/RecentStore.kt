package com.fengbro.director.core.store

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File

@Serializable
data class RecentProject(
    val path: String,
    val name: String,
    val touchedAtEpochMs: Long,
)

object RecentStore {
    fun load(file: File): List<RecentProject> {
        if (!file.exists()) return emptyList()
        return runCatching {
            ProjectStore.json.decodeFromString<List<RecentProject>>(file.readText())
        }.getOrDefault(emptyList())
    }

    fun touch(file: File, path: String, name: String, limit: Int = 20): List<RecentProject> {
        val now = System.currentTimeMillis()
        val next = buildList {
            add(RecentProject(path, name, now))
            for (item in load(file)) {
                if (item.path != path) add(item)
            }
        }.take(limit)
        file.parentFile?.mkdirs()
        file.writeText(ProjectStore.json.encodeToString(next))
        return next
    }
}
