package com.catsofwar


object EngineConfig
{
	var updatesPerSecond = 30
		private set

	var useVulkanValidationLayers = false
		private set

	var preferredPhysicalDevice: String? = null
		private set

	var useVerticalSync = true
		private set

	var preferredImageBufferingCount = 3
		private set

	var maxInFlightFrames = 2
		private set
}