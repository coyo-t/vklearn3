package fpw.ren.gpu

import fpw.Main
import fpw.ren.gpu.GPUtil.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.vkCreateShaderModule
import org.lwjgl.vulkan.VK10.vkDestroyShaderModule
import org.lwjgl.vulkan.VkShaderModuleCreateInfo
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption


class GPUShaderModule private constructor (
	val handle: Long,
	val shaderStage: Int,
): GPUClosable
{

	override fun close(context: GPUContext)
	{
		vkDestroyShaderModule(context.vkDevice, handle, null)
	}


	companion object
	{
		fun create (context: GPUContext, shaderStage: Int, spirv: ByteBuffer): GPUShaderModule
		{
			check(spirv.isDirect) {
				"requires direct buffer"
			}
			return GPUShaderModule(
				handle = createShaderModule(context, spirv),
				shaderStage = shaderStage,
			)
		}

		fun create (context: GPUContext, shaderStage: Int, spirvSrc: Path): GPUShaderModule
		{
			try
			{
				FileChannel.open(spirvSrc, StandardOpenOption.READ).use { f ->
					MemoryStack.stackPush().use { stack ->
						val inpData = stack.malloc(f.size().toInt())
						f.read(inpData)
						inpData.flip()
						return GPUShaderModule(
							handle = createShaderModule(context, inpData),
							shaderStage = shaderStage,
						)
					}
				}
			}
			catch (e: Exception)
			{
				Main.logError(e) { "Error reading shader file" }
				throw RuntimeException(e)
			}
		}

		private fun createShaderModule(vkCtx: GPUContext, spirv: ByteBuffer): Long
		{
			MemoryStack.stackPush().use { stack ->
//				val pCode = stack.malloc(code.size).put(0, code)
				val pCode = spirv
				val moduleCreateInfo = VkShaderModuleCreateInfo.calloc(stack)
					.`sType$Default`()
					.pCode(pCode)

				val lp = stack.mallocLong(1)
				vkCheck(
					vkCreateShaderModule(vkCtx.vkDevice, moduleCreateInfo, null, lp),
					"Failed to create shader module"
				)
				return lp.get(0)
			}
		}
	}

}