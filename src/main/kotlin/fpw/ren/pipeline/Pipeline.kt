package fpw.ren.pipeline

import fpw.ren.GPUtil
import fpw.ren.GPUtil.longs
import fpw.ren.Renderer
import fpw.ren.ShaderModule
import fpw.ren.descriptor.DescriptorSetLayout
import fpw.ren.enums.Pr
import fpw.ren.model.VertexFormat
import fpw.ren.topology
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo
import org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo
import org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo
import org.lwjgl.vulkan.VkPipelineRenderingCreateInfo
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo

class Pipeline (
	val renderer: Renderer,
	colorFormat: Int,
	shaderModules: List<ShaderModule>,
	vertexFormat: VertexFormat,
	depthFormat: Int = VK10.VK_FORMAT_UNDEFINED,
	pushConstRange: List<Triple<Int, Int, Int>> = emptyList(),
	descriptorSetLayouts: List<DescriptorSetLayout> = emptyList(),
)
{
	val vkPipeline: Long
	val vkPipelineLayout: Long

	init
	{
		val device = renderer.gpu.logicalDevice
		MemoryStack.stackPush().use { stack ->
			val lp = stack.mallocLong(1)
			val main = stack.UTF8("main")

			val numModules = shaderModules.size
			val shaderStages = VkPipelineShaderStageCreateInfo.calloc(numModules, stack)
			shaderStages.withIndex().forEach { (i, shStage) ->
				val shaderModule = shaderModules[i]
				shStage.`sType$Default`()
				shStage.stage(shaderModule.shaderStage.vkFlag)
				shStage.module(shaderModule.handle)
				shStage.pName(main)
			}

			val assemblyState = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
			assemblyState.`sType$Default`()
			assemblyState.topology(Pr.TriangleList)

			val viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
			viewportState.`sType$Default`()
			viewportState.viewportCount(1)
			viewportState.scissorCount(1)

			val rasterState = VkPipelineRasterizationStateCreateInfo.calloc(stack)
			rasterState.`sType$Default`()
			rasterState.polygonMode(VK10.VK_POLYGON_MODE_FILL)
			rasterState.cullMode(VK10.VK_CULL_MODE_BACK_BIT)
			rasterState.frontFace(VK10.VK_FRONT_FACE_CLOCKWISE)
			rasterState.lineWidth(1f)

			val multisampleState = VkPipelineMultisampleStateCreateInfo.calloc(stack)
			multisampleState.`sType$Default`()
			multisampleState.rasterizationSamples(VK10.VK_SAMPLE_COUNT_1_BIT)

			val dynamicState = VkPipelineDynamicStateCreateInfo.calloc(stack)
			dynamicState.`sType$Default`()
			dynamicState.pDynamicStates(
				stack.ints(
					VK10.VK_DYNAMIC_STATE_VIEWPORT,
					VK10.VK_DYNAMIC_STATE_SCISSOR,
				)
			)

			val blendAttachState = VkPipelineColorBlendAttachmentState.calloc(1, stack)
			blendAttachState.colorWriteMask(GPUtil.CW_MASK_RGBA)
			blendAttachState.blendEnable(false)

			val colorBlendState = VkPipelineColorBlendStateCreateInfo.calloc(stack)
			colorBlendState.`sType$Default`()
			colorBlendState.pAttachments(blendAttachState)

			val ds = if (depthFormat != VK10.VK_FORMAT_UNDEFINED)
			{
				val it = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
				it.`sType$Default`()
				it.depthTestEnable(true)
				it.depthWriteEnable(true)
				it.depthCompareOp(VK10.VK_COMPARE_OP_LESS_OR_EQUAL)
				it.depthBoundsTestEnable(false)
				it.stencilTestEnable(false)
				it
			}
			else
			{
				null
			}

//			val numPushConstants = pushConstRange.size
//			val vpcr: VkPushConstantRange.Buffer?
//			if (numPushConstants > 0)
//			{
//				vpcr = VkPushConstantRange.calloc(numPushConstants, stack)
//				for (i in 0..<numPushConstants)
//				{
//					val (stage, offset, size) = pushConstRange[i]
//					vpcr[i]
//						.stageFlags(stage)
//						.offset(offset)
//						.size(size)
//				}
//			}
//			else
//			{
//				vpcr = null
//			}

			val ppLayout = stack.longs(descriptorSetLayouts.size) {
				descriptorSetLayouts[it].vkDescLayout
			}

			val pPipelineLayoutCreateInfo = VkPipelineLayoutCreateInfo.calloc(stack)
			pPipelineLayoutCreateInfo.`sType$Default`()
			pPipelineLayoutCreateInfo.pSetLayouts(ppLayout)
//			pPipelineLayoutCreateInfo.pPushConstantRanges(vpcr)

			GPUtil.gpuCheck(
				VK10.vkCreatePipelineLayout(device.vkDevice, pPipelineLayoutCreateInfo, null, lp),
				"Failed to create pipeline layout"
			)
			vkPipelineLayout = lp.get(0)

			val createInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
			createInfo.`sType$Default`()
			createInfo.renderPass(VK10.VK_NULL_HANDLE)
			createInfo.pStages(shaderStages)
			createInfo.pVertexInputState(vertexFormat.vi)
			createInfo.pInputAssemblyState(assemblyState)
			createInfo.pViewportState(viewportState)
			createInfo.pRasterizationState(rasterState)
			createInfo.pColorBlendState(colorBlendState)
			createInfo.pMultisampleState(multisampleState)
			createInfo.pDynamicState(dynamicState)
			createInfo.layout(vkPipelineLayout)

			val rendCreateInfo = VkPipelineRenderingCreateInfo.calloc(stack)
			rendCreateInfo.`sType$Default`()
			rendCreateInfo.colorAttachmentCount(1)
			rendCreateInfo.pColorAttachmentFormats(stack.ints(colorFormat))
			if (ds != null)
			{
				rendCreateInfo.depthAttachmentFormat(depthFormat)
			}

			createInfo.pNext(rendCreateInfo)
			if (ds != null)
			{
				createInfo.pDepthStencilState(ds)
			}

			GPUtil.gpuCheck(
				VK10.vkCreateGraphicsPipelines(
					device.vkDevice,
					VK10.VK_NULL_HANDLE,
					createInfo,
					null,
					lp
				),
				"Error creating graphics pipeline"
			)
			vkPipeline = lp.get(0)
		}
	}

	fun free ()
	{
		val vkDevice = renderer.gpu.logicalDevice.vkDevice
		VK10.vkDestroyPipelineLayout(vkDevice, vkPipelineLayout, null)
		VK10.vkDestroyPipeline(vkDevice, vkPipeline, null)
	}

}