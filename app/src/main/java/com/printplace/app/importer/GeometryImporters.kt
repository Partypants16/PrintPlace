package com.printplace.app.importer

import com.printplace.app.importer.stl.StlParser
import com.printplace.app.importer.threemf.ThreeMfParser
import com.printplace.app.model.ModelFormat
import java.io.File

/**
 * Looks up the geometry parser for a given format, so callers (the
 * repository) don't need a format-specific `when` of their own.
 */
object GeometryImporters {
    fun forFormat(format: ModelFormat): ((File) -> GeometryImportResult)? = when (format) {
        ModelFormat.STL -> StlParser::parse
        ModelFormat.THREE_MF -> ThreeMfParser::parse
    }
}
