package fpw.ren.gpu

import fpw.ren.gpu.GPUtil.gpuCheck
import fpw.ren.gpu.queuez.CommandQueue
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*


class CommandBuffer
{
	val oneTimeSubmit: Boolean
	val primary: Boolean
	val vkCommandBuffer: VkCommandBuffer
	var isRecording = false
		private set

	constructor (
		vkCtx: GPUContext,
		cmdPool: CommandPool,
		primary: Boolean,
		oneTimeSubmit: Boolean,
	)
	{
//		Main.logDebug("Creating command buffer")
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
			gpuCheck(
				vkAllocateCommandBuffers(vkDevice, cmdBufAllocateInfo, pb),
				"allocate render command buffer"
			)
			vkCommandBuffer = VkCommandBuffer(pb.get(0), vkDevice)
		}
	}



	inline fun record (cb: CommandBuffer.()->Unit)
	{
		beginRecording()
		cb.invoke(this)
		endRecording()
	}

	inline fun recordSubmitAndWait (ctc: GPUContext, queue: CommandQueue, cb: CommandBuffer.()->Unit)
	{
		beginRecording()
		cb.invoke(this)
		endRecording()
		submitAndWait(ctc, queue)
	}

	fun beginRecording (inheritanceInfo:InheritanceInfo?=null)
	{
//		check(!isRecording) { "already recording!" }
//		isRecording = true
		MemoryStack.stackPush().use { stack ->
			val cmdBufInfo = VkCommandBufferBeginInfo.calloc(stack).`sType$Default`()
			if (oneTimeSubmit)
			{
				cmdBufInfo.flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT)
			}
			if (!primary)
			{
				requireNotNull(inheritanceInfo) {
					"Secondary buffers must declare inheritance info"
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
			gpuCheck(
				vkBeginCommandBuffer(vkCommandBuffer, cmdBufInfo),
				"begin command buffer",
			)
		}
	}

	fun cleanup(vkCtx: GPUContext, cmdPool: CommandPool)
	{
//		Main.logTrace("Destroying command buffer")
		vkFreeCommandBuffers(
			vkCtx.device.vkDevice, cmdPool.vkCommandPool,
			vkCommandBuffer
		)
	}

	fun endRecording()
	{
//		check(isRecording) { "not recording!" }
//		isRecording = false
		gpuCheck(
			vkEndCommandBuffer(vkCommandBuffer),
			"end command buffer"
		)
	}

	fun reset()
	{
		vkResetCommandBuffer(vkCommandBuffer, VK_COMMAND_BUFFER_RESET_RELEASE_RESOURCES_BIT)
	}

	fun submitAndWait(vkCtx: GPUContext, queue: CommandQueue)
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
		fence.close(vkCtx)
	}

	class InheritanceInfo (
		val depthFormat: Int,
		val colorFormats: IntArray,
		val rasterizationSamples: Int,
	)
}