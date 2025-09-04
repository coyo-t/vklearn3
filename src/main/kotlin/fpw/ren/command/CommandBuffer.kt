package fpw.ren.command

import fpw.ren.Renderer
import fpw.ren.command.CommandPool
import fpw.ren.command.CommandSequence
import fpw.ren.Fence
import fpw.ren.GPUtil
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VkCommandBuffer
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo
import org.lwjgl.vulkan.VkCommandBufferBeginInfo
import org.lwjgl.vulkan.VkCommandBufferInheritanceInfo
import org.lwjgl.vulkan.VkCommandBufferInheritanceRenderingInfo
import org.lwjgl.vulkan.VkCommandBufferSubmitInfo

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
		val vkDevice = vkCtx.gpu.logicalDevice.vkDevice
		inheritanceInfo = inherit

		MemoryStack.stackPush().use { stack ->
			val cmdBufAllocateInfo = VkCommandBufferAllocateInfo.calloc(stack)
				.`sType$Default`()
				.commandPool(cmdPool.vkCommandPool)
				.level(
					if (inherit == null)
						VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY
					else
						VK10.VK_COMMAND_BUFFER_LEVEL_SECONDARY
				)
				.commandBufferCount(1)
			val pb = stack.mallocPointer(1)
			GPUtil.gpuCheck(
				VK10.vkAllocateCommandBuffers(vkDevice, cmdBufAllocateInfo, pb),
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
				cmdBufInfo.flags(VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT)
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
				renderingInfo.`sType$Default`()
				renderingInfo.depthAttachmentFormat(inherit.depthFormat)
				renderingInfo.pColorAttachmentFormats(pColorFormats)
				renderingInfo.rasterizationSamples(inherit.rasterizationSamples)
				val vkInheritanceInfo = VkCommandBufferInheritanceInfo.calloc(stack)
				vkInheritanceInfo.`sType$Default`()
				vkInheritanceInfo.pNext(renderingInfo)
				cmdBufInfo.pInheritanceInfo(vkInheritanceInfo)
			}
			GPUtil.gpuCheck(
				VK10.vkBeginCommandBuffer(vkCommandBuffer, cmdBufInfo),
				"begin command buffer",
			)
		}
	}

	fun free(vkCtx: Renderer, cmdPool: CommandPool)
	{
		VK10.vkFreeCommandBuffers(
			vkCtx.gpu.logicalDevice.vkDevice, cmdPool.vkCommandPool,
			vkCommandBuffer
		)
	}

	fun endRecording()
	{
		GPUtil.gpuCheck(
			VK10.vkEndCommandBuffer(vkCommandBuffer),
			"end command buffer"
		)
	}

	fun reset()
	{
		VK10.vkResetCommandBuffer(
			vkCommandBuffer,
			VK10.VK_COMMAND_BUFFER_RESET_RELEASE_RESOURCES_BIT,
		)
	}

	fun submitAndWait(vkCtx: Renderer, queue: CommandSequence)
	{
		val fence = Fence(vkCtx, signaled = true)
		fence.reset()
		MemoryStack.stackPush().use { stack ->
			val cmds = VkCommandBufferSubmitInfo.calloc(1, stack)
			cmds.`sType$Default`()
			cmds.commandBuffer(vkCommandBuffer)
			queue.submit(cmds, null, null, fence)
		}
		fence.waitForFences()
		fence.free()
	}

	class InheritanceInfo (
		val depthFormat: Int,
		val colorFormats: IntArray,
		val rasterizationSamples: Int,
	)
}