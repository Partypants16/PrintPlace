package com.printplace.app.ui.preview

import com.google.android.filament.Box
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndexBuffer
import com.google.android.filament.MaterialInstance
import com.google.android.filament.MathUtils
import com.google.android.filament.RenderableManager
import com.google.android.filament.VertexBuffer
import com.printplace.app.mesh.Mesh
import com.printplace.app.mesh.Vec3
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal data class FilamentMeshResources(
    val entity: Int,
    val vertexBuffer: VertexBuffer,
    val indexBuffer: IndexBuffer,
) {
    fun destroy(engine: Engine) {
        engine.destroyEntity(entity)
        engine.destroyVertexBuffer(vertexBuffer)
        engine.destroyIndexBuffer(indexBuffer)
        EntityManager.get().destroy(entity)
    }
}

internal object FilamentMeshBuilder {

    private const val FLOAT_BYTES = 4
    private const val POSITION_FLOATS = 3
    private const val TANGENT_FLOATS = 4
    private const val VERTEX_STRIDE = (POSITION_FLOATS + TANGENT_FLOATS) * FLOAT_BYTES

    fun build(engine: Engine, mesh: Mesh, material: MaterialInstance): FilamentMeshResources {
        require(mesh.vertices.size == mesh.triangleCount * 9) { "Mesh vertex data is incomplete." }
        val vertexCount = mesh.triangleCount * 3
        val vertexData = ByteBuffer.allocate(vertexCount * VERTEX_STRIDE).order(ByteOrder.nativeOrder())

        for (triangle in 0 until mesh.triangleCount) {
            val normal = resolvedNormal(mesh, triangle)
            val frame = tangentFrameFor(normal)
            val packed = FloatArray(4)
            MathUtils.packTangentFrame(
                frame.tangent.x, frame.tangent.y, frame.tangent.z,
                frame.bitangent.x, frame.bitangent.y, frame.bitangent.z,
                frame.normal.x, frame.normal.y, frame.normal.z,
                packed,
            )
            for (vertex in 0 until 3) {
                val base = triangle * 9 + vertex * 3
                vertexData.putFloat(mesh.vertices[base])
                vertexData.putFloat(mesh.vertices[base + 1])
                vertexData.putFloat(mesh.vertices[base + 2])
                packed.forEach(vertexData::putFloat)
            }
        }
        vertexData.flip()

        val vertexBuffer = VertexBuffer.Builder()
            .bufferCount(1)
            .vertexCount(vertexCount)
            .attribute(
                VertexBuffer.VertexAttribute.POSITION,
                0,
                VertexBuffer.AttributeType.FLOAT3,
                0,
                VERTEX_STRIDE,
            )
            .attribute(
                VertexBuffer.VertexAttribute.TANGENTS,
                0,
                VertexBuffer.AttributeType.FLOAT4,
                POSITION_FLOATS * FLOAT_BYTES,
                VERTEX_STRIDE,
            )
            .build(engine)
        vertexBuffer.setBufferAt(engine, 0, vertexData)

        val indexData = ByteBuffer.allocate(vertexCount * Int.SIZE_BYTES).order(ByteOrder.nativeOrder())
        repeat(vertexCount) { indexData.putInt(it) }
        indexData.flip()
        val indexBuffer = IndexBuffer.Builder()
            .indexCount(vertexCount)
            .bufferType(IndexBuffer.Builder.IndexType.UINT)
            .build(engine)
        indexBuffer.setBuffer(engine, indexData)

        val bounds = mesh.boundingBox
        val entity = EntityManager.get().create()
        RenderableManager.Builder(1)
            .boundingBox(
                Box(
                    (bounds.min.x + bounds.max.x) / 2f,
                    (bounds.min.y + bounds.max.y) / 2f,
                    (bounds.min.z + bounds.max.z) / 2f,
                    bounds.width / 2f,
                    bounds.depth / 2f,
                    bounds.height / 2f,
                ),
            )
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertexBuffer, indexBuffer, 0, vertexCount)
            .material(0, material)
            .culling(false)
            .build(engine, entity)

        return FilamentMeshResources(entity, vertexBuffer, indexBuffer)
    }

    private fun resolvedNormal(mesh: Mesh, triangle: Int): Vec3 {
        val normalBase = triangle * 3
        if (normalBase + 2 < mesh.normals.size) {
            val supplied = Vec3(
                mesh.normals[normalBase],
                mesh.normals[normalBase + 1],
                mesh.normals[normalBase + 2],
            )
            if (supplied.lengthSquared().isFinite() && supplied.lengthSquared() > 1e-12f) {
                return supplied.normalizedOr(Vec3(0f, 0f, 1f))
            }
        }

        val base = triangle * 9
        val first = Vec3(mesh.vertices[base], mesh.vertices[base + 1], mesh.vertices[base + 2])
        val second = Vec3(mesh.vertices[base + 3], mesh.vertices[base + 4], mesh.vertices[base + 5])
        val third = Vec3(mesh.vertices[base + 6], mesh.vertices[base + 7], mesh.vertices[base + 8])
        return (second - first).cross(third - first).normalizedOr(Vec3(0f, 0f, 1f))
    }
}
