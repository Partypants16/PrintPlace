package com.printplace.app.mesh

/**
 * Parsed triangle geometry, independent of both the source file format and
 * any rendering API (see architecture doc section 8 -- importers produce
 * this, renderers/AR consume it, neither talks to the other directly).
 *
 * STL has no concept of shared vertices, so triangles are stored "flat":
 * [vertices] holds 3 vertices per triangle (9 floats each) and [normals]
 * holds one normal per triangle (3 floats each), both in the order the
 * triangles were read. A renderer that wants indexed/deduplicated vertices
 * or per-vertex normals can derive that from this on demand -- deduplication
 * is a rendering-layer concern, not a parsing one.
 *
 * Note: because [vertices]/[normals] are arrays, the compiler-generated
 * equals()/hashCode() compare array *references*, not contents. Don't rely
 * on Mesh equality in tests or elsewhere; compare fields individually.
 */
data class Mesh(
    val vertices: FloatArray,
    val normals: FloatArray,
    val triangleCount: Int,
    val boundingBox: BoundingBox,
)
