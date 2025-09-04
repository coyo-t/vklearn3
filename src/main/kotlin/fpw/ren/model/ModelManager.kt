package fpw.ren.model

import fpw.LuaCoyote
import fpw.ResourceLocation
import fpw.ren.GPUBuffer
import fpw.ren.GPUtil
import fpw.ren.model.InputMesh
import fpw.ren.Renderer
import fpw.ren.command.CommandBuffer
import fpw.ren.command.CommandPool
import fpw.ren.command.CommandSequence
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.vma.Vma
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VkBufferCopy
import party.iroiro.luajava.value.LuaTableValue

class ModelManager (val context: Renderer)
{
	val modelMap = mutableMapOf<ResourceLocation, Mesh>()


	fun free ()
	{
		modelMap.values.forEach {
			it.verticesBuffer.free()
			it.indicesBuffer.free()
		}
		modelMap.clear()
	}

	operator fun get (rl: ResourceLocation): Mesh?
	{
		if (rl in modelMap)
		{
			return modelMap[rl]!!
		}
		return ldMdl(rl)
	}

	private fun ldMdl (mRlrl: ResourceLocation): Mesh?
	{
		LuaCoyote().use { L ->
			L.openLibraries()
			val thing = (L.run(mRlrl) as? LuaTableValue) ?: return null
			val verticesTable = requireNotNull(thing["points"] as? LuaTableValue) {
				"model needs AT LEAST positions >:["
			}
			val vertexCount = verticesTable.length()
			val vertices = verticesTable.flatMap { (_, it) ->
				check(it is LuaTableValue)
				listOf(
					it[1].toNumber().toFloat(),
					it[2].toNumber().toFloat(),
					it[3].toNumber().toFloat(),
				)
			}.toFloatArray()

			val uvs = ((thing["uvs"] as? LuaTableValue)?.flatMap { (_, it) ->
				check(it is LuaTableValue)
				listOf(
					it[1].toNumber().toFloat(),
					it[2].toNumber().toFloat(),
				)
			}?.toFloatArray()) ?: FloatArray(vertexCount * 2) { 0f }

			val indices = requireNotNull(thing["indices"] as? LuaTableValue) {
				"I REQUIRE INDICES (for now -.-)"
			}.map { (_, it) ->
				it.toInteger().toInt()
			}.toIntArray()

			return loadModels(
				context,
				context.currentSwapChainDirector.commandPool,
				context.graphicsQueue,
				mRlrl,
				InputMesh(
					positions = vertices,
					texCoords = uvs,
					indices = indices
				),
			)
		}
	}

	fun loadModels (
		context: Renderer,
		commandPool: CommandPool,
		queue: CommandSequence,
		id: ResourceLocation,
		meshData: InputMesh,
	): Mesh
	{
		val stagingBufferList = mutableListOf<GPUBuffer>()

		val cmd = CommandBuffer(context, commandPool, oneTimeSubmit = true)
		cmd.beginRecording()
		val (vsrc, vdst) = createVerticesBuffers(context, meshData)
		val (isrc, idst) = createIndicesBuffers(context, meshData)
		stagingBufferList.add(vsrc)
		stagingBufferList.add(isrc)
		recordTransferCommand(cmd, vsrc, vdst)
		recordTransferCommand(cmd, isrc, idst)
		val outs = Mesh(
			vdst,
			idst,
			meshData.indices.size,
		)
		modelMap[id] = outs
		cmd.endRecording()
		cmd.submitAndWait(context, queue)
		cmd.free(context, commandPool)
		stagingBufferList.forEach { it.free() }
		return outs
	}

	private fun createIndicesBuffers(context: Renderer, meshData: InputMesh): Pair<GPUBuffer, GPUBuffer>
	{
		val indices = meshData.indices
		val numIndices = indices.size
		val bufferSize = (numIndices * GPUtil.SIZEOF_INT).toLong()

		val srcBuffer = GPUBuffer(
			context,
			bufferSize,
			VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
			Vma.VMA_MEMORY_USAGE_AUTO,
			Vma.VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT,
			VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
		)
		val dstBuffer = GPUBuffer(
			context,
			bufferSize,
			VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT or VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT,
			Vma.VMA_MEMORY_USAGE_AUTO,
			0,
			0,
		)

		srcBuffer.doMapped { mapped ->
			val data = MemoryUtil.memIntBuffer(mapped, srcBuffer.requestedSize.toInt())
			data.put(indices)
		}
		return srcBuffer to dstBuffer
	}

	private fun createVerticesBuffers(context: Renderer, meshData: InputMesh): Pair<GPUBuffer, GPUBuffer>
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
			VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
			Vma.VMA_MEMORY_USAGE_AUTO,
			Vma.VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT,
			VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
		)
		val dstBuffer = GPUBuffer(
			context,
			bufferSize,
			VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT or VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
			Vma.VMA_MEMORY_USAGE_AUTO,
			0,
			0,
		)

		srcBuffer.doMapped { mappedMemory ->
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
			VK10.vkCmdCopyBuffer(cmd.vkCommandBuffer, from.bufferStruct, to.bufferStruct, copyRegion)
		}
	}

	class Mesh(
		val verticesBuffer: GPUBuffer,
		val indicesBuffer: GPUBuffer,
		val numIndices: Int
	)
}