package com.catsofwar.vk

import com.catsofwar.EngineConfig

class VKContext: AutoCloseable
{

	val instance = run {
		val cfg = EngineConfig
		VKInstance(cfg.vkUseValidationLayers)
	}

	override fun close()
	{
		instance.close()
	}
}