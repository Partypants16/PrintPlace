package com.printplace.app.model

/**
 * Real-world bounding-box dimensions of an imported model, always normalised
 * to millimetres for internal use (the AR layer's mm-to-metre conversion
 * depends on that). [sourceUnit] records what was assumed/declared by the
 * source file so the UI can disclose the assumption to the user.
 *
 * Null until the geometry importer (Milestone 2/3) has run.
 */
data class ModelDimensions(
    val widthMm: Double,
    val heightMm: Double,
    val depthMm: Double,
    val sourceUnit: DimensionUnit,
)
