package fpw.ren.gpu

import fpw.FUtil
import org.lwjgl.vulkan.VK10.*


class DescriptorAllocator
{

	private val descLimits = mutableMapOf<Int, Int>()
	private val descPoolList = mutableListOf<DescPoolInfo>()
	private val descSetInfoMap = mutableMapOf<String, DescSetInfo>()

	constructor (physDevice: HardwareDevice, device: LogicalDevice)
	{
		descPoolList.add(createDescPoolInfo(device, descLimits))
	}

	private fun createDescLimits(physDevice: HardwareDevice): MutableMap<Int, Int>
	{
		val limits = physDevice.vkPhysicalDeviceProperties.properties().limits()
		return mutableMapOf(
			VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER to limits.maxDescriptorSetUniformBuffers(),
			VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER to limits.maxDescriptorSetSamplers(),
			VK_DESCRIPTOR_TYPE_STORAGE_BUFFER to limits.maxDescriptorSetStorageBuffers(),
		)
	}

	private fun createDescPoolInfo (device: LogicalDevice, descLimits: MutableMap<Int, Int>): DescPoolInfo
	{
		val descCount = descLimits.toMutableMap()
		val descTypeCounts = mutableListOf<DescriptorSetPool.DescTypeCount>()
		descLimits.forEach { (k, v) ->
			descTypeCounts.add(DescriptorSetPool.DescTypeCount(k, v))
		}
		val descPool = DescriptorSetPool(device, descTypeCounts)
		return DescPoolInfo(descCount, descPool)
	}

	fun addDescSets(device: LogicalDevice, id: String, count: Int, descSetLayout: DescriptorSetLayout): List<DescriptorSet>
	{
		// Check if we have room for the sets in any descriptor pool
		var targetPool: DescPoolInfo? = null
		var poolPos = 0
		for (descPoolInfo in descPoolList)
		{
			for (layoutInfo in descSetLayout.layoutInfos)
			{
				val descType = layoutInfo.descType
				val available = descPoolInfo.descCount[descType] ?: throw RuntimeException("Unknown type [$descType]")
				val maxTotal = descLimits[descType]!!
				if (count > maxTotal)
				{
					throw RuntimeException("Cannot create more than [$maxTotal] for descriptor type [$descType]")
				}
				if (available < count)
				{
					targetPool = null
					break
				}
				else
				{
					targetPool = descPoolInfo
				}
			}
			poolPos++
		}

		if (targetPool == null)
		{
			targetPool = createDescPoolInfo(device, descLimits)
			descPoolList.add(targetPool)
			poolPos++
		}

		val result = MutableList(count) {
			DescriptorSet(device, targetPool.descPool, descSetLayout)
		}

		descSetInfoMap[id] = DescSetInfo(result, poolPos)

		// Update consumed descriptors
		for (layoutInfo in descSetLayout.layoutInfos)
		{
			val descType = layoutInfo.descType
			targetPool.descCount[descType] = targetPool.descCount[descType]!! - count
		}

		return result
	}

	fun free(device: LogicalDevice)
	{
//		Logger.debug("Destroying descriptor allocator")
		descSetInfoMap.clear()
		descPoolList.forEach { it.descPool.cleanup(device) }
	}

	fun freeDescSet(device: LogicalDevice, id: String?)
	{
		val descSetInfo = descSetInfoMap[id]
		if (descSetInfo == null)
		{
			FUtil.logInfo("Could not find descriptor set with id [{}]", id)
			return
		}
		if (descSetInfo.poolPos >= descPoolList.size)
		{
			FUtil.logInfo("Could not find descriptor pool associated to set with id [{}]", id)
			return
		}
		val descPoolInfo = descPoolList[descSetInfo.poolPos]
		descSetInfo.descSets.forEach { descPoolInfo.descPool.freeDescriptorSet(device, it.vkDescriptorSet) }
	}

	fun getDescSet(id: String, pos: Int): DescriptorSet?
	{
		val descSetInfo = descSetInfoMap[id]
		if (descSetInfo != null)
		{
			return descSetInfo.descSets[pos]
		}
		return null
	}

	class DescPoolInfo (
		val descCount: MutableMap<Int, Int>,
		val descPool: DescriptorSetPool,
	)

	class DescSetInfo(
		val descSets: MutableList<DescriptorSet>,
		val poolPos: Int,
	)
}