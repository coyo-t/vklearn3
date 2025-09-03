package fpw.ren.gpu

import fpw.Renderer
import fpw.ren.gpu.GPUtil.gpuCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.vkCreateDescriptorSetLayout
import org.lwjgl.vulkan.VK10.vkDestroyDescriptorSetLayout
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo


class DescriptorLayout (
	val vkCtx: Renderer,
	vararg layoutInfos: Info,
)
{
	val layoutInfos = layoutInfos.toList()
	val vkDescLayout: Long

	init
	{
		MemoryStack.stackPush().use { stack ->
			val count = layoutInfos.size
			val layoutBindings = VkDescriptorSetLayoutBinding.calloc(count, stack)
			for (i in 0..<count)
			{
				val layoutInfo = layoutInfos[i]
				layoutBindings.get(i)
					.binding(layoutInfo.binding)
					.descriptorType(layoutInfo.descType.vk)
					.descriptorCount(layoutInfo.descCount)
					.stageFlags(layoutInfo.stage)
			}

			val vkLayoutInfo = VkDescriptorSetLayoutCreateInfo
			.calloc(stack)
			.`sType$Default`()
			.pBindings(layoutBindings)

			val pSetLayout = stack.mallocLong(1)
			gpuCheck(
				vkCreateDescriptorSetLayout(vkCtx.vkDevice, vkLayoutInfo, null, pSetLayout),
				"Failed to create descriptor set layout"
			)
			vkDescLayout = pSetLayout.get(0)
		}
	}

	fun free ()
	{
		vkDestroyDescriptorSetLayout(vkCtx.vkDevice, vkDescLayout, null)
	}

	data class Info(
		val descType: DescriptorType,
		val binding: Int,
		val descCount: Int,
		val stage: Int,
	)

}