package fpw.ren

import fpw.Renderer
import fpw.ren.GPUtil.gpuCheck
import fpw.ren.enums.DescriptorType
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.vkCreateDescriptorSetLayout
import org.lwjgl.vulkan.VK10.vkDestroyDescriptorSetLayout
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo


class DescriptorSetLayout (
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
				val f = layoutBindings[i]
				f.binding(layoutInfo.binding)
				f.descriptorType(layoutInfo.descType.vk)
				f.descriptorCount(layoutInfo.descCount)
				f.stageFlags(layoutInfo.stage)
			}

			val vkLayoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
			vkLayoutInfo.`sType$Default`()
			vkLayoutInfo.pBindings(layoutBindings)

			val pSetLayout = stack.mallocLong(1)
			gpuCheck(
				vkCreateDescriptorSetLayout(
					vkCtx.gpu.logicalDevice.vkDevice,
					vkLayoutInfo,
					null,
					pSetLayout,
				),
				"Failed to create descriptor set layout"
			)
			vkDescLayout = pSetLayout.get(0)
		}
	}

	fun free ()
	{
		vkDestroyDescriptorSetLayout(vkCtx.gpu.logicalDevice.vkDevice, vkDescLayout, null)
	}

	data class Info(
		val descType: DescriptorType,
		val binding: Int,
		val descCount: Int,
		val stage: Int,
	)

}