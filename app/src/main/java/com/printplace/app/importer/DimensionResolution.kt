package com.printplace.app.importer

import com.printplace.app.mesh.BoundingBox
import com.printplace.app.model.DimensionUnit
import com.printplace.app.model.ModelDimensions
import com.printplace.app.model.millimetersPerUnit

/** Converts a bounding box expressed in [unit]-native coordinates into millimetre-normalised [ModelDimensions]. */
fun BoundingBox.toDimensions(unit: DimensionUnit): ModelDimensions {
    val factor = unit.millimetersPerUnit
    return ModelDimensions(
        widthMm = width.toDouble() * factor,
        heightMm = height.toDouble() * factor,
        depthMm = depth.toDouble() * factor,
        sourceUnit = unit,
    )
}
