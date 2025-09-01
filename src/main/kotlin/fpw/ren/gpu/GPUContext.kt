package fpw.ren.gpu

import fpw.EngineConfig
import fpw.Window


class GPUContext(window: Window)
{

	val instance = GPUInstance(
		validate = EngineConfig.useVulkanValidationLayers,
	)
	val hardware = HardwareDevice.createPhysicalDevice(
		instance,
		prefDeviceName = EngineConfig.preferredPhysicalDevice,
	)
	val device = LogicalDevice(hardware)
	var displaySurface = DisplaySurface(instance, hardware, window)
		private set
	var swapChain = SwapChain(
		window,
		device,
		displaySurface,
		requestedImages = EngineConfig.preferredImageBufferingCount,
		vsync = EngineConfig.useVerticalSync,
	)
		private set

	val vkDevice get() = device.vkDevice

	val pipelineCache = device.createPipelineCache()

	fun resize (window: Window)
	{
		swapChain.cleanup(device)
		displaySurface.free(instance)
		displaySurface = DisplaySurface(instance, hardware, window)
		swapChain = SwapChain(
			window,
			device,
			displaySurface,
			EngineConfig.preferredImageBufferingCount,
			EngineConfig.useVerticalSync,
		)
	}

	fun free()
	{
		pipelineCache.free(this)
		swapChain.cleanup(device)
		displaySurface.free(instance)
		device.close()
		hardware.free()
		instance.close()
	}
}