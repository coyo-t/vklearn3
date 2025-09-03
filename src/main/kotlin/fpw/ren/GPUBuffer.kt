package fpw.ren

import fpw.Renderer
import fpw.ren.GPUtil.gpuCheck
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.util.vma.Vma.vmaCreateBuffer
import org.lwjgl.util.vma.Vma.vmaDestroyBuffer
import org.lwjgl.util.vma.Vma.vmaFlushAllocation
import org.lwjgl.util.vma.Vma.vmaMapMemory
import org.lwjgl.util.vma.Vma.vmaUnmapMemory
import org.lwjgl.util.vma.VmaAllocationCreateInfo
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkBufferCreateInfo


class GPUBuffer (
	val vkCtx: Renderer,
	size: Long,
	bufferUsage: Int,
	vmaUsage: Int,
	vmaFlags: Int,
	reqFlags:Int,
)
{
	val allocation: Long
//	val allocationSize: Long
//	val bufferStruct: Long
	val bufferStruct: Long
	val pb: PointerBuffer
	val requestedSize: Long

	var mappedMemory: Long
		private set

	init
	{
		requestedSize = size
		mappedMemory = NULL
		MemoryStack.stackPush().use { stack ->
			val bufferCreateInfo = VkBufferCreateInfo.calloc(stack)
				.`sType$Default`()
				.size(size)
				.usage(bufferUsage)
				.sharingMode(VK_SHARING_MODE_EXCLUSIVE)
			val allocInfo = VmaAllocationCreateInfo.calloc(stack)
				.usage(vmaUsage)
				.flags(vmaFlags)
				.requiredFlags(reqFlags)

			val pAllocation = stack.callocPointer(1)
			val lp = stack.mallocLong(1)
			gpuCheck(
				vmaCreateBuffer(
					vkCtx.memAlloc.vmaAlloc, bufferCreateInfo, allocInfo, lp,
					pAllocation, null
				), "Failed to create buffer"
			)
			bufferStruct = lp.get(0)
			allocation = pAllocation.get(0)
			pb = MemoryUtil.memAllocPointer(1)
		}
	}

	fun free()
	{
		MemoryUtil.memFree(pb)
//		val vkDevice = vkCtx.vkDevice
		unMap()
		vmaDestroyBuffer(vkCtx.memAlloc.vmaAlloc, bufferStruct, allocation)
//		vkDestroyBuffer(vkDevice, bufferStruct, null)
//		vkFreeMemory(vkDevice, bufferData, null)
	}

	fun flush ()
	{
		vmaFlushAllocation(vkCtx.memAlloc.vmaAlloc, allocation, 0, VK_WHOLE_SIZE)
	}

	fun map(): Long
	{
		if (mappedMemory == NULL)
		{
			gpuCheck(
				vmaMapMemory(vkCtx.memAlloc.vmaAlloc, allocation, pb),
				"Failed to map buffer"
			)
			mappedMemory = pb.get(0)
		}
		return mappedMemory
	}

	fun unMap ()
	{
		if (mappedMemory != NULL)
		{
			vmaUnmapMemory(vkCtx.memAlloc.vmaAlloc, allocation)
			mappedMemory = NULL
		}
	}

	inline fun doMapped (cb: (Long)->Unit)
	{
		cb.invoke(map())
		unMap()
	}
}

