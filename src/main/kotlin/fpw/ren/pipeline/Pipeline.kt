package fpw.ren.pipeline

import fpw.ren.GPUtil
import fpw.ren.GPUtil.longs
import fpw.ren.Renderer
import fpw.ren.ShaderModule
import fpw.ren.descriptor.DescSetLayout
import fpw.ren.enums.CompareOperation
import fpw.ren.enums.CullingMode
import fpw.ren.enums.DynamicStates
import fpw.ren.enums.PolygonMode
import fpw.ren.enums.Pr
import fpw.ren.enums.VkFormat
import fpw.ren.enums.WindingOrder
import fpw.ren.model.VertexFormat
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VK10.VK_SAMPLE_COUNT_1_BIT
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo
import org.lwjgl.vulkan.VkPipelineRenderingCreateInfo
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo

class Pipeline (
	val renderer: Renderer,
	colorFormat: VkFormat,
	shaderModules: List<ShaderModule>,
	vertexFormat: VertexFormat,
	depthFormat: VkFormat = VkFormat.UNDEFINED,
	pushConstRange: List<Triple<Int, Int, Int>> = emptyList(),
	descriptorSetLayouts: List<DescSetLayout> = emptyList(),
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

			val pl = PipelineBuilder {

				primitiveType(Pr.TriangleList)
				viewportCount(1)
				scissorCount(1)
				polygonMode(PolygonMode.Filled)
				culling(CullingMode.Back, WindingOrder.Clockwise)
				lineSize(1)

				dynamicStates(
					DynamicStates.Viewport,
					DynamicStates.Scissor,
				)

				colorWriteEnable(r=true, g=true, b=true, a=true)
				blendingEnabled(false)

				zTestEnabled(true)
				zWriteEnabled(true)
				zCompareOperation(CompareOperation.LessThanOrEqual)
				stencilEnabled(false)

				depthStencilState.depthBoundsTestEnable(false)
				colorBlendState.pAttachments(blendAttachState)
				multisampleState.rasterizationSamples(VK_SAMPLE_COUNT_1_BIT)
			}

			val ds = if (!depthFormat.isUndefined)
			{
				pl.depthStencilState
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
				descriptorSetLayouts[it].vk
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
			createInfo.pInputAssemblyState(pl.assemblyState)
			createInfo.pViewportState(pl.viewportState)
			createInfo.pRasterizationState(pl.rasterState)
			createInfo.pColorBlendState(pl.colorBlendState)
			createInfo.pMultisampleState(pl.multisampleState)
			createInfo.pDynamicState(pl.dynamicState)
			createInfo.layout(vkPipelineLayout)

			val rendCreateInfo = VkPipelineRenderingCreateInfo.calloc(stack)
			rendCreateInfo.`sType$Default`()
			rendCreateInfo.colorAttachmentCount(1)
			rendCreateInfo.pColorAttachmentFormats(stack.ints(colorFormat.vk))
			if (ds != null)
			{
				rendCreateInfo.depthAttachmentFormat(depthFormat.vk)
				createInfo.pDepthStencilState(ds)
			}
			createInfo.pNext(rendCreateInfo)

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