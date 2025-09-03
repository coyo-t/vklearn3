package fpw.ren

import fpw.Renderer
import fpw.ren.GPUtil.gpuCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.vkCreateShaderModule
import org.lwjgl.vulkan.VK10.vkDestroyShaderModule
import org.lwjgl.vulkan.VkShaderModuleCreateInfo.calloc
import java.nio.ByteBuffer
import kotlin.use


class ShaderModule (
	val renderer: Renderer,
	val shaderStage: ShaderAssetThinger.ShaderType,
	spirv: ByteBuffer,
)
{
	val handle: Long
	init
	{
		MemoryStack.stackPush().use { stack ->
			val moduleCreateInfo = calloc(stack)
			moduleCreateInfo.`sType$Default`()
			moduleCreateInfo.pCode(spirv)

			val lp = stack.mallocLong(1)
			gpuCheck(
				vkCreateShaderModule(renderer.gpu.logicalDevice.vkDevice, moduleCreateInfo, null, lp),
				"Failed to create shader module"
			)
			handle = lp.get(0)
		}
	}

	fun free()
	{
		vkDestroyShaderModule(renderer.gpu.logicalDevice.vkDevice, handle, null)
	}
}