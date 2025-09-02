package fpw.ren.gpu

import fpw.Renderer


class GPUMesh(
	val verticesBuffer: GPUBuffer,
	val indicesBuffer: GPUBuffer,
	val numIndices: Int
)
{
	fun free (context: Renderer)
	{
		verticesBuffer.free()
		indicesBuffer.free()
	}
}