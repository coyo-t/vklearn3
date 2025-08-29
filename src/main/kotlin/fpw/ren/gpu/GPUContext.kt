package fpw.ren.gpu

import fpw.EngineConfig
import fpw.Window
import sun.java2d.Surface


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
	var surface = GPUSurface(instance, physDevice, window)
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
		surface = GPUSurface(instance, physDevice, window)
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