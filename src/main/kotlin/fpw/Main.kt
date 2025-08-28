package fpw


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
			Engine("MACHINE WITNESS", Main()).use {
				logInfo("MACHINE WITNESS REIFICATION")
				it.run()
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
				"${t.stackTraceToString()}"+
				"${ANSI_RESET}"
			)
		}
	}
}

private const val ANSI_RESET = "\u001B[0m"

private const val ANSI_BLACK = "\u001B[30m"
private const val ANSI_RED = "\u001B[31m"
private const val ANSI_GREEN = "\u001B[32m"
private const val ANSI_YELLOW = "\u001B[33m"
private const val ANSI_BLUE = "\u001B[34m"
private const val ANSI_PURPLE = "\u001B[35m"
private const val ANSI_CYAN = "\u001B[36m"
private const val ANSI_WHITE = "\u001B[37m"

private const val ANSI_BLACK_BACKGROUND = "\u001B[40m"
private const val ANSI_RED_BACKGROUND = "\u001B[41m"
private const val ANSI_GREEN_BACKGROUND = "\u001B[42m"
private const val ANSI_YELLOW_BACKGROUND = "\u001B[43m"
private const val ANSI_BLUE_BACKGROUND = "\u001B[44m"
private const val ANSI_PURPLE_BACKGROUND = "\u001B[45m"
private const val ANSI_CYAN_BACKGROUND = "\u001B[46m"
private const val ANSI_WHITE_BACKGROUND = "\u001B[47m"
