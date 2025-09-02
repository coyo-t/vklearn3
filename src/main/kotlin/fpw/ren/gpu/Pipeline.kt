package fpw.ren.gpu

import fpw.Renderer
import fpw.ren.gpu.GPUtil.gpuCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.VK_CULL_MODE_NONE
import org.lwjgl.vulkan.VK10.VK_POLYGON_MODE_FILL
import org.lwjgl.vulkan.VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST
import org.lwjgl.vulkan.VK10.vkCreateGraphicsPipelines
import org.lwjgl.vulkan.VK10.vkDestroyPipeline
import org.lwjgl.vulkan.VK14.*


class Pipeline (vkCtx: Renderer, buildInfo: Info)
{
	val vkPipeline: Long
	val vkPipelineLayout: Long

	init
	{
//		Main.logDebug("Creating pipeline")
		val device = vkCtx.device
		MemoryStack.stackPush().use { stack ->
			val lp = stack.mallocLong(1)
			val main = stack.UTF8("main")

			val shaderModules = buildInfo.shaderModules
			val numModules = shaderModules.size
			val shaderStages = VkPipelineShaderStageCreateInfo.calloc(numModules, stack)
			for (i in 0..<numModules)
			{
				val shaderModule = shaderModules[i]
				shaderStages.get(i)
					.`sType$Default`()
					.stage(shaderModule.shaderStage)
					.module(shaderModule.handle)
					.pName(main)
			}

			val assemblyStateCreateInfo = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
				.`sType$Default`()
				.topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)

			val viewportStateCreateInfo = VkPipelineViewportStateCreateInfo.calloc(stack)
				.`sType$Default`()
				.viewportCount(1)
				.scissorCount(1)

			val rasterizationStateCreateInfo = VkPipelineRasterizationStateCreateInfo.calloc(stack)
				.`sType$Default`()
				.polygonMode(VK_POLYGON_MODE_FILL)
				.cullMode(VK_CULL_MODE_NONE)
				.frontFace(VK_FRONT_FACE_CLOCKWISE)
				.lineWidth(1.0f)

			val multisampleStateCreateInfo = VkPipelineMultisampleStateCreateInfo.calloc(stack)
				.`sType$Default`()
				.rasterizationSamples(VK_SAMPLE_COUNT_1_BIT)

			val dynamicStateCreateInfo = VkPipelineDynamicStateCreateInfo.calloc(stack)
				.`sType$Default`()
				.pDynamicStates(
					stack.ints(
						VK_DYNAMIC_STATE_VIEWPORT,
						VK_DYNAMIC_STATE_SCISSOR
					)
				)

			val blendAttState = VkPipelineColorBlendAttachmentState.calloc(1, stack)
				.colorWriteMask(
					GPUtil.CW_MASK_RGBA
				)
				.blendEnable(false)

			val colorBlendState = VkPipelineColorBlendStateCreateInfo.calloc(stack)
				.`sType$Default`()
				.pAttachments(blendAttState)

			val ds = if (buildInfo.depthFormat != VK_FORMAT_UNDEFINED)
			{
				VkPipelineDepthStencilStateCreateInfo.calloc(stack)
					.`sType$Default`()
					.depthTestEnable(true)
					.depthWriteEnable(true)
					.depthCompareOp(VK_COMPARE_OP_LESS_OR_EQUAL)
					.depthBoundsTestEnable(false)
					.stencilTestEnable(false)
			}
			else
			{
				null
			}

			val pushConstRanges = buildInfo.pushConstRange
			val numPushConstants = pushConstRanges.size
			val vpcr: VkPushConstantRange.Buffer?
			if (numPushConstants > 0)
			{
				vpcr = VkPushConstantRange.calloc(numPushConstants, stack)
				for (i in 0..<numPushConstants)
				{
					val (stage, offset, size) = pushConstRanges[i]
					vpcr[i]
						.stageFlags(stage)
						.offset(offset)
						.size(size)
				}
			}
			else
			{
				vpcr = null
			}

			val descSetLayouts = buildInfo.descriptorSetLayouts
			val numLayouts = descSetLayouts.size
			val ppLayout = stack.mallocLong(numLayouts)
			for (i in 0..<numLayouts)
			{
				ppLayout.put(i, descSetLayouts[i].vkDescLayout)
			}

			val rendCreateInfo = VkPipelineRenderingCreateInfo.calloc(stack)
				.`sType$Default`()
				.colorAttachmentCount(1)
				.pColorAttachmentFormats(stack.mallocInt(1).put(0, buildInfo.colorFormat))
			if (ds != null)
			{
				rendCreateInfo.depthAttachmentFormat(buildInfo.depthFormat)
			}

			val pPipelineLayoutCreateInfo = VkPipelineLayoutCreateInfo.calloc(stack)
				.`sType$Default`()
				.pSetLayouts(ppLayout)
				.pPushConstantRanges(vpcr)

			gpuCheck(
				vkCreatePipelineLayout(device.vkDevice, pPipelineLayoutCreateInfo, null, lp),
				"Failed to create pipeline layout"
			)
			vkPipelineLayout = lp.get(0)

			val createInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
				.`sType$Default`()
				.renderPass(VK_NULL_HANDLE)
				.pStages(shaderStages)
				.pVertexInputState(buildInfo.vi)
				.pInputAssemblyState(assemblyStateCreateInfo)
				.pViewportState(viewportStateCreateInfo)
				.pRasterizationState(rasterizationStateCreateInfo)
				.pColorBlendState(colorBlendState)
				.pMultisampleState(multisampleStateCreateInfo)
				.pDynamicState(dynamicStateCreateInfo)
				.layout(vkPipelineLayout)
				.pNext(rendCreateInfo)
			if (ds != null)
			{
				createInfo.pDepthStencilState(ds)
			}

			gpuCheck(
				vkCreateGraphicsPipelines(
					device.vkDevice,
					vkCtx.pipelineCache.vkPipelineCache,
					createInfo,
					null,
					lp
				),
				"Error creating graphics pipeline"
			)
			vkPipeline = lp.get(0)
		}
	}

	fun cleanup(vkCtx: Renderer)
	{
//		Main.logDebug("Destroying pipeline")
		val vkDevice = vkCtx.vkDevice
		vkDestroyPipelineLayout(vkDevice, vkPipelineLayout, null)
		vkDestroyPipeline(vkDevice, vkPipeline, null)
	}

	class Info(
		val colorFormat: Int,
		val shaderModules: List<ShaderModule>,
		val vi: VkPipelineVertexInputStateCreateInfo,
		val depthFormat: Int = VK_FORMAT_UNDEFINED,
		var pushConstRange: List<Triple<Int, Int, Int>> = emptyList(),
		var descriptorSetLayouts: List<DescriptorLayout> = emptyList(),
	)
}