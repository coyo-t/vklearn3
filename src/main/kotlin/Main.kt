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
			Main.logInfo("MACHINE WITNESS BEGIN")
			val engine = Engine("MACHINE WITNESS", Main())
			Main.logInfo("MACHINE WITNESS REIFICATION")
			engine.run()
		}

		fun logInfo (f:String, vararg args:Any?)
		{
//			println(f.format(*args))
			Logger.info(f, *args)
		}

		fun logWarn (f:String, vararg args:Any?)
		{
//			println(f.format(*args))
			Logger.warn(f, *args)
		}

		fun logError (f:String, vararg args:Any?)
		{
//			println(f.format(*args))
			Logger.error(f, *args)
		}

		fun logDebug (f:String, vararg args:Any?)
		{
//			println(f.format(*args))
			Logger.debug(f, *args)
		}

		fun logTrace (n: Any?)
		{
			Logger.trace(n)
		}

		fun logError (t: Throwable, k:()->String)
		{
			Logger.error(t, k)
		}
	}
}