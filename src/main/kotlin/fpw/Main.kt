package fpw




fun main (vararg args: String)
{
	var engine: Engine? = null
	try
	{
		engine = Engine(
			window=Window.create("MACHINE WITNESS", 1280, 720),
		)
		engine.init()
		engine.run()
	}
	catch (t: Throwable)
	{
		FUtil.logError(t) { "KERSPLOSION??? :[" }
	}
	finally
	{
		engine?.close()
	}
}

