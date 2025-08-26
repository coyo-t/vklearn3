package com.catsofwar

import com.catsofwar.vk.VKContext

class Render (engineContext: EngineContext): AutoCloseable
{
	private val vkContext = VKContext()

	override fun close()
	{
	}

	fun render (engineContext: EngineContext)
	{

	}

}