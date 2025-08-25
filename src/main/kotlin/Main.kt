package com.catsofwar

import org.tinylog.kotlin.Logger


class Main: GameLogic
{
	override fun init(context: EngineContext)
	{
	}

	override fun input(context: EngineContext, diffTimeMillis: Long)
	{
	}

	override fun update(context: EngineContext, diffTimeMillis: Long)
	{
	}

	override fun close()
	{
	}


	companion object
	{
		@JvmStatic
		fun main (vararg args: String)
		{
			Logger.info("MACHINE WITNESS BEGIN")
			Logger.info("yeah :)")
			val engine = Engine("MACHINE WITNESS", Main())
			Logger.info("MACHINE WITNESS REIFICATION")
			engine.run()
		}
	}
}