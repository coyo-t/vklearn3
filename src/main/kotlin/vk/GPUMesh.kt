package vk

import com.catsofwar.vk.GPUContext


data class GPUMesh(
	val id: String,
	val verticesBuffer: GPUBuffer,
	val indicesBuffer: GPUBuffer,
	val numIndices: Int
)
{
	fun cleanup(vkCtx: GPUContext)
	{
		verticesBuffer.cleanup(vkCtx)
		indicesBuffer.cleanup(vkCtx)
	}
}