package fpw.ren

import fpw.Renderer

class SwapChainDirector (val renderer: Renderer)
{
	val commandPool
		= renderer.createCommandPool(renderer.graphicsQueue.queueFamilyIndex, false)

	val commandBuffer
		= CommandBuffer(renderer, commandPool, oneTimeSubmit = true)

	var imageAcquiredSemaphore
		= renderer.createSemaphor()

	val fence
		= renderer.createFence(signaled = true)


	fun onResize ()
	{
		imageAcquiredSemaphore.free()
		imageAcquiredSemaphore = renderer.createSemaphor()
	}

	fun free ()
	{
		commandBuffer.free(renderer, commandPool)
		commandPool.free()
		imageAcquiredSemaphore.free()
		fence.free(renderer)
	}
}