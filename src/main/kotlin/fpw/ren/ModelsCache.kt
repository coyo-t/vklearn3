package fpw.ren

import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.VK14.*
import fpw.ren.gpu.GPUBuffer
import fpw.ren.gpu.GPUClosable
import fpw.ren.gpu.GPUCommandBuffer
import fpw.ren.gpu.GPUCommandPool
import fpw.ren.gpu.GPUCommandQueue
import fpw.ren.gpu.GPUContext
import fpw.ren.gpu.GPUMesh
import fpw.ren.gpu.GPUMeshData
import fpw.ren.gpu.GPUModel
import fpw.ren.gpu.GPUModelData
import fpw.ren.gpu.GPUTransferBuffer
import fpw.ren.gpu.GPUtil


class ModelsCache: GPUClosable
{
	val modelMap = mutableMapOf<String, GPUModel>()


	override fun close (context: GPUContext)
	{
		modelMap.forEach { (k, v) -> v.cleanup(context) }
		modelMap.clear()
	}

	fun loadModels (
		context: GPUContext,
		models: List<GPUModelData>,
		commandPool: GPUCommandPool,
		queue: GPUCommandQueue,
	)
	{
		val stagingBufferList = mutableListOf<GPUBuffer>()

		val cmd = GPUCommandBuffer(context, commandPool, primary = true, oneTimeSubmit = true)
		cmd.record {
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
						meshData.id, verticesBuffers.to,
						indicesBuffers.to, meshData.indices.size
					)
					vulkanModel.vulkanMeshList.add(vulkanMesh)
				}
			}
		}

		cmd.submitAndWait(context, queue)
		cmd.cleanup(context, commandPool)
		stagingBufferList.forEach { it.close(context) }
	}

	private fun createIndicesBuffers(context: GPUContext, meshData: GPUMeshData): GPUTransferBuffer
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
		return GPUTransferBuffer(srcBuffer, dstBuffer)
	}

	private fun createVerticesBuffers(context: GPUContext, meshData: GPUMeshData): GPUTransferBuffer
	{
		val positions = meshData.positions
		val numElements = positions.size
		val bufferSize = (numElements * GPUtil.SIZEOF_FLOAT).toLong()

		val srcBuffer = GPUBuffer(
			context, bufferSize,
			VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
			VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
		)
		val dstBuffer = GPUBuffer(
			context, bufferSize,
			VK_BUFFER_USAGE_TRANSFER_DST_BIT or VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
			VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
		)

		srcBuffer.doMapped(context) { mappedMemory ->
			val data = MemoryUtil.memFloatBuffer(mappedMemory, srcBuffer.requestedSize.toInt())

			val rows = positions.size / 3
			for (row in 0..<rows)
			{
				val startPos = row * 3
				data.put(positions[startPos])
				data.put(positions[startPos + 1])
				data.put(positions[startPos + 2])
			}
		}
		return GPUTransferBuffer(srcBuffer, dstBuffer)
	}


}