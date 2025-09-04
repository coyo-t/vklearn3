package fpw.ren

import fpw.ren.command.CommandBuffer
import fpw.ren.command.CommandPool
import fpw.ren.descriptor.DescriptorAllocatorGrowable
import fpw.ren.descriptor.DescriptorAllocatorGrowable.PoolSizeRatio
import fpw.ren.descriptor.DescriptorType

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

	val descriptors = DescriptorAllocatorGrowable(renderer.gpu)

	init
	{
		val sizes = listOf(
			PoolSizeRatio(DescriptorType.StorageImage, 3),
			PoolSizeRatio(DescriptorType.StorageBuffer, 3),
			PoolSizeRatio(DescriptorType.UniformBuffer, 3),
			PoolSizeRatio(DescriptorType.CombinedImageSampler, 4),
		)
		descriptors.init(1000, sizes)
	}

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
		descriptors.destroyPools()
	}
}