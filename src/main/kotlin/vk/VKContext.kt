package com.catsofwar.vk

import com.catsofwar.EngineConfig
import com.catsofwar.Window

class VKContext (window: Window): AutoCloseable
{

	val instance = VKInstance(
		validate = EngineConfig.useVulkanValidationLayers,
	)
	val physDevice = PhysicalDevice.createPhysicalDevice(
		instance,
		prefDeviceName = EngineConfig.preferredPhysicalDevice,
	)
	val device = Device(physDevice)
	val surface = Surface(instance, physDevice, window)
	val swapChain = SwapChain(
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

	override fun close()
	{
		surface.cleanup(instance)
		device.close()
		physDevice.close()
		instance.close()
		swapChain.cleanup(device)
	}
}