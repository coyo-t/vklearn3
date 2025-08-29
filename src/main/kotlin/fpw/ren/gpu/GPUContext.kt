package fpw.ren.gpu

import fpw.EngineConfig
import fpw.Window


class GPUContext(window: Window)
{

	val instance = GPUInstance(
		validate = EngineConfig.useVulkanValidationLayers,
	)
	val physDevice = GPUPhysical.createPhysicalDevice(
		instance,
		prefDeviceName = EngineConfig.preferredPhysicalDevice,
	)
	val device = GPUDevice(physDevice)
	var surface = Surface(instance, physDevice, window)
		private set
	var swapChain = GPUSwapChain(
		window,
		device,
		surface,
		requestedImages = EngineConfig.preferredImageBufferingCount,
		vsync = EngineConfig.useVerticalSync,
	)
	private set

	val vkDevice get() = device.vkDevice

	val pipelineCache = GPUPipeLineCache(device)

//	val instance = run {
//		val cfg = EngineConfig
//		VKInstance(cfg.vkUseValidationLayers)
//	}

	fun resize (window: Window)
	{
		swapChain.cleanup(device)
		surface.cleanup(instance)
		val engCfg = EngineConfig
		surface = Surface(instance, physDevice, window)
		swapChain = GPUSwapChain(window, device, surface, engCfg.preferredImageBufferingCount, engCfg.useVerticalSync)
	}

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