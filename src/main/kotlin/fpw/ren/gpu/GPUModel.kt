package fpw.ren.gpu


class GPUModel(val id: String)
{

	val vulkanMeshList = mutableListOf<GPUMesh>()

	fun cleanup(vkCtx: GPUContext)
	{
		vulkanMeshList.forEach { it.free(vkCtx) }
	}

}