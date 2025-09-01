package fpw

import fpw.ren.VertexFormatBuilder.Companion.buildVertexFormat
import java.nio.ByteBuffer

object TestCube
{
	val format = buildVertexFormat {
		location3D()
		texcoord2D()
	}

	val vertices: ByteBuffer
	val indices: ByteBuffer

	init
	{
		val vp = floatArrayOf(
			-0.5f, +0.5f, +0.5f,
			-0.5f, -0.5f, +0.5f,
			+0.5f, -0.5f, +0.5f,
			+0.5f, +0.5f, +0.5f,
			-0.5f, +0.5f, -0.5f,
			+0.5f, +0.5f, -0.5f,
			-0.5f, -0.5f, -0.5f,
			+0.5f, -0.5f, -0.5f,
		)
		val vuv = floatArrayOf(
			0.0f, 0.0f,
			0.5f, 0.0f,
			1.0f, 0.0f,
			1.0f, 0.5f,
			1.0f, 1.0f,
			0.5f, 1.0f,
			0.0f, 1.0f,
			0.0f, 0.5f,
		)
		val vi = intArrayOf(
			// Front face
			0, 1, 3, 3, 1, 2,
			// Top Face
			4, 0, 3, 5, 4, 3,
			// Right face
			3, 2, 7, 5, 3, 7,
			// Left face
			6, 1, 0, 6, 0, 4,
			// Bottom face
			2, 1, 6, 2, 6, 7,
			// Back face
			7, 6, 4, 7, 4, 5,
		)

		val vertexCount = vp.size / 3
		vertices = FUtil.createBuffer(vertexCount * format.stride)
		indices = FUtil.createBuffer(vi.size * 4)

		for (i in 0..<vertexCount)
		{
			vertices.apply {
				putFloat(vp[i*3])
				putFloat(vp[i*3+1])
				putFloat(vp[i*3+2])
				putFloat(vuv[i*2])
				putFloat(vuv[i*2+1])
			}
		}
		vertices.flip()

		for (i in 0..<vi.size)
		{
			indices.putInt(vi[i])
		}
		indices.flip()
	}
}