package vk

import com.catsofwar.vk.GPUClosable
import com.catsofwar.vk.GPUContext
import com.catsofwar.vk.GPUDevice
import com.catsofwar.vk.GPUtil
import com.catsofwar.vk.GPUtil.vkCheck
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
import org.lwjgl.vulkan.VkDevice
import org.lwjgl.vulkan.VkMemoryAllocateInfo
import org.lwjgl.vulkan.VkMemoryRequirements


class GPUBuffer(vkCtx: GPUContext, size: Long, usage: Int, reqMask: Int):
	GPUClosable
{

	val allocationSize: Long
	val buffer: Long
	val memory: Long
	val pb: PointerBuffer
	val requestedSize = size

	var mappedMemory: Long
		private set

	init
	{
		mappedMemory = NULL
		MemoryStack.stackPush().use { stack ->
			val device: GPUDevice = vkCtx.device
			val bufferCreateInfo = VkBufferCreateInfo.calloc(stack)
				.`sType$Default`()
				.size(size)
				.usage(usage)
				.sharingMode(VK_SHARING_MODE_EXCLUSIVE)
			val lp = stack.mallocLong(1)
			vkCheck(vkCreateBuffer(device.vkDevice, bufferCreateInfo, null, lp), "Failed to create buffer")
			buffer = lp.get(0)

			val memReqs = VkMemoryRequirements.calloc(stack)
			vkGetBufferMemoryRequirements(device.vkDevice, buffer, memReqs)

			val memAlloc = VkMemoryAllocateInfo.calloc(stack)
				.`sType$Default`()
				.allocationSize(memReqs.size())
				.memoryTypeIndex(GPUtil.memoryTypeFromProperties(vkCtx, memReqs.memoryTypeBits(), reqMask))

			vkCheck(vkAllocateMemory(device.vkDevice, memAlloc, null, lp), "Failed to allocate memory")
			allocationSize = memAlloc.allocationSize()
			memory = lp.get(0)
			pb = MemoryUtil.memAllocPointer(1)
			vkCheck(vkBindBufferMemory(device.vkDevice, buffer, memory, 0), "Failed to bind buffer memory")
		}
	}

	override fun close(context: GPUContext)
	{
		MemoryUtil.memFree(pb)
		val vkDevice = context.vkDevice
		vkDestroyBuffer(vkDevice, buffer, null)
		vkFreeMemory(vkDevice, memory, null)
	}

	fun map(vkCtx: GPUContext): Long
	{
		if (mappedMemory == NULL)
		{
			vkCheck(vkMapMemory(vkCtx.vkDevice, memory, 0, allocationSize, 0, pb), "Failed to map Buffer")
			mappedMemory = pb.get(0)
		}
		return mappedMemory
	}

	fun unMap(vkCtx: GPUContext)
	{
		if (mappedMemory != NULL)
		{
			vkUnmapMemory(vkCtx.vkDevice, memory)
			mappedMemory = NULL
		}
	}

	inline fun doMapped (context: GPUContext, cb: (Long)->Unit)
	{
		cb.invoke(map(context))
		unMap(context)
	}
}

