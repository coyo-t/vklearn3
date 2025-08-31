package fpw.ren.gpu


class GPUMesh(
	val verticesBuffer: GPUBuffer,
	val indicesBuffer: GPUBuffer,
	val numIndices: Int
)
{
	fun free (context: GPUContext)
	{
		verticesBuffer.free(context)
		indicesBuffer.free(context)
	}
}