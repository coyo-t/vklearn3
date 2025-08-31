package fpw.ren.gpu

import fpw.ren.gpu.GPUtil.vkCheck
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

	constructor (vkCtx: GPUContext, size: Long, usage: Int, reqMask: Int)
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
			vkCheck(vkCreateBuffer(device.vkDevice, bufferCreateInfo, null, lp), "Failed to create buffer")
			bufferStruct = lp.get(0)

			val memReqs = VkMemoryRequirements.calloc(stack)
			vkGetBufferMemoryRequirements(device.vkDevice, bufferStruct, memReqs)

			val memAlloc = VkMemoryAllocateInfo.calloc(stack)
				.`sType$Default`()
				.allocationSize(memReqs.size())
				.memoryTypeIndex(GPUtil.memoryTypeFromProperties(vkCtx, memReqs.memoryTypeBits(), reqMask))

			vkCheck(vkAllocateMemory(device.vkDevice, memAlloc, null, lp), "Failed to allocate memory")
			allocationSize = memAlloc.allocationSize()
			bufferData = lp.get(0)
			pb = MemoryUtil.memAllocPointer(1)
			vkCheck(vkBindBufferMemory(device.vkDevice, bufferStruct, bufferData, 0), "Failed to bind buffer memory")
		}
	}

	fun free(context: GPUContext)
	{
		MemoryUtil.memFree(pb)
		val vkDevice = context.vkDevice
		vkDestroyBuffer(vkDevice, bufferStruct, null)
		vkFreeMemory(vkDevice, bufferData, null)
	}

	fun map(vkCtx: GPUContext): Long
	{
		if (mappedMemory == NULL)
		{
			vkCheck(vkMapMemory(vkCtx.vkDevice, bufferData, 0, allocationSize, 0, pb), "Failed to map Buffer")
			mappedMemory = pb.get(0)
		}
		return mappedMemory
	}

	fun unMap(vkCtx: GPUContext)
	{
		if (mappedMemory != NULL)
		{
			vkUnmapMemory(vkCtx.vkDevice, bufferData)
			mappedMemory = NULL
		}
	}

	inline fun doMapped (context: GPUContext, cb: (Long)->Unit)
	{
		cb.invoke(map(context))
		unMap(context)
	}
}

