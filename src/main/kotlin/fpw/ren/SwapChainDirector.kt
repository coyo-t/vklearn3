package fpw.ren

import fpw.Renderer

class SwapChainDirector (val renderer: Renderer)
{
	val commandPool
		= CommandPool(renderer, renderer.graphicsQueue.queueFamilyIndex, false)

	val commandBuffer
		= CommandBuffer(renderer, commandPool, oneTimeSubmit = true)

	var imageAcquiredSemaphore
		= Semaphore(renderer)

	val fence
		= GPUFence(renderer, signaled = true)


	fun onResize ()
	{
		imageAcquiredSemaphore.free()
		imageAcquiredSemaphore = Semaphore(renderer)
	}

	fun free ()
	{
		commandBuffer.free(renderer, commandPool)
		commandPool.free()
		imageAcquiredSemaphore.free()
		fence.free()
	}
}