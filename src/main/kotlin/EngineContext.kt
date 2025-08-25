package com.catsofwar


data class EngineContext(
	val window: Window, val scene: Scene
): AutoCloseable
{
	override fun close ()
	{
	}
}
