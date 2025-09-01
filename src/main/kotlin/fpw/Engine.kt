package fpw

import org.joml.Math.toRadians
import org.lwjgl.glfw.GLFW.glfwPollEvents
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class Engine (val window: Window)
{
	val testTexture = ResourceLocation.withDefaultNameSpace("image/cros.png")
	val testShader = ResourceLocation.withDefaultNameSpace("shader/scene.lua")
	val testModel = ResourceLocation.withDefaultNameSpace("mesh/test cube.lua")
	val updatesPerSecond = 120

	val entities = mutableListOf<RenderEntity>()
	var lens = Projection(
		fov = 90f,
		zNear = 0.001f,
		zFar = 100f,
		width = window.wide,
		height = window.tall,
	)

	var viewPoint: RenderEntity? = null

	private val render = Renderer(this)


	private fun <T: RenderEntity> addEntity (who: T, init:T.()->Unit): T
	{
		init.invoke(who)
		entities.add(who)
		return who
	}

	fun run ()
	{
		viewPoint = addEntity(RenderEntity("camera")) {
			location.set(0.5, 0.0, 0.0)
		}

		addEntity(RenderEntity("tha cube")) {
			modelId = "test cube"
			location.set(0.0, 0.0, -2.0)
			update = { milliTimeDiff ->
				location.set(
					cos(window.time * PI*0.5) * 4.0,
					sin(window.time * PI) * 2.0,
					-2.0,
				)

				val dt = milliTimeDiff / 1000.0
				val fdt = dt.toFloat()
				rotation
				.rotateX(toRadians(fdt * 45f))
				.rotateY(toRadians(fdt * 60f))
			}
		}
		addEntity(RenderEntity("another one lol")) {
			modelId = "test cube"
			location.set(-0.5, -0.5, -3.0)
		}
		render.init(this)
		try
		{
			var initialTime = System.currentTimeMillis()
			val timeU = 1000.0f / updatesPerSecond
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