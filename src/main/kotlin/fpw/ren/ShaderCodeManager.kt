package fpw.ren

import fpw.FUtil
import fpw.LuaCoyote
import fpw.ResourceLocation
import fpw.ren.enums.ShaderType
import org.lwjgl.util.shaderc.Shaderc.*
import party.iroiro.luajava.Lua.LuaType
import java.nio.ByteBuffer
import kotlin.io.path.div

class ShaderCodeManager (val renderer: Renderer)
{
	val L = LuaCoyote {
		openLibraries()
	}

	var doGenerateShaderDebugSymbols = true

	val nametable = mutableMapOf<ResourceLocation, Entry>()


	operator fun get (at: ResourceLocation): Entry
	{
		if (at in nametable)
		{
			return nametable[at]!!
		}

		try
		{
			val rp = FUtil.ASSETS_PATH/at.path
			L.run(FUtil.getFileBytes(rp), "")
			val result = L.get()
			check(result.type() == LuaType.TABLE) {
				"expecting a table, got ${result.type()}"
			}
			val vertexSrc = result["vertex"]!!.toString()
			val fragmentSrc = result["fragment"]!!.toString()

			val vc = compileSPIRV(at, vertexSrc, ShaderType.Vertex)
			val fc = compileSPIRV(at, fragmentSrc, ShaderType.Fragment)
			val outs = Entry(
				vertex = vc,
				fragment = fc,
			)
			nametable[at] = outs
			return outs
		}
		catch (e: Exception)
		{
			throw e
		}
		finally
		{
			L.top = 0
		}
	}

	private fun compileSPIRV (who: ResourceLocation, shaderCode: String, shaderType: ShaderType): ByteBuffer
	{
		var compiler = 0L
		var options = 0L

		try
		{
			compiler = shaderc_compiler_initialize()
			options = shaderc_compile_options_initialize()
			if (doGenerateShaderDebugSymbols)
			{
				shaderc_compile_options_set_generate_debug_info(options)
				shaderc_compile_options_set_optimization_level(options, 0)
				shaderc_compile_options_set_source_language(options, shaderc_source_language_glsl)
			}

			val result = shaderc_compile_into_spv(
				compiler,
				shaderCode,
				shaderType.scEnum,
				"$who @ $shaderType",
				"main",
				options
			)
			check(shaderc_result_get_compilation_status(result) == shaderc_compilation_status_success) {
				"Shader compilation failed: ${shaderc_result_get_error_message(result)}"
			}
			val outs = FUtil.createBuffer(shaderc_result_get_length(result))
			return outs.put(shaderc_result_get_bytes(result)).flip()
		}
		finally
		{
			shaderc_compile_options_release(options)
			shaderc_compiler_release(compiler)
		}
	}

	fun free ()
	{
		L.close()
	}

	data class Entry (
		val vertex: ByteBuffer,
		val fragment: ByteBuffer,
	)
}