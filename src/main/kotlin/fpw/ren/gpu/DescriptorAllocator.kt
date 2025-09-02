package fpw.ren.gpu

import fpw.FUtil
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER
import org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER
import org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER


class DescriptorAllocator (
	val hardwareDevice: HardwareDevice,
	val logicalDevice: LogicalDevice,
)
{

	private val descLimits: MutableMap<Int, Int>
	private val descPoolList = mutableListOf<PoolInfo>()
	private val descSetInfoMap = mutableMapOf<String, SetInfo>()

	init
	{
		val limits = hardwareDevice.vkPhysicalDeviceProperties.properties().limits()
		descLimits = mutableMapOf(
			VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER to limits.maxDescriptorSetUniformBuffers(),
			VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER to limits.maxDescriptorSetSamplers(),
			VK_DESCRIPTOR_TYPE_STORAGE_BUFFER to limits.maxDescriptorSetStorageBuffers(),
		)
		descPoolList.add(createDescPoolInfo(descLimits))
//		descPoolList.add(createDescPoolInfo(device, descLimits))
	}

	private fun createDescPoolInfo (descLimits: MutableMap<Int, Int>): PoolInfo
	{
		val descCount = descLimits.toMutableMap()
		val descTypeCounts = mutableListOf<DescriptorPool.DescTypeCount>()
		descLimits.forEach { (k, v) ->
			descTypeCounts.add(DescriptorPool.DescTypeCount(k, v))
		}
		val descPool = DescriptorPool(logicalDevice, descTypeCounts)
		return PoolInfo(descCount, descPool)
	}

	fun addDescSets (
		id: String,
		descSetLayout: DescriptorLayout,
		count: Int=1,
	): List<DescriptorSet>
	{
		// Check if we have room for the sets in any descriptor pool
		var targetPool: PoolInfo? = null
		var poolPos = 0
		for (descPoolInfo in descPoolList)
		{
			for (layoutInfo in descSetLayout.layoutInfos)
			{
				val descType = layoutInfo.descType
				val dkvk = descType.vk
				val available = requireNotNull(descPoolInfo.descCount[dkvk]) {
					"Unknown type [$descType]"
				}
				val maxTotal = descLimits[dkvk]!!
				check(count <= maxTotal) {
					"Cannot create more than [$maxTotal] for descriptor type [$descType]"
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
			targetPool = createDescPoolInfo(descLimits)
			descPoolList.add(targetPool)
			poolPos++
		}

		val result = MutableList(count) {
			DescriptorSet(logicalDevice, targetPool.descPool, descSetLayout)
		}

		descSetInfoMap[id] = SetInfo(result, poolPos)

		// Update consumed descriptors
		for (layoutInfo in descSetLayout.layoutInfos)
		{
			val descType = layoutInfo.descType
			val dkvk = descType.vk
			targetPool.descCount[dkvk] = targetPool.descCount[dkvk]!! - count
		}

		return result
	}

	fun free ()
	{
//		Logger.debug("Destroying descriptor allocator")
		descSetInfoMap.clear()
		descPoolList.forEach { it.descPool.free(logicalDevice) }
	}

	fun freeDescSet (id: String?)
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
		descSetInfo.descSets.forEach {
			descPoolInfo.descPool.freeDescriptorSet(logicalDevice, it.vkDescriptorSet)
		}
	}

	fun getDescSet(id: String, pos: Int=0): DescriptorSet
	{
		return descSetInfoMap[id]?.let { it.descSets[pos] }!!
	}

	class PoolInfo (
		val descCount: MutableMap<Int, Int>,
		val descPool: DescriptorPool,
	)

	class SetInfo(
		val descSets: MutableList<DescriptorSet>,
		val poolPos: Int,
	)
}