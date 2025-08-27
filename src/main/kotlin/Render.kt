package com.catsofwar

import com.catsofwar.vk.*
import java.util.*
import java.util.function.Consumer


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

		renderCompleteSemphs.forEach(vkContext::closeFor)
		imageAqSemphs.forEach(vkContext::closeFor)
		fences.forEach(vkContext::closeFor)

		for ((cb, cp) in cmdBuffers.zip(cmdPools))
		{
			cb.cleanup(vkContext, cp)
			cp.cleanup(vkContext)
		}

		vkContext.close()
	}

	fun render (engineContext: EngineContext)
	{

	}

}