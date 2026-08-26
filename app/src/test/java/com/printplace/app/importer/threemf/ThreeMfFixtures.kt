package com.printplace.app.importer.threemf

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Builds small synthetic 3MF (ZIP + XML) byte streams so parser tests don't depend on real fixture files. */
object ThreeMfFixtures {

    data class ObjectDef(
        val id: String,
        val vertices: List<Triple<Float, Float, Float>> = emptyList(),
        val triangles: List<Triple<Int, Int, Int>> = emptyList(),
        val rawContent: String? = null, // escape hatch for e.g. <components> instead of <mesh>
    )

    data class ItemDef(val objectId: String, val transform: String? = null)

    /** An 8-vertex, 12-triangle `width` x `depth` x `height` box, corner at the origin. */
    fun box(id: String, width: Float, depth: Float, height: Float): ObjectDef = ObjectDef(
        id = id,
        vertices = listOf(
            Triple(0f, 0f, 0f), Triple(width, 0f, 0f), Triple(width, depth, 0f), Triple(0f, depth, 0f),
            Triple(0f, 0f, height), Triple(width, 0f, height), Triple(width, depth, height), Triple(0f, depth, height),
        ),
        triangles = listOf(
            Triple(0, 1, 2), Triple(0, 2, 3), // bottom
            Triple(4, 6, 5), Triple(4, 7, 6), // top
            Triple(0, 5, 1), Triple(0, 4, 5), // front
            Triple(3, 2, 6), Triple(3, 6, 7), // back
            Triple(0, 3, 7), Triple(0, 7, 4), // left
            Triple(1, 6, 2), Triple(1, 5, 6), // right
        ),
    )

    fun cube(id: String, size: Float): ObjectDef = box(id, size, size, size)

    fun modelXml(unit: String?, objects: List<ObjectDef>, items: List<ItemDef>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        val unitAttr = if (unit != null) " unit=\"$unit\"" else ""
        append("<model xmlns=\"http://schemas.microsoft.com/3dmanufacturing/core/2015/02\"$unitAttr>")
        append("<resources>")
        for (obj in objects) {
            append("<object id=\"${obj.id}\" type=\"model\">")
            if (obj.rawContent != null) {
                append(obj.rawContent)
            } else {
                append("<mesh><vertices>")
                for (v in obj.vertices) append("<vertex x=\"${v.first}\" y=\"${v.second}\" z=\"${v.third}\"/>")
                append("</vertices><triangles>")
                for (t in obj.triangles) append("<triangle v1=\"${t.first}\" v2=\"${t.second}\" v3=\"${t.third}\"/>")
                append("</triangles></mesh>")
            }
            append("</object>")
        }
        append("</resources><build>")
        for (item in items) {
            val transformAttr = if (item.transform != null) " transform=\"${item.transform}\"" else ""
            append("<item objectid=\"${item.objectId}\"$transformAttr/>")
        }
        append("</build></model>")
    }

    fun zipBytes(modelXml: String, includeRels: Boolean = true, modelPath: String = "3D/3dmodel.model"): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            if (includeRels) {
                zip.putNextEntry(ZipEntry("_rels/.rels"))
                zip.write(relsXml(modelPath).toByteArray())
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry(modelPath))
            zip.write(modelXml.toByteArray())
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    private fun relsXml(modelPath: String): String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Target=\"/$modelPath\" Id=\"rel0\" " +
            "Type=\"http://schemas.microsoft.com/3dmanufacturing/2013/01/relationships/3dmodel\"/>" +
            "</Relationships>"
}
