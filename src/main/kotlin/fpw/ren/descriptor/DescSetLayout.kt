package fpw.ren.descriptor

import fpw.ren.Renderer
import fpw.ren.GPUtil.gpuCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo

class DescSetLayout (
	val renderer: Renderer,
	vararg laouts: Info,
)
{
	val vk: Long

	init
	{
		MemoryStack.stackPush().use { stack ->
			val count = laouts.size
			val layoutBindings = VkDescriptorSetLayoutBinding.calloc(count, stack)
			for (i in 0..<count)
			{
				val layoutInfo = laouts[i]
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
					renderer.gpu.logicalDevice.vkDevice,
					vkLayoutInfo,
					null,
					pSetLayout,
				),
				"Failed to create descriptor set layout"
			)
			vk = pSetLayout[0]
		}
	}

	fun free ()
	{
		vkDestroyDescriptorSetLayout(renderer.gpu.logicalDevice.vkDevice, vk, null)
	}

	data class Info(
		val descType: DescType,
		val binding: Int,
		val descCount: Int,
		val stage: Int,
	)

}