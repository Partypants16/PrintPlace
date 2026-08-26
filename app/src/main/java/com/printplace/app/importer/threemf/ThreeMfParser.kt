package com.printplace.app.importer.threemf

import com.printplace.app.importer.DEFAULT_MAX_TRIANGLES
import com.printplace.app.importer.GeometryImportResult
import com.printplace.app.importer.GrowableFloatBuffer
import com.printplace.app.importer.toDimensions
import com.printplace.app.importer.tooComplexFailure
import com.printplace.app.mesh.BoundingBox
import com.printplace.app.mesh.Mesh
import com.printplace.app.mesh.Vec3
import com.printplace.app.model.DimensionUnit
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import kotlin.math.sqrt
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.SAXException

/**
 * Parses 3MF (a ZIP package containing an XML model part) into an internal
 * [Mesh]. Pure JVM code -- no Android types -- for the same reason as
 * [com.printplace.app.importer.stl.StlParser]: plain JUnit tests with
 * synthetic fixtures, no Robolectric/instrumented tests required.
 *
 * Deliberately unsupported for this milestone (fails gracefully rather than
 * crashing, per architecture doc section 7):
 *  - `<components>` (nested/assembled objects) -- only objects with a direct
 *    `<mesh>` are resolved; components-only objects are skipped.
 *  - Materials/colours -- geometry and scale take priority per the spec.
 *  - Multiple model parts / non-default part naming beyond what `_rels/.rels`
 *    or the conventional `3D/3dmodel.model` path points at.
 */
object ThreeMfParser {

    private const val DEFAULT_MODEL_PART = "3D/3dmodel.model"
    private const val RELS_PART = "_rels/.rels"
    private const val START_PART_RELATIONSHIP_SUFFIX = "/3dmodel"

    fun parse(file: File, maxTriangles: Int = DEFAULT_MAX_TRIANGLES): GeometryImportResult = try {
        ZipFile(file).use { zip -> parseZip(zip, maxTriangles) }
    } catch (e: ZipException) {
        GeometryImportResult.Failure("This file does not appear to be a valid 3MF package.")
    } catch (e: IOException) {
        GeometryImportResult.Failure("The selected file could not be opened.")
    } catch (e: Exception) {
        // Defensive backstop for any XML/structural edge case not anticipated by the more
        // specific handling below -- section 7/18 require failing gracefully here, never crashing.
        GeometryImportResult.Failure("This 3MF file contains features that are not currently supported.")
    }

    private fun parseZip(zip: ZipFile, maxTriangles: Int): GeometryImportResult {
        val modelEntry = zip.getEntry(resolveModelPartPath(zip))
            ?: return GeometryImportResult.Failure("This file does not appear to be a valid 3MF package.")

        val document = zip.getInputStream(modelEntry).use { parseXml(it) }
        val modelElement = document.documentElement
        if (!modelElement.tagName.equals("model", ignoreCase = true)) {
            return GeometryImportResult.Failure("This file does not appear to be a valid 3MF package.")
        }

        val unit = parseUnit(modelElement.getAttribute("unit"))
            ?: return GeometryImportResult.Failure("This 3MF file contains features that are not currently supported.")

        val objects = parseObjects(modelElement)
        val buildElement = modelElement.getElementsByTagName("build").item(0) as? Element
        if (objects.isEmpty() || buildElement == null) {
            return GeometryImportResult.Failure("No printable geometry was found in this file.")
        }

        return assembleMesh(objects, buildElement, unit, maxTriangles)
    }

    private fun assembleMesh(
        objects: Map<String, ThreeMfObject>,
        buildElement: Element,
        unit: DimensionUnit,
        maxTriangles: Int,
    ): GeometryImportResult {
        val vertexBuffer = GrowableFloatBuffer()
        val normalBuffer = GrowableFloatBuffer()
        var boundingBox: BoundingBox? = null
        var triangleCount = 0

        val items = buildElement.getElementsByTagName("item")
        for (i in 0 until items.length) {
            val itemElement = items.item(i) as? Element ?: continue
            val obj = objects[itemElement.getAttribute("objectid")] ?: continue // unresolved/unsupported: skip
            val transform = ThreeMfTransform.parse(itemElement.getAttribute("transform")) ?: continue

            for ((i1, i2, i3) in obj.triangles) {
                if (i1 !in obj.vertices.indices || i2 !in obj.vertices.indices || i3 !in obj.vertices.indices) {
                    return GeometryImportResult.Failure("This 3MF file contains features that are not currently supported.")
                }
                val p1 = transform.apply(obj.vertices[i1])
                val p2 = transform.apply(obj.vertices[i2])
                val p3 = transform.apply(obj.vertices[i3])

                vertexBuffer.add(p1.x); vertexBuffer.add(p1.y); vertexBuffer.add(p1.z)
                vertexBuffer.add(p2.x); vertexBuffer.add(p2.y); vertexBuffer.add(p2.z)
                vertexBuffer.add(p3.x); vertexBuffer.add(p3.y); vertexBuffer.add(p3.z)
                val normal = faceNormal(p1, p2, p3)
                normalBuffer.add(normal.x); normalBuffer.add(normal.y); normalBuffer.add(normal.z)

                boundingBox = (boundingBox?.expandedBy(p1) ?: BoundingBox.startingAt(p1)).expandedBy(p2).expandedBy(p3)
                triangleCount++
                if (triangleCount > maxTriangles) return tooComplexFailure(triangleCount.toLong())
            }
        }

        val finalBoundingBox = boundingBox
        if (triangleCount == 0 || finalBoundingBox == null) {
            return GeometryImportResult.Failure("No printable geometry was found in this file.")
        }

        val mesh = Mesh(vertexBuffer.toFloatArray(), normalBuffer.toFloatArray(), triangleCount, finalBoundingBox)
        return GeometryImportResult.Success(mesh, finalBoundingBox.toDimensions(unit))
    }

