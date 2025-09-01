package fpw




fun main (vararg args: String)
{
	try
	{
		val engine = Engine(
			window=Window.create("MACHINE WITNESS", 1280, 720),
		)
		engine.run()
	}
	catch (t: Throwable)
	{
		FUtil.logError(t) { "KERSPLOSION??? :[" }
	}
}

