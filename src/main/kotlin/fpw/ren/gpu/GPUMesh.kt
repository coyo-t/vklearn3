package fpw.ren.gpu


class GPUMesh(
	val id: String,
	val verticesBuffer: GPUBuffer,
	val indicesBuffer: GPUBuffer,
	val numIndices: Int
): GPUClosable
{
	override fun close (context: GPUContext)
	{
		verticesBuffer.close(context)
		indicesBuffer.close(context)
	}
}