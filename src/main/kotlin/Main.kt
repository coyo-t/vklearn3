package com.catsofwar


class Main: GameLogic
{
	override fun init(context: EngineContext)
	{
		TODO("Not yet implemented")
	}

	override fun input(context: EngineContext, diffTimeMillis: Long)
	{
		TODO("Not yet implemented")
	}

	override fun update(context: EngineContext, diffTimeMillis: Long)
	{
		TODO("Not yet implemented")
	}

	override fun close()
	{
		TODO("Not yet implemented")
	}


	companion object
	{
		@JvmStatic
		fun main (vararg args: String)
		{
			val name = "Kotlin"
			println("Hello, $name!")

			for (i in 1..5)
			{
				println("i = $i")
			}
		}
	}
}