package com.catsofwar

import com.catsofwar.vk.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK13.VK_PIPELINE_STAGE_2_BOTTOM_OF_PIPE_BIT
import org.lwjgl.vulkan.VK13.VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT
import org.lwjgl.vulkan.VkCommandBufferSubmitInfo
import org.lwjgl.vulkan.VkSemaphoreSubmitInfo
import java.util.*


class Render (engineContext: EngineContext): AutoCloseable
{
	private val vkContext = VKContext(engineContext.window)
	private var currentFrame = 0

	private val graphQueue = CommandQueue.GraphicsQueue(vkContext, 0)
	private val presentQueue = CommandQueue.PresentQueue(vkContext, 0)

	private val cmdPools = List(EngineConfig.maxInFlightFrames) {
		CommandPool(vkContext, graphQueue.queueFamilyIndex, false)
	}
	private val cmdBuffers = cmdPools.map {
		CommandBuffer(vkContext, it, primary = true, oneTimeSubmit = true)
	}
	private val imageAqSemphs = List(EngineConfig.maxInFlightFrames) {
		Semaphore(vkContext)
	}
	private val fences = List(EngineConfig.maxInFlightFrames) {
		Fence(vkContext, signaled = true)
	}
	private val renderCompleteSemphs = List(vkContext.swapChain.numImages) {
		Semaphore(vkContext)
	}

	private val scnRender = ScnRender(vkContext)

	override fun close()
	{
		vkContext.device.waitIdle()

		scnRender.close()

		vkContext.closeAll(
			renderCompleteSemphs,
			imageAqSemphs,
			fences,
		)

		for ((cb, cp) in cmdBuffers.zip(cmdPools))
		{
			cb.cleanup(vkContext, cp)
			cp.cleanup(vkContext)
		}

		vkContext.close()
	}

	private fun waitForFence(currentFrame: Int)
	{
		fences[currentFrame].fenceWait(vkContext)
	}

	private fun recordingStart(cmdPool: CommandPool, cmdBuffer: CommandBuffer)
	{
		cmdPool.reset(vkContext)
		cmdBuffer.beginRecording()
	}

	private fun recordingStop(cmdBuffer: CommandBuffer)
	{
		cmdBuffer.endRecording()
	}

	private fun submit(cmdBuff: CommandBuffer, currentFrame: Int, imageIndex: Int)
	{
		MemoryStack.stackPush().use { stack ->
			val fence = fences[currentFrame]
			fence.reset(vkContext)
			val cmds = VkCommandBufferSubmitInfo.calloc(1, stack)
				.`sType$Default`()
				.commandBuffer(cmdBuff.vkCommandBuffer)
			val waitSemphs = VkSemaphoreSubmitInfo.calloc(1, stack)
				.`sType$Default`()
				.stageMask(VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT)
				.semaphore(imageAqSemphs[currentFrame].vkSemaphore)
			val signalSemphs = VkSemaphoreSubmitInfo.calloc(1, stack)
				.`sType$Default`()
				.stageMask(VK_PIPELINE_STAGE_2_BOTTOM_OF_PIPE_BIT)
				.semaphore(renderCompleteSemphs[imageIndex].vkSemaphore)
			graphQueue.submit(cmds, waitSemphs, signalSemphs, fence)
		}
	}

	fun render (engineContext: EngineContext)
	{
		val swapChain = vkContext.swapChain

		waitForFence(currentFrame)

		val cmdPool = cmdPools[currentFrame]
		val cmdBuffer = cmdBuffers[currentFrame]

		recordingStart(cmdPool, cmdBuffer)

		val imageIndex = swapChain.acquireNextImage(vkContext.device, imageAqSemphs[currentFrame])
		if (imageIndex < 0)
		{
			return
		}
		scnRender.render(vkContext, cmdBuffer, imageIndex)

		recordingStop(cmdBuffer)

		submit(cmdBuffer, currentFrame, imageIndex)

		swapChain.presentImage(presentQueue, renderCompleteSemphs[imageIndex], imageIndex)

		currentFrame = (currentFrame + 1) % EngineConfig.maxInFlightFrames
	}

}