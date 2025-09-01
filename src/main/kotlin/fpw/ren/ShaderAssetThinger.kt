package fpw.ren

import fpw.FUtil
import fpw.LuaCoyote
import org.lwjgl.util.shaderc.Shaderc.*
import party.iroiro.luajava.Lua
import java.nio.ByteBuffer
import java.nio.file.Path


object ShaderAssetThinger
{
	val L = LuaCoyote {
		openLibraries()
	}

	var doGenerateShaderDebugSymbols = true

	fun compileSPIRV (shaderCode: String, shaderType: Int): ByteBuffer
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
				shaderType,
				"shader.glsl",
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

	fun loadFromLuaScript (at: Path): Sources
	{
		try
		{
			val src = FUtil.getFileBytes(at)
			L.run(src, "")
			val result = L.get()
			check(result.type() == Lua.LuaType.TABLE) {
				"expecting a table, got ${result.type()}"
			}

			return Sources(
				vertex = result["vertex"]!!.toString(),
				fragment = result["fragment"]!!.toString(),
			)
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

	data class Sources (
		val vertex: String,
		val fragment: String,
	)
}