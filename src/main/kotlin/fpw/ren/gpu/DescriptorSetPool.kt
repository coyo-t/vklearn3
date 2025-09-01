package fpw.ren.gpu

import fpw.ren.gpu.GPUtil.gpuCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT
import org.lwjgl.vulkan.VK10.vkCreateDescriptorPool
import org.lwjgl.vulkan.VK10.vkDestroyDescriptorPool
import org.lwjgl.vulkan.VK10.vkFreeDescriptorSets
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo
import org.lwjgl.vulkan.VkDescriptorPoolSize


class DescriptorSetPool
{
	val vkDescPool: Long
	val descTypeCounts: MutableList<DescTypeCount>

	constructor (device: LogicalDevice, descTypeCounts: MutableList<DescTypeCount>)
	{
		this.descTypeCounts = descTypeCounts
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
				.flags(VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT)
				.pPoolSizes(typeCounts)
				.maxSets(maxSets)

			val pDescriptorPool = stack.mallocLong(1)
			gpuCheck(
				vkCreateDescriptorPool(device.vkDevice, descriptorPoolInfo, null, pDescriptorPool),
				"Failed to create descriptor pool"
			)
			vkDescPool = pDescriptorPool[0]
		}
	}

	fun cleanup(device: LogicalDevice)
	{
//		Logger.debug("Destroying descriptor pool")
		vkDestroyDescriptorPool(device.vkDevice, vkDescPool, null)
	}

	fun freeDescriptorSet (device: LogicalDevice, vkDescriptorSet: Long)
	{
		MemoryStack.stackPush().use { stack ->
			val longBuffer = stack.mallocLong(1)
			longBuffer.put(0, vkDescriptorSet)
			gpuCheck(
				vkFreeDescriptorSets(device.vkDevice, vkDescPool, longBuffer),
				"Failed to free descriptor set"
			)
		}
	}

	data class DescTypeCount(val descType: Int, val count: Int)
}