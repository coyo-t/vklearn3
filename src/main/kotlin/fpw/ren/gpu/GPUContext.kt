package fpw.ren.gpu

import fpw.EngineConfig
import fpw.Window

class GPUContext(window: Window)
{

	val instance = GPUInstance(
		validate = EngineConfig.useVulkanValidationLayers,
	)
	val physDevice = GPUPhysical.Companion.createPhysicalDevice(
		instance,
		prefDeviceName = EngineConfig.preferredPhysicalDevice,
	)
	val device = GPUDevice(physDevice)
	val surface = GPUSurface(instance, physDevice, window)
	val swapChain = GPUSwapChain(
		window,
		device,
		surface,
		requestedImages = EngineConfig.preferredImageBufferingCount,
		vsync = EngineConfig.useVerticalSync,
	)

	val vkDevice get() = device.vkDevice

	val pipelineCache = GPUPipeLineCache(device)

//	val instance = run {
//		val cfg = EngineConfig
//		VKInstance(cfg.vkUseValidationLayers)
//	}

	fun close()
	{
		pipelineCache.close(this)
		swapChain.cleanup(device)
		surface.cleanup(instance)
		device.close()
		physDevice.close()
		instance.close()
	}
}