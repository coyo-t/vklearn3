package fpw

import org.lwjgl.glfw.GLFW

class Engine (
	windowTitle: String,
	private val gameLogic: GameLogic,
): AutoCloseable
{
	private val engineContext: EngineContext
	private val render: Render

	init
	{
		val window = Window.Companion.create(windowTitle, 1280, 720)
		engineContext = EngineContext(window, Scene(window))
		render = Render(engineContext)
		val idat = gameLogic.init(engineContext)
		render.init(idat)
	}

	override fun close ()
	{
		gameLogic.close()
		render.close()
		engineContext.close()
		engineContext.window.close()
	}

	fun run ()
	{
		try
		{
			spinning()
		}
		catch (sg: StopGame)
		{
			Main.Companion.logInfo("hard-stopping game")
			when (val reason = sg.reason)
			{
				null -> Main.Companion.logInfo("no reason given")
				else -> Main.Companion.logInfo("reason: \"$reason\"")
			}
		}
		catch (e: Exception)
		{
			Main.Companion.logError(e) { "EXCEPTION FUCK" }
		}
		catch (t: Throwable)
		{
			Main.Companion.logError(t) { "SOMETHING THREW HARDER THAN ELI FUCK" }
		}
//		cleanup()
	}

	private fun spinning()
	{
		var initialTime = System.currentTimeMillis()
		val timeU = 1000.0f / EngineConfig.updatesPerSecond
		var deltaUpdate = 0.0

		var updateTime = initialTime
		val window = engineContext.window
		while (!window.shouldClose)
		{
			val now = System.currentTimeMillis()
			deltaUpdate += ((now - initialTime) / timeU).toDouble()

			GLFW.glfwPollEvents()
			window.pollEvents()
			gameLogic.input(engineContext, now - initialTime)
			window.resetInput()

			if (deltaUpdate >= 1)
			{
				val diffTimeMillis = now - updateTime
				gameLogic.update(engineContext, diffTimeMillis)
				updateTime = now
				deltaUpdate--
			}

			render.render(engineContext)

			initialTime = now
		}
	}
}