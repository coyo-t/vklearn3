package vk

import com.catsofwar.vk.GPUCommandBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.vkCmdCopyBuffer
import org.lwjgl.vulkan.VkBufferCopy


class GPUTransferBuffer(val from: GPUBuffer, val to: GPUBuffer)
{

	fun recordTransferCommand (cmd: GPUCommandBuffer)
	{
		MemoryStack.stackPush().use { stack ->
			val copyRegion = VkBufferCopy
				.calloc(1, stack)
				.srcOffset(0)
				.dstOffset(0)
				.size(from.requestedSize)
			vkCmdCopyBuffer(cmd.vkCommandBuffer, from.buffer, to.buffer, copyRegion)
		}
	}
}