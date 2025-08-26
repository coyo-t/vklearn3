package com.catsofwar

import com.catsofwar.vk.VKContext

class Render (engineContext: EngineContext): AutoCloseable
{
	private val vkContext = VKContext(engineContext.window)

	override fun close()
	{
	}

	fun render (engineContext: EngineContext)
	{

	}

}