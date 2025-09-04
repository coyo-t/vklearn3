package fpw.ren.pipeline

import fpw.ren.GPUtil.registerForCleanup
import fpw.ren.enums.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import java.nio.IntBuffer

class PipelineLayoutBuilder
{
	private var dynamicStateList: IntBuffer? = null
	val assembly = registerForCleanup(VkPipelineInputAssemblyStateCreateInfo::calloc) {
		`sType$Default`()
	}
	val viewport = registerForCleanup(VkPipelineViewportStateCreateInfo::calloc) {
		`sType$Default`()
	}
	val raster = registerForCleanup(VkPipelineRasterizationStateCreateInfo::calloc) {
		`sType$Default`()
	}
	val multisample = registerForCleanup(VkPipelineMultisampleStateCreateInfo::calloc) {
		`sType$Default`()
	}
	val dynamic = registerForCleanup(VkPipelineDynamicStateCreateInfo::calloc) {
		`sType$Default`()
	}
	val colorBlend = registerForCleanup(VkPipelineColorBlendStateCreateInfo::calloc) {
		`sType$Default`()
	}
	val depthStencil = registerForCleanup(VkPipelineDepthStencilStateCreateInfo::calloc) {
		`sType$Default`()
	}
	val blendAttach = registerForCleanup(VkPipelineColorBlendAttachmentState.calloc(1))

	fun enableBlending (uhh: Boolean=true)
	{
		blendAttach.blendEnable(uhh)
	}

	fun blendModeExt (
		srcColor: BlendFactor,
		dstColor: BlendFactor,
		srcAlpha: BlendFactor,
		dstAlpha: BlendFactor,
	)
	{
		blendAttach.srcColorBlendFactor(srcColor.vk)
		blendAttach.dstColorBlendFactor(dstColor.vk)
		blendAttach.srcAlphaBlendFactor(srcAlpha.vk)
		blendAttach.dstAlphaBlendFactor(dstAlpha.vk)
	}

	fun blendMode (
		src: BlendFactor,
		dst: BlendFactor,
	)
	{
		blendModeExt(src,dst, src,dst)
	}

	fun blendOperation (c: BlendOperation, a: BlendOperation)
	{
		blendAttach.colorBlendOp(c.vk)
		blendAttach.alphaBlendOp(a.vk)
	}

	fun dynamicStates (vararg s: DynamicStates)
	{
		val vd = MemoryStack.stackMallocInt(s.size)
		for (i in 0..<s.size)
		{
			vd.put(i, s[i].vk)
		}
		dynamic.pDynamicStates(vd)
		dynamicStateList = vd
	}

	fun polygonMode (pm: PolygonMode)
	{
		raster.polygonMode(pm.vk)
	}

	fun culling (c: CullingMode, w: WindingOrder)
	{
		raster.cullMode(c.vk)
		raster.frontFace(w.vk)
	}

	fun primitiveType (pr: Pr)
	{
		assembly.topology(pr.vkEnum)
	}

	fun lineSize (c: Number)
	{
		raster.lineWidth(c.toFloat())
	}

	fun viewportCount (c:Int)
	{
		viewport.viewportCount(c)
	}

	fun scissorCount (c: Int)
	{
		viewport.scissorCount(c)
	}


}