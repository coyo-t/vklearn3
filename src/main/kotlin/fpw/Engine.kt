package fpw

import fpw.ren.goobers.Projection
import fpw.ren.goobers.RenderEntity
import fpw.ren.Renderer
import fpw.ren.goobers.EntityViewPoint
import org.joml.Math.toRadians
import org.lwjgl.glfw.GLFW.glfwPollEvents
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class Engine (val window: Window)
{
	val testTexture = ResourceLocation.create("image/terrain.png")
	val testShader = ResourceLocation.create("shader/scene.lua")
	val testModel = ResourceLocation.create("mesh/test cube.lua")
	val testModelTriangle = ResourceLocation.create("mesh/triangle.lua")
	val updatesPerSecond = 120

	val entities = mutableListOf<RenderEntity>()
	var lens = Projection(
		fov = 90f,
		zNear = 0.001f,
		zFar = 100f,
		width = window.wide,
		height = window.tall,
	)

	val viewPoint = EntityViewPoint(lens)

	private val render = Renderer(this)


	private inline fun <T: RenderEntity> addEntity (who: T, init:T.()->Unit): T
	{
		init.invoke(who)
		entities.add(who)
		return who
	}

	fun run ()
	{
		render.init()
		render.viewPoint = viewPoint
		window.show()

		viewPoint.viewer = addEntity(RenderEntity("camera")) {
			location.set(0.5, 0.0, 0.0)
		}

		addEntity(RenderEntity("another one lol")) {
			model = testModel
			location.set(-0.5, -0.5, -3.0)
		}
		addEntity(RenderEntity("tha cube")) {
			model = testModelTriangle
			location.set(0.0, 0.0, -2.0)
			setUpdateCallback {
				location.set(
					cos(window.time * PI * 0.5) * 1.0,
					sin(window.time * PI) * 0.5,
					-2.0,
				)

				val fdt = window.time.toFloat()
				rotation
					.identity()
					.rotateX(toRadians(fdt * 45f))
					.rotateY(toRadians(fdt * 60f))
			}
		}

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
						entity.update?.invokeitalize(diffTimeMillis)
					}
					updateTime = now
					ticks--
				}

				render.render()

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