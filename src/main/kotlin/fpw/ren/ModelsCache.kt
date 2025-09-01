package fpw.ren

import fpw.ren.gpu.*
import fpw.ren.gpu.queuez.CommandQueue
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.VK14.*


class ModelsCache
{
	val modelMap = mutableMapOf<String, GPUModel>()


	fun close (context: GPUContext)
	{
		modelMap.values.forEach { it.cleanup(context) }
		modelMap.clear()
	}

	fun loadModels (
		context: GPUContext,
		models: List<GPUModelData>,
		commandPool: CommandPool,
		queue: CommandQueue,
	)
	{
		val stagingBufferList = mutableListOf<GPUBuffer>()

		val cmd = CommandBuffer(context, commandPool, primary = true, oneTimeSubmit = true)
		cmd.recordSubmitAndWait(context, queue) {
			for (modelData in models)
			{
				val vulkanModel = GPUModel(modelData.id)
				modelMap[vulkanModel.id] = vulkanModel

				// Transform meshes loading their data into GPU buffers
				for (meshData in modelData.meshes)
				{
					val verticesBuffers = createVerticesBuffers(context, meshData)
					val indicesBuffers = createIndicesBuffers(context, meshData)
					stagingBufferList.add(verticesBuffers.from)
					stagingBufferList.add(indicesBuffers.from)
					verticesBuffers.recordTransferCommand(cmd)
					indicesBuffers.recordTransferCommand(cmd)

					val vulkanMesh = GPUMesh(
						verticesBuffers.to,
						indicesBuffers.to, meshData.indices.size
					)
					vulkanModel.vulkanMeshList.add(vulkanMesh)
				}
			}
		}
		cmd.cleanup(context, commandPool)
		stagingBufferList.forEach { it.free(context) }
	}

	private fun createIndicesBuffers(context: GPUContext, meshData: GPUMeshData): TransferBuffer
	{
		val indices = meshData.indices
		val numIndices = indices.size
		val bufferSize = (numIndices * GPUtil.SIZEOF_INT).toLong()

		val srcBuffer = GPUBuffer(
			context,
			bufferSize,
			VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
			VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
		)
		val dstBuffer = GPUBuffer(
			context,
			bufferSize,
			VK_BUFFER_USAGE_TRANSFER_DST_BIT or VK_BUFFER_USAGE_INDEX_BUFFER_BIT,
			VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
		)

		srcBuffer.doMapped(context) { mapped ->
			val data = MemoryUtil.memIntBuffer(mapped, srcBuffer.requestedSize.toInt())
			data.put(indices)
		}
		return TransferBuffer(srcBuffer, dstBuffer)
	}

	private fun createVerticesBuffers(context: GPUContext, meshData: GPUMeshData): TransferBuffer
	{
		val positions = meshData.positions
		var texCoords = meshData.texCoords
		// This Is Stupid
		if (texCoords.isEmpty())
		{
			texCoords = FloatArray(positions.size / 3 * 2) { 0f }
		}
		val numElements = positions.size + texCoords.size
		val bufferSize = (numElements * GPUtil.SIZEOF_FLOAT).toLong()

		val srcBuffer = GPUBuffer(
			context, bufferSize,
			VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
			(
				VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or
				VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
			)
		)
		val dstBuffer = GPUBuffer(
			context, bufferSize,
			(
				VK_BUFFER_USAGE_TRANSFER_DST_BIT or
				VK_BUFFER_USAGE_VERTEX_BUFFER_BIT
			),
			VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
		)

		srcBuffer.doMapped(context) { mappedMemory ->
			val data = MemoryUtil.memFloatBuffer(mappedMemory, srcBuffer.requestedSize.toInt())

			val rows = positions.size / 3
			for (row in 0..<rows)
			{
				val coi = row * 3
				val uvi = row * 2
				with (data)
				{
					put(positions[coi])
					put(positions[coi + 1])
					put(positions[coi + 2])
					put(texCoords[uvi])
					put(texCoords[uvi + 1])
				}
			}
		}
		return TransferBuffer(srcBuffer, dstBuffer)
	}


}