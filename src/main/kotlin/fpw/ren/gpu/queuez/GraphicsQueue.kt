package fpw.ren.gpu.queuez

import fpw.ren.gpu.GPUContext
import fpw.ren.gpu.getGraphicsQueueFamilyIndex

class GraphicsQueue(vkCtx: GPUContext, queueIndex: Int):
	CommandQueue(vkCtx, vkCtx.getGraphicsQueueFamilyIndex(), queueIndex)
