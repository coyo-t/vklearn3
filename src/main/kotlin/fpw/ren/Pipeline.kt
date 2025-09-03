package fpw.ren

import fpw.Renderer
import fpw.ren.GPUtil.gpuCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.VK_CULL_MODE_NONE
import org.lwjgl.vulkan.VK10.VK_POLYGON_MODE_FILL
import org.lwjgl.vulkan.VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST
import org.lwjgl.vulkan.VK10.vkCreateGraphicsPipelines
import org.lwjgl.vulkan.VK10.vkDestroyPipeline
import org.lwjgl.vulkan.VK14.*


class Pipeline (
	val renderer: Renderer,
	colorFormat: Int,
	shaderModules: List<ShaderModule>,
	vertexFormat: VkPipelineVertexInputStateCreateInfo,
	depthFormat: Int = VK_FORMAT_UNDEFINED,
	pushConstRange: List<Triple<Int, Int, Int>> = emptyList(),
	descriptorSetLayouts: List<DescriptorLayout> = emptyList(),
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
			for (i in 0..<numModules)
			{
				val shaderModule = shaderModules[i]
				val shStage = shaderStages.get(i)
				shStage.`sType$Default`()
				shStage.stage(shaderModule.shaderStage)
				shStage.module(shaderModule.handle)
				shStage.pName(main)
			}

			val assemblyState = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
			assemblyState.`sType$Default`()
			assemblyState.topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)

			val viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
			viewportState.`sType$Default`()
			viewportState.viewportCount(1)
			viewportState.scissorCount(1)

			val rasterState = VkPipelineRasterizationStateCreateInfo.calloc(stack)
			rasterState.`sType$Default`()
			rasterState.polygonMode(VK_POLYGON_MODE_FILL)
			rasterState.cullMode(VK_CULL_MODE_BACK_BIT)
			rasterState.frontFace(VK_FRONT_FACE_CLOCKWISE)
			rasterState.lineWidth(1f)

			val multisampleState = VkPipelineMultisampleStateCreateInfo.calloc(stack)
			multisampleState.`sType$Default`()
			multisampleState.rasterizationSamples(VK_SAMPLE_COUNT_1_BIT)

			val dynamicState = VkPipelineDynamicStateCreateInfo.calloc(stack)
			dynamicState.`sType$Default`()
			dynamicState.pDynamicStates(
				stack.ints(
					VK_DYNAMIC_STATE_VIEWPORT,
					VK_DYNAMIC_STATE_SCISSOR,
				)
			)

			val blendAttachState = VkPipelineColorBlendAttachmentState.calloc(1, stack)
			blendAttachState.colorWriteMask(GPUtil.CW_MASK_RGBA)
			blendAttachState.blendEnable(false)

			val colorBlendState = VkPipelineColorBlendStateCreateInfo.calloc(stack)
			colorBlendState.`sType$Default`()
			colorBlendState.pAttachments(blendAttachState)

			val ds = if (depthFormat != VK_FORMAT_UNDEFINED)
			{
				val it = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
				it.`sType$Default`()
				it.depthTestEnable(true)
				it.depthWriteEnable(true)
				it.depthCompareOp(VK_COMPARE_OP_LESS_OR_EQUAL)
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

			val numLayouts = descriptorSetLayouts.size

			val ppLayout = stack.mallocLong(numLayouts)
			for (i in 0..<numLayouts)
			{
				ppLayout.put(i, descriptorSetLayouts[i].vkDescLayout)
			}

			val pPipelineLayoutCreateInfo = VkPipelineLayoutCreateInfo.calloc(stack)
			pPipelineLayoutCreateInfo.`sType$Default`()
			pPipelineLayoutCreateInfo.pSetLayouts(ppLayout)
//			pPipelineLayoutCreateInfo.pPushConstantRanges(vpcr)

			gpuCheck(
				vkCreatePipelineLayout(device.vkDevice, pPipelineLayoutCreateInfo, null, lp),
				"Failed to create pipeline layout"
			)
			vkPipelineLayout = lp.get(0)

			val createInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
			createInfo.`sType$Default`()
			createInfo.renderPass(VK_NULL_HANDLE)
			createInfo.pStages(shaderStages)
			createInfo.pVertexInputState(vertexFormat)
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

			gpuCheck(
				vkCreateGraphicsPipelines(
					device.vkDevice,
					VK_NULL_HANDLE,
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
		vkDestroyPipelineLayout(vkDevice, vkPipelineLayout, null)
		vkDestroyPipeline(vkDevice, vkPipeline, null)
	}

}