package fpw

import fpw.ren.ModelsCache
import fpw.ren.SceneRender
import fpw.ren.gpu.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK13.VK_PIPELINE_STAGE_2_BOTTOM_OF_PIPE_BIT
import org.lwjgl.vulkan.VK13.VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT
import org.lwjgl.vulkan.VkCommandBufferSubmitInfo
import org.lwjgl.vulkan.VkExtent2D
import org.lwjgl.vulkan.VkSemaphoreSubmitInfo
import java.util.*
import java.util.concurrent.Semaphore
import java.util.function.Consumer


class Render (engineContext: EngineContext)
{
	private val vkContext = GPUContext(engineContext.window)
	private var currentFrame = 0

	private val graphQueue = GPUCommandQueue.GraphicsQueue(vkContext, 0)
	private val presentQueue = GPUCommandQueue.PresentQueue(vkContext, 0)

	private val cmdPools = List(EngineConfig.maxInFlightFrames) {
		GPUCommandPool(vkContext, graphQueue.queueFamilyIndex, false)
	}
	private val cmdBuffers = cmdPools.map {
		GPUCommandBuffer(vkContext, it, primary = true, oneTimeSubmit = true)
	}
	private var imageAqSemphs = List(EngineConfig.maxInFlightFrames) {
		GPUSemaphore(vkContext)
	}
	private var fences = List(EngineConfig.maxInFlightFrames) {
		GPUFence(vkContext, signaled = true)
	}
	private var renderCompleteSemphs = List(vkContext.swapChain.numImages) {
		GPUSemaphore(vkContext)
	}
	private val modelsCache = ModelsCache()

	private val scnRender = SceneRender(vkContext)

	private var doResize = false

	fun init (initData: InitData)
	{
		val models = initData.models
		modelsCache.loadModels(vkContext, models, cmdPools[0], graphQueue)
		Main.logDebug("Loaded ${models.size}")
	}

	fun close()
	{
		vkContext.device.waitIdle()

		scnRender.close(vkContext)

		modelsCache.close(vkContext)
		renderCompleteSemphs.forEach { it.close(vkContext) }
		imageAqSemphs.forEach { it.close(vkContext) }
		fences.forEach { it.close(vkContext) }
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

	private fun recordingStart(cmdPool: GPUCommandPool, cmdBuffer: GPUCommandBuffer)
	{
		cmdPool.reset(vkContext)
		cmdBuffer.beginRecording()
	}

	private fun recordingStop(cmdBuffer: GPUCommandBuffer)
	{
		cmdBuffer.endRecording()
	}

	private fun submit(cmdBuff: GPUCommandBuffer, currentFrame: Int, imageIndex: Int)
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

		if (doResize)
		{
			resize(engineContext)
			return
		}
		val imageIndex = swapChain.acquireNextImage(vkContext.device, imageAqSemphs[currentFrame])
		if (imageIndex < 0)
		{
			resize(engineContext)
			return
		}
		scnRender.render(engineContext, vkContext, cmdBuffer, modelsCache, imageIndex)

		recordingStop(cmdBuffer)

		submit(cmdBuffer, currentFrame, imageIndex)

		doResize = swapChain.presentImage(presentQueue, renderCompleteSemphs[imageIndex], imageIndex)

		currentFrame = (currentFrame + 1) % EngineConfig.maxInFlightFrames
	}

	private fun resize (engCtx: EngineContext)
	{
		val window = engCtx.window
		if (window.wide == 0 || window.tall == 0)
		{
			return
		}
		doResize = false
		vkContext.device.waitIdle()

		vkContext.resize(window)

		renderCompleteSemphs.forEach { it.close(vkContext) }
		imageAqSemphs.forEach { it.close(vkContext) }

		imageAqSemphs = List(EngineConfig.maxInFlightFrames) {
			GPUSemaphore(vkContext)
		}

		renderCompleteSemphs = List(vkContext.swapChain.numImages) {
			GPUSemaphore(vkContext)
		}

		val extent = vkContext.swapChain.swapChainExtent
		engCtx.scene.projection.resize(extent.width(), extent.height())
		scnRender.resize(vkContext)
	}

}