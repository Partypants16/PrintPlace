package com.printplace.app.importer.stl

import com.printplace.app.importer.toDimensions
import com.printplace.app.mesh.BoundingBox
import com.printplace.app.model.DimensionUnit
import com.printplace.app.model.ModelDimensions

/**
 * STL carries no unit information at all, so per the project's unit
 * strategy (architecture doc section 6) its geometry is interpreted as
 * millimetres by default. This is the one place that assumption is made,
 * so a future per-model unit override only needs to change this call site.
 */
fun BoundingBox.asMillimeterDimensions(): ModelDimensions = toDimensions(DimensionUnit.MILLIMETER)
