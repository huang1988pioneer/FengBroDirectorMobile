package com.fengbro.director.core.store

import com.fengbro.director.core.model.EditorProject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object ProjectStore {
    val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun save(project: EditorProject, path: String) {
        project.filePath = path
        project.modifiedAtEpochMs = System.currentTimeMillis()
        project.isDirty = false
        File(path).writeText(snapshot(project))
    }

    fun load(path: String): EditorProject {
        val project = restore(File(path).readText(), path)
        project.isDirty = false
        return project
    }

    fun snapshot(project: EditorProject): String = json.encodeToString(project)

    fun restore(snapshot: String, filePath: String?): EditorProject {
        val project = json.decodeFromString<EditorProject>(snapshot)
        project.filePath = filePath
        EditorProject.ensureDefaultTracks(project)
        project.isDirty = true
        return project
    }
}

class UndoStack {
    private val undo = ArrayDeque<String>()
    private val redo = ArrayDeque<String>()

    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()

    fun push(project: EditorProject) {
        undo.addLast(ProjectStore.snapshot(project))
        redo.clear()
        if (undo.size > 80) {
            while (undo.size > 60) undo.removeFirst()
        }
    }

    fun undo(current: EditorProject): EditorProject? {
        if (undo.isEmpty()) return null
        redo.addLast(ProjectStore.snapshot(current))
        return ProjectStore.restore(undo.removeLast(), current.filePath)
    }

    fun redo(current: EditorProject): EditorProject? {
        if (redo.isEmpty()) return null
        undo.addLast(ProjectStore.snapshot(current))
        return ProjectStore.restore(redo.removeLast(), current.filePath)
    }

    fun clear() {
        undo.clear()
        redo.clear()
    }
}
