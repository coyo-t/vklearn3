package com.catsofwar

import org.lwjgl.glfw.GLFW
import org.tinylog.kotlin.Logger

class Engine (
	windowTitle: String,
	private val gameLogic: GameLogic,
)
{
	private val context: EngineContext
	private val render: Render

	init
	{
		val window = Window.create(windowTitle, 1280, 720)
		context = EngineContext(window, Scene(window))
		render = Render(context)
		gameLogic.init(context)
	}

	private fun cleanup()
	{
		gameLogic.close()
		render.close()
		context.close()
		context.window.cleanup()
	}

	fun run()
	{
		var initialTime = System.currentTimeMillis()
		val timeU = 1000.0f / EngineConfig.updatesPerSecond
		var deltaUpdate = 0.0

		var updateTime = initialTime
		val window = context.window
		try
		{
			while (!window.shouldClose)
			{
				val now = System.currentTimeMillis()
				deltaUpdate += ((now - initialTime) / timeU).toDouble()

				GLFW.glfwPollEvents()
				window.pollEvents()
				gameLogic.input(context, now - initialTime)
				window.resetInput()

				if (deltaUpdate >= 1)
				{
					val diffTimeMillis = now - updateTime
					gameLogic.update(context, diffTimeMillis)
					updateTime = now
					deltaUpdate--
				}

				render.render(context)

				initialTime = now
			}
		}
		catch (sg: StopGame)
		{
			Logger.info("hard-stopping game")
			when (val reason = sg.reason)
			{
				null -> Logger.info("no reason given")
				else -> Logger.info("reason: \"$reason\"")
			}
		}
		catch (e: Exception)
		{
			Logger.error(e) { "EXCEPTION FUCK" }
		}
		catch (t: Throwable)
		{
			Logger.error(t) { "SOMETHING THREW HARDER THAN ELI FUCK" }
		}

		cleanup()
	}
}