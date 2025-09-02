package fpw.ren.gpu

import fpw.Renderer
import fpw.ren.gpu.GPUtil.gpuCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*


class CommandBuffer
{
	val oneTimeSubmit: Boolean
	val vkCommandBuffer: VkCommandBuffer
	var inheritanceInfo: InheritanceInfo?

	constructor (
		vkCtx: Renderer,
		cmdPool: CommandPool,
		oneTimeSubmit: Boolean,
		inherit:InheritanceInfo?=null
	)
	{
		this.oneTimeSubmit = oneTimeSubmit
		val vkDevice = vkCtx.vkDevice
		inheritanceInfo = inherit

		MemoryStack.stackPush().use { stack ->
			val cmdBufAllocateInfo = VkCommandBufferAllocateInfo.calloc(stack)
				.`sType$Default`()
				.commandPool(cmdPool.vkCommandPool)
				.level(
					if (inherit == null)
						VK_COMMAND_BUFFER_LEVEL_PRIMARY
					else
						VK_COMMAND_BUFFER_LEVEL_SECONDARY
				)
				.commandBufferCount(1)
			val pb = stack.mallocPointer(1)
			gpuCheck(
				vkAllocateCommandBuffers(vkDevice, cmdBufAllocateInfo, pb),
				"allocate render command buffer"
			)
			vkCommandBuffer = VkCommandBuffer(pb[0], vkDevice)
		}
	}


	fun beginRecording ()
	{
		MemoryStack.stackPush().use { stack ->
			val cmdBufInfo = VkCommandBufferBeginInfo.calloc(stack).`sType$Default`()
			if (oneTimeSubmit)
			{
				cmdBufInfo.flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT)
			}
			val inherit = inheritanceInfo
			if (inherit != null)
			{
				val numColorFormats = inherit.colorFormats.size
				val pColorFormats = stack.callocInt(inherit.colorFormats.size)
				for (i in 0..<numColorFormats)
				{
					pColorFormats.put(0, inherit.colorFormats[i])
				}
				val renderingInfo = VkCommandBufferInheritanceRenderingInfo.calloc(stack)
					.`sType$Default`()
					.depthAttachmentFormat(inherit.depthFormat)
					.pColorAttachmentFormats(pColorFormats)
					.rasterizationSamples(inherit.rasterizationSamples)
				val vkInheritanceInfo = VkCommandBufferInheritanceInfo.calloc(stack)
					.`sType$Default`()
					.pNext(renderingInfo)
				cmdBufInfo.pInheritanceInfo(vkInheritanceInfo)
			}
			gpuCheck(
				vkBeginCommandBuffer(vkCommandBuffer, cmdBufInfo),
				"begin command buffer",
			)
		}
	}

	fun free(vkCtx: Renderer, cmdPool: CommandPool)
	{
		vkFreeCommandBuffers(
			vkCtx.device.vkDevice, cmdPool.vkCommandPool,
			vkCommandBuffer
		)
	}

	fun endRecording()
	{
		gpuCheck(
			vkEndCommandBuffer(vkCommandBuffer),
			"end command buffer"
		)
	}

	fun reset()
	{
		vkResetCommandBuffer(vkCommandBuffer, VK_COMMAND_BUFFER_RESET_RELEASE_RESOURCES_BIT)
	}

	fun submitAndWait(vkCtx: Renderer, queue: CommandQueue)
	{
		val fence = vkCtx.createFence(true)
		fence.reset(vkCtx)
		MemoryStack.stackPush().use { stack ->
			val cmds = VkCommandBufferSubmitInfo.calloc(1, stack)
				.`sType$Default`()
				.commandBuffer(vkCommandBuffer)
			queue.submit(cmds, null, null, fence)
		}
		fence.wait(vkCtx)
		fence.free(vkCtx)
	}

	class InheritanceInfo (
		val depthFormat: Int,
		val colorFormats: IntArray,
		val rasterizationSamples: Int,
	)
}