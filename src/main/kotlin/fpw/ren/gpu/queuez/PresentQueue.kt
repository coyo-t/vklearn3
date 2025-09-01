package fpw.ren.gpu.queuez

import fpw.ren.gpu.GPUContext
import fpw.ren.gpu.getPresentQueueFamilyIndex

class PresentQueue (vkCtx: GPUContext, queueIndex: Int):
	CommandQueue(vkCtx, vkCtx.getPresentQueueFamilyIndex(), queueIndex)