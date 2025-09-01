package fpw.ren.gpu

import fpw.Renderer


class GPUModel(val id: String)
{

	val vulkanMeshList = mutableListOf<GPUMesh>()

	fun cleanup(vkCtx: Renderer)
	{
		vulkanMeshList.forEach { it.free(vkCtx) }
	}

}