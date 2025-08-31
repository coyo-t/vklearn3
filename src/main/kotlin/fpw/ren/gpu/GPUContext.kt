package fpw.ren.gpu

import fpw.EngineConfig
import fpw.Window


class GPUContext(window: Window)
{

	val instance = GPUInstance(
		validate = EngineConfig.useVulkanValidationLayers,
	)
	val hardware = GPUHardware.createPhysicalDevice(
		instance,
		prefDeviceName = EngineConfig.preferredPhysicalDevice,
	)
	val device = GPUDevice(hardware)
	var surface = Surface(instance, hardware, window)
		private set
	var swapChain = SwapChain(
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
		surface = Surface(instance, hardware, window)
		swapChain = SwapChain(window, device, surface, engCfg.preferredImageBufferingCount, engCfg.useVerticalSync)
	}

	fun close()
	{
		pipelineCache.close(this)
		swapChain.cleanup(device)
		surface.cleanup(instance)
		device.close()
		hardware.close()
		instance.close()
	}
}