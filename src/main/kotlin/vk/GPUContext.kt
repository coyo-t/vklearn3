package com.catsofwar.vk

import com.catsofwar.EngineConfig
import com.catsofwar.Window

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
	val surface = GPUSurface(instance, physDevice, window)
	val swapChain = GPUSwapChain(
		window,
		device,
		surface,
		requestedImages = EngineConfig.preferredImageBufferingCount,
		vsync = EngineConfig.useVerticalSync,
	)

	val vkDevice get() = device.vkDevice

//	val instance = run {
//		val cfg = EngineConfig
//		VKInstance(cfg.vkUseValidationLayers)
//	}

	fun close()
	{
		swapChain.cleanup(device)
		surface.cleanup(instance)
		device.close()
		physDevice.close()
		instance.close()
	}
}