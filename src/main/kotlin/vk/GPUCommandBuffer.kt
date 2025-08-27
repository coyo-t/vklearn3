package com.catsofwar.vk

import com.catsofwar.Main
import com.catsofwar.vk.GPUtil.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*


class GPUCommandBuffer (vkCtx: GPUContext, cmdPool: GPUCommandPool, primary: Boolean, oneTimeSubmit: Boolean)
{

	val oneTimeSubmit: Boolean
	val primary: Boolean
	val vkCommandBuffer: VkCommandBuffer

	init
	{
		Main.logTrace("Creating command buffer")
		this.primary = primary
		this.oneTimeSubmit = oneTimeSubmit
		val vkDevice = vkCtx.vkDevice

		MemoryStack.stackPush().use { stack ->
			val cmdBufAllocateInfo = VkCommandBufferAllocateInfo.calloc(stack)
				.`sType$Default`()
				.commandPool(cmdPool.vkCommandPool)
				.level(if (primary) VK_COMMAND_BUFFER_LEVEL_PRIMARY else VK_COMMAND_BUFFER_LEVEL_SECONDARY)
				.commandBufferCount(1)
			val pb = stack.mallocPointer(1)
			vkCheck(
				vkAllocateCommandBuffers(vkDevice, cmdBufAllocateInfo, pb),
				"Failed to allocate render command buffer"
			)
			vkCommandBuffer = VkCommandBuffer(pb.get(0), vkDevice)
		}
	}

	inline fun record (cb: GPUCommandBuffer.()->Unit)
	{
		beginRecording()
		cb.invoke(this)
		endRecording()
	}

	fun beginRecording (inheritanceInfo:InheritanceInfo?=null)
	{
		MemoryStack.stackPush().use { stack ->
			val cmdBufInfo = VkCommandBufferBeginInfo.calloc(stack).`sType$Default`()
			if (oneTimeSubmit)
			{
				cmdBufInfo.flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT)
			}
			if (!primary)
			{
				if (inheritanceInfo == null)
				{
					throw RuntimeException("Secondary buffers must declare inheritance info")
				}
				val numColorFormats = inheritanceInfo.colorFormats.size
				val pColorFormats = stack.callocInt(inheritanceInfo.colorFormats.size)
				for (i in 0..<numColorFormats)
				{
					pColorFormats.put(0, inheritanceInfo.colorFormats[i])
				}
				val renderingInfo = VkCommandBufferInheritanceRenderingInfo.calloc(stack)
					.`sType$Default`()
					.depthAttachmentFormat(inheritanceInfo.depthFormat)
					.pColorAttachmentFormats(pColorFormats)
					.rasterizationSamples(inheritanceInfo.rasterizationSamples)
				val vkInheritanceInfo = VkCommandBufferInheritanceInfo.calloc(stack)
					.`sType$Default`()
					.pNext(renderingInfo)
				cmdBufInfo.pInheritanceInfo(vkInheritanceInfo)
			}
			vkCheck(vkBeginCommandBuffer(vkCommandBuffer, cmdBufInfo), "Failed to begin command buffer")
		}
	}

	fun cleanup(vkCtx: GPUContext, cmdPool: GPUCommandPool)
	{
		Main.logTrace("Destroying command buffer")
		vkFreeCommandBuffers(
			vkCtx.device.vkDevice, cmdPool.vkCommandPool,
			vkCommandBuffer
		)
	}

	fun endRecording()
	{
		vkCheck(vkEndCommandBuffer(vkCommandBuffer), "Failed to end command buffer")
	}

	fun reset()
	{
		vkResetCommandBuffer(vkCommandBuffer, VK_COMMAND_BUFFER_RESET_RELEASE_RESOURCES_BIT)
	}

	fun submitAndWait(vkCtx: GPUContext, queue: GPUCommandQueue)
	{
		val fence = GPUFence(vkCtx, true)
		fence.reset(vkCtx)
		MemoryStack.stackPush().use { stack ->
			val cmds = VkCommandBufferSubmitInfo.calloc(1, stack)
				.`sType$Default`()
				.commandBuffer(vkCommandBuffer)
			queue.submit(cmds, null, null, fence)
		}
		fence.fenceWait(vkCtx)
		fence.close(vkCtx)
	}

	class InheritanceInfo (
		val depthFormat: Int,
		val colorFormats: IntArray,
		val rasterizationSamples: Int,
	)
}