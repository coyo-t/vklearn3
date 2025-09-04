package fpw.ren

import fpw.ren.Renderer
import fpw.ren.command.CommandBuffer
import fpw.ren.command.CommandPool

class SwapChainDirector (val renderer: Renderer)
{
	val commandPool
		= CommandPool(renderer, renderer.graphicsQueue.queueFamilyIndex, false)

	val commandBuffer
		= CommandBuffer(renderer, commandPool, oneTimeSubmit = true)

	var imageAcquiredSemaphore
		= Semaphore(renderer)

	val fence
		= Fence(renderer, signaled = true)


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