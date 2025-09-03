package fpw.ren

import fpw.ren.device.GPUDevice
import org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER
import org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER
import org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER


class DescriptorAllocator (
	val gpu: GPUDevice,
)
{

	private val descLimits: MutableMap<Int, Int>
	private val descPoolList = mutableListOf<PoolInfo>()
	private val descSetInfoMap = mutableMapOf<String, SetInfo>()

	init
	{
		val limits = gpu.hardwareDevice.vkPhysicalDeviceProperties.properties().limits()
		descLimits = mutableMapOf(
			VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER
			to limits.maxDescriptorSetUniformBuffers(),

			VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER
			to limits.maxDescriptorSetSamplers(),

			VK_DESCRIPTOR_TYPE_STORAGE_BUFFER
			to limits.maxDescriptorSetStorageBuffers(),
		)
		descPoolList.add(createDescPoolInfo(descLimits))
	}

	private fun createDescPoolInfo (descLimits: MutableMap<Int, Int>): PoolInfo
	{
		val descCount = descLimits.toMutableMap()
		val descTypeCounts = mutableListOf<DescriptorPool.DescTypeCount>()
		descLimits.forEach { (k, v) ->
			descTypeCounts.add(DescriptorPool.DescTypeCount(k, v))
		}
		val descPool = DescriptorPool(gpu, descTypeCounts)
		return PoolInfo(descCount, descPool)
	}

	fun addDescSets (
		id: String,
		descSetLayout: DescriptorSetLayout,
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
			DescriptorSet(gpu, targetPool.descPool, descSetLayout)
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
		descSetInfoMap.clear()
		descPoolList.forEach { it.descPool.free() }
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