    /**
     * The 3MF spec's `unit` attribute defaults to millimetre when absent -- that's a documented
     * default, not a guess. An attribute that IS present but holds a value outside the six legal
     * units is a red flag (malformed file or an extension we don't understand); given how
     * scale-critical units are to this app, we refuse to guess at those rather than silently
     * defaulting, unlike the absent case.
     */
    private fun parseUnit(raw: String): DimensionUnit? = when (raw.trim().lowercase()) {
        "", "millimeter" -> DimensionUnit.MILLIMETER
        "micron" -> DimensionUnit.MICRON
        "centimeter" -> DimensionUnit.CENTIMETER
        "meter" -> DimensionUnit.METER
        "inch" -> DimensionUnit.INCH
        "foot" -> DimensionUnit.FOOT
        else -> null
    }

    private data class ThreeMfObject(val vertices: List<Vec3>, val triangles: List<Triple<Int, Int, Int>>)

    /** Only resolves objects with a direct `<mesh>`; components-only objects are unsupported (skipped). */
    private fun parseObjects(modelElement: Element): Map<String, ThreeMfObject> {
        val result = mutableMapOf<String, ThreeMfObject>()
        val objectNodes = modelElement.getElementsByTagName("object")
        for (i in 0 until objectNodes.length) {
            val objectElement = objectNodes.item(i) as? Element ?: continue
            val id = objectElement.getAttribute("id")
            if (id.isBlank()) continue
            val meshElement = objectElement.getElementsByTagName("mesh").item(0) as? Element ?: continue
            val vertices = parseVertices(meshElement)
            val triangles = parseTriangles(meshElement)
            if (vertices.isNotEmpty() && triangles.isNotEmpty()) {
                result[id] = ThreeMfObject(vertices, triangles)
            }
        }
        return result
    }

    private fun parseVertices(meshElement: Element): List<Vec3> {
        val nodes = meshElement.getElementsByTagName("vertex")
        val result = ArrayList<Vec3>(nodes.length)
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as? Element ?: return emptyList()
            val x = el.getAttribute("x").toFloatOrNull() ?: return emptyList()
            val y = el.getAttribute("y").toFloatOrNull() ?: return emptyList()
            val z = el.getAttribute("z").toFloatOrNull() ?: return emptyList()
            result.add(Vec3(x, y, z))
        }
        return result
    }

    private fun parseTriangles(meshElement: Element): List<Triple<Int, Int, Int>> {
        val nodes = meshElement.getElementsByTagName("triangle")
        val result = ArrayList<Triple<Int, Int, Int>>(nodes.length)
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as? Element ?: return emptyList()
            val v1 = el.getAttribute("v1").toIntOrNull() ?: return emptyList()
            val v2 = el.getAttribute("v2").toIntOrNull() ?: return emptyList()
            val v3 = el.getAttribute("v3").toIntOrNull() ?: return emptyList()
            result.add(Triple(v1, v2, v3))
        }
        return result
    }

    private fun faceNormal(p1: Vec3, p2: Vec3, p3: Vec3): Vec3 {
        val ux = p2.x - p1.x; val uy = p2.y - p1.y; val uz = p2.z - p1.z
        val vx = p3.x - p1.x; val vy = p3.y - p1.y; val vz = p3.z - p1.z
        val nx = uy * vz - uz * vy
        val ny = uz * vx - ux * vz
        val nz = ux * vy - uy * vx
        val length = sqrt(nx * nx + ny * ny + nz * nz)
        return if (length > 0f) Vec3(nx / length, ny / length, nz / length) else Vec3(0f, 0f, 0f)
    }

    /**
     * Resolves the root model part via `_rels/.rels` (the spec-correct way), falling back to the
     * conventional path if the rels part is missing or unparsable -- most real-world 3MF writers
     * use the conventional path anyway, so a broken/absent rels part shouldn't sink the import.
     */
    private fun resolveModelPartPath(zip: ZipFile): String {
        val relsEntry = zip.getEntry(RELS_PART) ?: return DEFAULT_MODEL_PART
        val relsDocument = try {
            zip.getInputStream(relsEntry).use { parseXml(it) }
        } catch (e: IOException) {
            return DEFAULT_MODEL_PART
        } catch (e: SAXException) {
            return DEFAULT_MODEL_PART
        } catch (e: ParserConfigurationException) {
            return DEFAULT_MODEL_PART
        }

        val relationships = relsDocument.getElementsByTagName("Relationship")
        for (i in 0 until relationships.length) {
            val element = relationships.item(i) as? Element ?: continue
            if (element.getAttribute("Type").endsWith(START_PART_RELATIONSHIP_SUFFIX)) {
                val target = element.getAttribute("Target").removePrefix("/")
                if (target.isNotBlank()) return target
            }
        }
        return DEFAULT_MODEL_PART
    }

    /** Hardened against XXE: untrusted files must not be able to read local files or reach the network. */
    private fun parseXml(input: InputStream): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            trySetFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            trySetFeature("http://xml.org/sax/features/external-general-entities", false)
            trySetFeature("http://xml.org/sax/features/external-parameter-entities", false)
            trySetFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            isExpandEntityReferences = false
        }
        return factory.newDocumentBuilder().parse(input)
    }

    private fun DocumentBuilderFactory.trySetFeature(name: String, value: Boolean) {
        try {
            setFeature(name, value)
        } catch (e: ParserConfigurationException) {
            // Feature not recognised by this JVM/Android XML provider -- best effort, keep going.
        }
    }
}
