package fpw.ren

import fpw.Renderer
import fpw.ren.gpu.*
import fpw.ren.gpu.CommandQueue
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.VK14.*
import org.lwjgl.vulkan.VkBufferCopy
import kotlin.use


class ModelsCache
{
	val modelMap = mutableMapOf<String, GPUMesh>()


	fun close (context: Renderer)
	{
		modelMap.values.forEach { it.free(context) }
		modelMap.clear()
	}

	fun loadModels (
		context: Renderer,
		commandPool: CommandPool,
		queue: CommandQueue,
		models: Pair<String, Mesh>
	)
	{
		val stagingBufferList = mutableListOf<GPUBuffer>()

		val cmd = CommandBuffer(context, commandPool, oneTimeSubmit = true)
		cmd.recordSubmitAndWait(context, queue) {
			val (id, meshData) = models
			// Transform meshes loading their data into GPU buffers
			val (vsrc, vdst) = createVerticesBuffers(context, meshData)
			val (isrc, idst) = createIndicesBuffers(context, meshData)
			stagingBufferList.add(vsrc)
			stagingBufferList.add(isrc)
			recordTransferCommand(cmd, vsrc, vdst)
			recordTransferCommand(cmd, isrc, idst)

			val vulkanMesh = GPUMesh(
				vdst,
				idst,
				meshData.indices.size,
			)
			modelMap[id] = vulkanMesh
		}
		cmd.cleanup(context, commandPool)
		stagingBufferList.forEach { it.free(context) }
	}

	private fun createIndicesBuffers(context: Renderer, meshData: Mesh): Pair<GPUBuffer, GPUBuffer>
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
		return srcBuffer to dstBuffer
	}

	private fun createVerticesBuffers(context: Renderer, meshData: Mesh): Pair<GPUBuffer, GPUBuffer>
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
			context,
			bufferSize,
			VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
			VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
		)
		val dstBuffer = GPUBuffer(
			context,
			bufferSize,
			VK_BUFFER_USAGE_TRANSFER_DST_BIT or VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
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
		return srcBuffer to dstBuffer
	}

	fun recordTransferCommand (cmd: CommandBuffer, from: GPUBuffer, to: GPUBuffer)
	{
		MemoryStack.stackPush().use { stack ->
			val copyRegion = VkBufferCopy
				.calloc(1, stack)
				.srcOffset(0)
				.dstOffset(0)
				.size(from.requestedSize)
			vkCmdCopyBuffer(cmd.vkCommandBuffer, from.bufferStruct, to.bufferStruct, copyRegion)
		}
	}
}