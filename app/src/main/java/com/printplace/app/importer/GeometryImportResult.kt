package com.printplace.app.importer

import com.printplace.app.mesh.Mesh
import com.printplace.app.model.ModelDimensions

/**
 * Common result type for every format-specific geometry importer (STL, and
 * later 3MF). [Success.dimensions] is already resolved to millimetres --
 * unit interpretation is inherently format-specific (STL assumes mm; 3MF
 * declares its own unit) so each parser resolves it itself rather than
 * leaving callers to reinterpret [Mesh]'s native-unit bounding box.
 */
sealed interface GeometryImportResult {
    data class Success(val mesh: Mesh, val dimensions: ModelDimensions) : GeometryImportResult
    data class Failure(val reason: String) : GeometryImportResult
}
