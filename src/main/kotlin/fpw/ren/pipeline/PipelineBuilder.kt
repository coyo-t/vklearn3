package fpw.ren.pipeline

import fpw.ren.GPUtil.registerForCleanup
import fpw.ren.enums.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_A_BIT
import org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_B_BIT
import org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_G_BIT
import org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_R_BIT
import java.nio.IntBuffer

class PipelineBuilder
{
	private var dynamicStateList: IntBuffer? = null
	val assemblyState = registerForCleanup(VkPipelineInputAssemblyStateCreateInfo::calloc) {
		`sType$Default`()
	}
	val viewportState = registerForCleanup(VkPipelineViewportStateCreateInfo::calloc) {
		`sType$Default`()
	}
	val rasterState = registerForCleanup(VkPipelineRasterizationStateCreateInfo::calloc) {
		`sType$Default`()
	}
	val multisampleState = registerForCleanup(VkPipelineMultisampleStateCreateInfo::calloc) {
		`sType$Default`()
	}
	val dynamicState = registerForCleanup(VkPipelineDynamicStateCreateInfo::calloc) {
		`sType$Default`()
	}
	val colorBlendState = registerForCleanup(VkPipelineColorBlendStateCreateInfo::calloc) {
		`sType$Default`()
	}
	val depthStencilState = registerForCleanup(VkPipelineDepthStencilStateCreateInfo::calloc) {
		`sType$Default`()
	}
	val blendAttachState = registerForCleanup(VkPipelineColorBlendAttachmentState.calloc(1))

	constructor ()

	constructor (init: PipelineBuilder.()->Unit): this() {
		init(this)
	}

	fun zTestEnabled (uhh: Boolean)
	{
		depthStencilState.depthTestEnable(uhh)
	}

	fun zWriteEnabled (uhh: Boolean)
	{
		depthStencilState.depthWriteEnable(uhh)
	}

	fun zCompareOperation (c: CompareOperation)
	{
		depthStencilState.depthCompareOp(c.vk)
	}

	fun stencilEnabled (uhh: Boolean)
	{
		depthStencilState.stencilTestEnable(uhh)
	}

	fun blendingEnabled (uhh: Boolean)
	{
		blendAttachState.blendEnable(uhh)
	}

	fun blendModeExt (
		srcColor: BlendFactor, dstColor: BlendFactor,
		srcAlpha: BlendFactor, dstAlpha: BlendFactor,
	)
	{
		blendAttachState.srcColorBlendFactor(srcColor.vk)
		blendAttachState.dstColorBlendFactor(dstColor.vk)
		blendAttachState.srcAlphaBlendFactor(srcAlpha.vk)
		blendAttachState.dstAlphaBlendFactor(dstAlpha.vk)
	}

	fun blendMode (src: BlendFactor, dst: BlendFactor)
	{
		blendModeExt(src,dst, src,dst)
	}

	fun blendOperation (c: BlendOperation, a: BlendOperation)
	{
		blendAttachState.colorBlendOp(c.vk)
		blendAttachState.alphaBlendOp(a.vk)
	}

	fun colorWriteEnable (r: Boolean, g: Boolean, b: Boolean, a: Boolean)
	{
		blendAttachState.colorWriteMask(
			(if (r) VK_COLOR_COMPONENT_R_BIT else 0) or
			(if (g) VK_COLOR_COMPONENT_G_BIT else 0) or
			(if (b) VK_COLOR_COMPONENT_B_BIT else 0) or
			(if (a) VK_COLOR_COMPONENT_A_BIT else 0) or
			0
		)
	}

	fun dynamicStates (vararg s: DynamicStates)
	{
		val vd = MemoryStack.stackMallocInt(s.size)
		for (i in 0..<s.size)
		{
			vd.put(i, s[i].vk)
		}
		dynamicState.pDynamicStates(vd)
		dynamicStateList = vd
	}

	fun polygonMode (pm: PolygonMode)
	{
		rasterState.polygonMode(pm.vk)
	}

	fun culling (whichSideGetsCulled: CullingMode, frontFaceWindingOrder: WindingOrder)
	{
		rasterState.cullMode(whichSideGetsCulled.vk)
		rasterState.frontFace(frontFaceWindingOrder.vk)
	}

	fun primitiveType (pr: Pr)
	{
		assemblyState.topology(pr.vkEnum)
	}

	fun primitiveRestartEnabled (uhh: Boolean)
	{
		assemblyState.primitiveRestartEnable(uhh)
	}

	fun lineSize (c: Number)
	{
		rasterState.lineWidth(c.toFloat())
	}

	fun viewportCount (c:Int)
	{
		viewportState.viewportCount(c)
	}

	fun scissorCount (c: Int)
	{
		viewportState.scissorCount(c)
	}


}