package fpw

import fpw.ren.gpu.GPUMeshData
import fpw.ren.gpu.GPUModelData


class Main: GameLogic
{
	override fun init(context: EngineContext): InitData
	{
		val modelId = "TriangleModel"
		val meshData = GPUMeshData(
			"triangle-mesh",
			floatArrayOf(
				-0.5f, -0.5f, 0f,
				+0.0f, +0.5f, 0f,
				+0.5f, -0.5f, 0f,
			),
			intArrayOf(0, 1, 2)
		)
		val modelData = GPUModelData(modelId, listOf(meshData))
		return InitData(listOf(modelData))

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
			try
			{
				logInfo("MACHINE WITNESS BEGIN")
				Engine("MACHINE WITNESS", Main()).use {
					logInfo("MACHINE WITNESS REIFICATION")
					it.run()
				}
			}
			catch (t: Throwable)
			{
				logError(t) { "KERSPLOSION??? :[" }
			}
		}

		fun logInfo (f:String, vararg args:Any?)
		{
			println("${ANSI_GREEN}ovo${ANSI_RESET} $f".format(*args))
		}

		fun logWarn (f:String, vararg args:Any?)
		{
			println("${ANSI_YELLOW}v_v $f${ANSI_RESET}".format(*args))
		}

		fun logError (f:String, vararg args:Any?)
		{
			println("${ANSI_RED}x_x $f${ANSI_RESET}".format(*args))
		}

		fun logDebug (f:String, vararg args:Any?)
		{
			println("${ANSI_BLUE}._.${ANSI_RESET} $f".format(*args))
		}

		fun logTrace (f:String, vararg n: Any?)
		{
//			val uhh = RuntimeProvider.getCallerStackTraceElement(1)
//			println("${uhh.fileName} @ ${uhh.lineNumber} ==> $f".format(*n))
			println("${ANSI_PURPLE}-.-${ANSI_RESET} $f".format(*n))
		}

		fun logError (t: Throwable, k:()->String)
		{
			println(
				"${ANSI_RED}x_x ${k.invoke()}\n" +
				t.stackTraceToString() +
				ANSI_RESET
			)
		}
	}
}
