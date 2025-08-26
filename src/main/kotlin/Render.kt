package com.catsofwar

import com.catsofwar.vk.CommandBuffer
import com.catsofwar.vk.CommandPool
import com.catsofwar.vk.CommandQueue
import com.catsofwar.vk.Fence
import com.catsofwar.vk.Semaphore
import com.catsofwar.vk.VKContext

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



	override fun close()
	{
	}

	fun render (engineContext: EngineContext)
	{

	}

}