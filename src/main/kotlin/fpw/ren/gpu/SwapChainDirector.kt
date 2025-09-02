package fpw.ren.gpu

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
		imageAcquiredSemaphore.free(renderer)
		imageAcquiredSemaphore = renderer.createSemaphor()
	}

	fun free ()
	{
		commandBuffer.free(renderer, commandPool)
		commandPool.free(renderer)
		imageAcquiredSemaphore.free(renderer)
		fence.free(renderer)
	}
}