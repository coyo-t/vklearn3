package vk

import com.catsofwar.vk.GPUContext


class GPUModel(val id: String)
{

	val vulkanMeshList = mutableListOf<GPUMesh>()

	fun cleanup(vkCtx: GPUContext)
	{
		vulkanMeshList.forEach { it.close(vkCtx) }
	}

}