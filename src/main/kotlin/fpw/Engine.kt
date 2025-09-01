package fpw

import org.lwjgl.glfw.GLFW.glfwPollEvents

class Engine (
	val window: Window,
	private val gameLogic: GameLogic,
)
{
	val entities = mutableListOf<Entity>()
	val projection = Projection(
		fov = 90f,
		zNear = 0.001f,
		zFar = 100f,
		width = window.wide,
		height = window.tall,
	)

	private val render = GameRenderer(this)

	init
	{
		render.init(gameLogic.init(this))
	}

	fun close ()
	{
		gameLogic.close()
		render.close()
		window.close()
	}

	fun run ()
	{
		try
		{
			var initialTime = System.currentTimeMillis()
			val timeU = 1000.0f / EngineConfig.updatesPerSecond
			var deltaUpdate = 0.0
			var updateTime = initialTime
			while (!window.shouldClose)
			{
				val now = System.currentTimeMillis()
				deltaUpdate += ((now - initialTime) / timeU).toDouble()

				glfwPollEvents()
				window.pollEvents()
				gameLogic.input(this, now - initialTime)
				window.resetInput()

				var ticks = minOf(deltaUpdate.toInt(), 10)

				while (ticks >= 1)
				{
					val diffTimeMillis = now - updateTime
					gameLogic.update(this, diffTimeMillis)
					updateTime = now
					ticks--
				}

				render.render(this)

				initialTime = now
			}
		}
		catch (sg: StopGame)
		{
			Main.logInfo("hard-stopping game")
			when (val reason = sg.reason)
			{
				null -> Main.logInfo("no reason given")
				else -> Main.logInfo("reason: \"$reason\"")
			}
		}
		catch (e: Exception)
		{
			Main.logError(e) { "EXCEPTION FUCK" }
		}
		catch (t: Throwable)
		{
			Main.logError(t) { "SOMETHING THREW HARDER THAN ELI FUCK" }
		}
	}

}