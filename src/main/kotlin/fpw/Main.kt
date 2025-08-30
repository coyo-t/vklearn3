package fpw

import fpw.ren.gpu.GPUMeshData
import fpw.ren.gpu.GPUModelData
import org.joml.Math.toRadians


class Main: GameLogic
{
	lateinit var thaCubeEntity: Entity

	override fun init(context: EngineContext): InitData
	{
		val modelId = "Cubezor"
		val meshData = GPUMeshData(
			"mesh.cubezor",
			positions = floatArrayOf(
				-0.5f, +0.5f, +0.5f,
				-0.5f, -0.5f, +0.5f,
				+0.5f, -0.5f, +0.5f,
				+0.5f, +0.5f, +0.5f,
				-0.5f, +0.5f, -0.5f,
				+0.5f, +0.5f, -0.5f,
				-0.5f, -0.5f, -0.5f,
				+0.5f, -0.5f, -0.5f,
			),
			texCoords = floatArrayOf(
				0.0f, 0.0f,
				0.5f, 0.0f,
				1.0f, 0.0f,
				1.0f, 0.5f,
				1.0f, 1.0f,
				0.5f, 1.0f,
				0.0f, 1.0f,
				0.0f, 0.5f,
			),
			indices = intArrayOf(
				// Front face
				0, 1, 3, 3, 1, 2,
				// Top Face
				4, 0, 3, 5, 4, 3,
				// Right face
				3, 2, 7, 5, 3, 7,
				// Left face
				6, 1, 0, 6, 0, 4,
				// Bottom face
				2, 1, 6, 2, 6, 7,
				// Back face
				7, 6, 4, 7, 4, 5,
			)
		)
		val modelData = GPUModelData(modelId, listOf(meshData))

		thaCubeEntity = Entity("Cubezor", modelId, 0f, 0f, -2f)
		context.scene.entities.addAll(listOf(
			thaCubeEntity,
			Entity("another one lol", modelId, -0.5f, -0.5f, -3f),
		))

		return InitData(listOf(modelData))

	}

	override fun input(context: EngineContext, diffTimeMillis: Long)
	{
	}

	override fun update(context: EngineContext, diffTimeMillis: Long)
	{
		val dt = diffTimeMillis / 1000.0
		with (thaCubeEntity)
		{
			val fdt = dt.toFloat()
			rotation
			.rotateX(toRadians(fdt * 45f))
			.rotateY(toRadians(fdt * 60f))
			updateModelMatrix()
		}
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
				Engine("MACHINE WITNESS", Main()).use {
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

//		fun logTrace (f:String, vararg n: Any?)
//		{
//			println("${ANSI_PURPLE}-.-${ANSI_RESET} $f".format(*n))
//		}

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
