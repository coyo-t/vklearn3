package fpw

import org.joml.Math.toRadians
import org.lwjgl.glfw.GLFW.glfwPollEvents
import kotlin.math.PI
import kotlin.math.sin

class Engine (val window: Window)
{
	val testTexture = ResourceLocation.withDefaultNameSpace("image/cros.png")
	val testShader = ResourceLocation.withDefaultNameSpace("shader/scene.lua")

	val entities = mutableListOf<Entity>()
	val projection = Projection(
		fov = 90f,
		zNear = 0.001f,
		zFar = 100f,
		width = window.wide,
		height = window.tall,
	)

	var viewPoint: Entity? = null

	private val render = Renderer(this)


	private fun <T: Entity> addEntity (who: T, init:T.()->Unit): T
	{
		init.invoke(who)
		entities.add(who)
		return who
	}

	fun run ()
	{
		viewPoint = addEntity(Entity("camera", "", 0f, 0f, 0f)) {
			update = {
				position.set(0.5, 0.0, 0.0)
				rotation.setAngleAxis(
					toRadians(sin(window.time * PI) * 22.5),
					0.0,
					0.0,
					1.0
				)
			}
		}

		addEntity(Entity("tha cube", "Cubezor", 0f, 0f, -2f)) {
			update = { milliTimeDiff ->
				val dt = milliTimeDiff / 1000.0
				val fdt = dt.toFloat()
				rotation
				.rotateX(toRadians(fdt * 45f))
				.rotateY(toRadians(fdt * 60f))
			}
		}
		addEntity(Entity("another one lol", "Cubezor", -0.5f, -0.5f, -3f)) {

		}
		render.init()
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
				window.input.input()
				window.input.resetInput()

				var ticks = minOf(deltaUpdate.toInt(), 10)

				while (ticks >= 1)
				{
					val diffTimeMillis = now - updateTime
					for (entity in entities)
					{
						entity.update?.invoke(entity, diffTimeMillis)
					}
					updateTime = now
					ticks--
				}

				render.render(this)

				initialTime = now
			}
		}
//		catch (sg: StopGame)
//		{
//			FUtil.logInfo("hard-stopping game")
//			when (val reason = sg.reason)
//			{
//				null -> FUtil.logInfo("no reason given")
//				else -> FUtil.logInfo("reason: \"$reason\"")
//			}
//		}
		catch (e: Exception)
		{
			FUtil.logError(e) { "EXCEPTION FUCK" }
		}
		catch (t: Throwable)
		{
			FUtil.logError(t) { "SOMETHING THREW HARDER THAN ELI FUCK" }
		}

		render.free()
		window.free()
	}

}