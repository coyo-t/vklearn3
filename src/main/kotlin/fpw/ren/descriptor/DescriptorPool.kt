package fpw.ren.descriptor

import fpw.ren.descriptor.DescriptorSet
import fpw.ren.GPUtil
import fpw.ren.device.GPUDevice
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo
import org.lwjgl.vulkan.VkDescriptorPoolSize

class DescriptorPool (
	val device: GPUDevice,
	val descTypeCounts: MutableList<DescTypeCount>,
)
{
	val vkDescPool: Long

	init
	{
		MemoryStack.stackPush().use { stack ->
			var maxSets = 0
			val numTypes = descTypeCounts.size
			val typeCounts = VkDescriptorPoolSize.calloc(numTypes, stack)
			for (i in 0..<numTypes)
			{
				maxSets += descTypeCounts[i].count
				typeCounts.get(i)
					.type(descTypeCounts[i].descType)
					.descriptorCount(descTypeCounts[i].count)
			}

			val descriptorPoolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
				.`sType$Default`()
				.flags(VK10.VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT)
				.pPoolSizes(typeCounts)
				.maxSets(maxSets)

			val pDescriptorPool = stack.mallocLong(1)
			GPUtil.gpuCheck(
				VK10.vkCreateDescriptorPool(device.logicalDevice.vkDevice, descriptorPoolInfo, null, pDescriptorPool),
				"Failed to create descriptor pool"
			)
			vkDescPool = pDescriptorPool[0]
		}
	}

	fun free ()
	{
		VK10.vkDestroyDescriptorPool(device.logicalDevice.vkDevice, vkDescPool, null)
	}

	fun freeDescriptorSet (vkDescriptorSet: DescriptorSet)
	{
		MemoryStack.stackPush().use { stack ->
			val longBuffer = stack.mallocLong(1)
			longBuffer.put(0, vkDescriptorSet.vkDescriptorSet)
			GPUtil.gpuCheck(
				VK10.vkFreeDescriptorSets(device.logicalDevice.vkDevice, vkDescPool, longBuffer),
				"Failed to free descriptor set"
			)
		}
	}

	data class DescTypeCount(val descType: Int, val count: Int)
}