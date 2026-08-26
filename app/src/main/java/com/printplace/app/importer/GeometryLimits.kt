package com.printplace.app.importer

/**
 * Shared safety cap for every geometry importer: ~5M triangles is already far
 * beyond what a phone can preview smoothly, so this is a backstop against
 * exhausting device memory, not a quality target. Kept in one place so STL
 * and 3MF can't silently drift to different caps or wording.
 */
const val DEFAULT_MAX_TRIANGLES = 5_000_000

fun tooComplexFailure(triangleCount: Long): GeometryImportResult.Failure = GeometryImportResult.Failure(
    "This model is too complex to preview efficiently on this device.\n\nTriangles: $triangleCount"
)
