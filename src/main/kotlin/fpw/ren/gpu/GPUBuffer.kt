package fpw.ren.gpu

import fpw.Renderer
import fpw.ren.gpu.GPUtil.gpuCheck
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE
import org.lwjgl.vulkan.VK10.vkAllocateMemory
import org.lwjgl.vulkan.VK10.vkBindBufferMemory
import org.lwjgl.vulkan.VK10.vkCreateBuffer
import org.lwjgl.vulkan.VK10.vkDestroyBuffer
import org.lwjgl.vulkan.VK10.vkFreeMemory
import org.lwjgl.vulkan.VK10.vkGetBufferMemoryRequirements
import org.lwjgl.vulkan.VK10.vkMapMemory
import org.lwjgl.vulkan.VK10.vkUnmapMemory
import org.lwjgl.vulkan.VkBufferCreateInfo
import org.lwjgl.vulkan.VkMemoryAllocateInfo
import org.lwjgl.vulkan.VkMemoryRequirements


class GPUBuffer
{

	val allocationSize: Long
	val bufferStruct: Long
	val bufferData: Long
	val pb: PointerBuffer
	val requestedSize: Long

	var mappedMemory: Long
		private set

	constructor (vkCtx: Renderer, size: Long, usage: Int, reqMask: Int)
	{
		mappedMemory = NULL
		requestedSize = size
		MemoryStack.stackPush().use { stack ->
			val device: LogicalDevice = vkCtx.device
			val bufferCreateInfo = VkBufferCreateInfo.calloc(stack)
				.`sType$Default`()
				.size(size)
				.usage(usage)
				.sharingMode(VK_SHARING_MODE_EXCLUSIVE)
			val lp = stack.mallocLong(1)
			gpuCheck(vkCreateBuffer(device.vkDevice, bufferCreateInfo, null, lp), "Failed to create buffer")
			bufferStruct = lp.get(0)

			val memReqs = VkMemoryRequirements.calloc(stack)
			vkGetBufferMemoryRequirements(device.vkDevice, bufferStruct, memReqs)

			val memAlloc = VkMemoryAllocateInfo.calloc(stack)
				.`sType$Default`()
				.allocationSize(memReqs.size())
				.memoryTypeIndex(GPUtil.memoryTypeFromProperties(vkCtx, memReqs.memoryTypeBits(), reqMask))

			gpuCheck(
				vkAllocateMemory(device.vkDevice, memAlloc, null, lp),
				"Failed to allocate memory"
			)
			allocationSize = memAlloc.allocationSize()
			bufferData = lp.get(0)
			pb = MemoryUtil.memAllocPointer(1)
			gpuCheck(
				vkBindBufferMemory(device.vkDevice, bufferStruct, bufferData, 0),
				"Failed to bind buffer memory"
			)
		}
	}

	fun free(context: Renderer)
	{
		MemoryUtil.memFree(pb)
		val vkDevice = context.vkDevice
		vkDestroyBuffer(vkDevice, bufferStruct, null)
		vkFreeMemory(vkDevice, bufferData, null)
	}

	fun map(vkCtx: Renderer): Long
	{
		if (mappedMemory == NULL)
		{
			gpuCheck(
				vkMapMemory(vkCtx.vkDevice, bufferData, 0, allocationSize, 0, pb),
				"Failed to map Buffer"
			)
			mappedMemory = pb.get(0)
		}
		return mappedMemory
	}

	fun unMap(vkCtx: Renderer)
	{
		if (mappedMemory != NULL)
		{
			vkUnmapMemory(vkCtx.vkDevice, bufferData)
			mappedMemory = NULL
		}
	}

	inline fun doMapped (context: Renderer, cb: (Long)->Unit)
	{
		cb.invoke(map(context))
		unMap(context)
	}
}

