package fpw.ren

import fpw.FUtil
import fpw.LuaCoyote
import fpw.ResourceLocation
import org.lwjgl.util.shaderc.Shaderc.*
import party.iroiro.luajava.Lua
import java.nio.ByteBuffer
import kotlin.io.path.div
import org.lwjgl.vulkan.VK14.*

object ShaderAssetThinger
{
	val L = LuaCoyote {
		openLibraries()
	}

	enum class ShaderType (
		val vkFlag: Int,
		val scEnum: Int,
	)
	{
		Vertex(
			VK_SHADER_STAGE_VERTEX_BIT,
			shaderc_vertex_shader,
		),
		Fragment(
			VK_SHADER_STAGE_FRAGMENT_BIT,
			shaderc_fragment_shader,
		),
		Compute(
			VK_SHADER_STAGE_COMPUTE_BIT,
			shaderc_compute_shader,
		),
	}

	var doGenerateShaderDebugSymbols = true

	fun compileSPIRV (shaderCode: String, shaderType: ShaderType): ByteBuffer
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

	fun loadFromLuaScript (at: ResourceLocation): Sources
	{
		try
		{
			val rp = FUtil.ASSETS_PATH/at.path
			L.run(FUtil.getFileBytes(rp), "")
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