package fpw.ren.gpu

import org.lwjgl.vulkan.VK10

class GraphicsQueue(vkCtx: GPUContext, queueIndex: Int):
	GPUCommandQueue(vkCtx, getGraphicsQueueFamilyIndex(vkCtx), queueIndex)
{
	companion object
	{
		private fun getGraphicsQueueFamilyIndex(vkCtx: GPUContext): Int
		{
			val queuePropsBuff = vkCtx.physDevice.vkQueueFamilyProps
			val uhh = queuePropsBuff.indexOfFirst { (it.queueFlags() and VK10.VK_QUEUE_GRAPHICS_BIT) != 0 }
//				var index = -1
//				val numQueuesFamilies = queuePropsBuff.capacity()
//				for (i in 0..<numQueuesFamilies)
//				{
//					val props = queuePropsBuff.get(i)
//					val graphicsQueue = (props.queueFlags() and VK_QUEUE_GRAPHICS_BIT) != 0
//					if (graphicsQueue)
//					{
//						index = i
//						break
//					}
//				}

			require(uhh >= 0) {
				"Failed to get graphics Queue family index"
			}
			return uhh
		}
	}
}