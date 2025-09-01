package fpw.ren.gpu

import fpw.Renderer
import fpw.ren.gpu.GPUtil.gpuCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.vkCreateDescriptorSetLayout
import org.lwjgl.vulkan.VK10.vkDestroyDescriptorSetLayout
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo


class DescriptorSetLayout
{
	val layoutInfos: List<LayoutInfo>
	val vkDescLayout: Long

	constructor (vkCtx: Renderer, vararg layoutInfos: LayoutInfo)
	{
		this.layoutInfos = layoutInfos.toList()
		MemoryStack.stackPush().use { stack ->
			val count = layoutInfos.size
			val layoutBindings = VkDescriptorSetLayoutBinding.calloc(count, stack)
			for (i in 0..<count)
			{
				val layoutInfo = layoutInfos[i]
				layoutBindings.get(i)
					.binding(layoutInfo.binding)
					.descriptorType(layoutInfo.descType)
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

	fun cleanup(vkCtx: Renderer)
	{
//		Logger.debug("Destroying descriptor set layout")
		vkDestroyDescriptorSetLayout(vkCtx.vkDevice, vkDescLayout, null)
	}

	fun getLayoutInfo (): LayoutInfo
	{
		return layoutInfos[0]
	}

	data class LayoutInfo(val descType: Int, val binding: Int, val descCount: Int, val stage: Int)

}