package com.printplace.app.model

/**
 * The 3D file formats PrintPlace can import. Kept separate from any parsing
 * or rendering code so new formats can be added without touching those layers.
 */
enum class ModelFormat(val extension: String, val displayName: String) {
    STL("stl", "STL"),
    THREE_MF("3mf", "3MF");

    companion object {
        fun fromExtension(extension: String): ModelFormat? =
            entries.firstOrNull { it.extension.equals(extension, ignoreCase = true) }
    }
}
