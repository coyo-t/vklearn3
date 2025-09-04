package fpw.ren.texture

import fpw.FUtil
import fpw.Image
import fpw.ResourceLocation
import fpw.ren.Renderer
import fpw.ren.command.CommandBuffer
import fpw.ren.command.CommandPool
import fpw.ren.command.CommandSequence
import org.lwjgl.vulkan.VK10
import kotlin.io.path.div

class TextureManager (val renderer: Renderer)
{
	private val textureMap = mutableMapOf<String, Texture>()

	operator fun get (from: ResourceLocation): Texture
	{
		val p = from.path
		if (p in textureMap)
		{
			return textureMap[p]!!
		}

		try
		{
			val srcImage = Image.Companion.load(FUtil.ASSETS_PATH / p)!!
			val outs = Texture(renderer, p, srcImage, VK10.VK_FORMAT_R8G8B8A8_SRGB)
			textureMap[p] = outs
			uploadTextures(
				renderer.currentSwapChainDirector.commandPool,
				renderer.graphicsQueue,
				outs,
			)
			return outs
		}
		catch (e: Exception)
		{
			FUtil.logError(e) { "Could not load texture [$from]" }
			throw e
		}
	}

	fun free ()
	{
		textureMap.values.forEach { it.free() }
		textureMap.clear()
	}

	fun uploadTextures (cmd: CommandPool, queue: CommandSequence, vararg textures: Texture)
	{
		val c = CommandBuffer(renderer, cmd, oneTimeSubmit = true)
		c.beginRecording()
		for (it in textures)
		{
			it.recordTextureTransition(c)
		}
		c.endRecording()
		c.submitAndWait(renderer, queue)
		c.free(renderer, cmd)
		for (it in textures)
		{
			it.cleanupStgBuffer()
		}
	}

//	fun transitionTexts (cmdPool: CommandPool, queue: CommandQueue)
//	{
////		Logger.debug("Recording texture transitions")
////		val numTextures = textureMap.size
////		val numPaddingTexts = numTextures
////		val defaultTexturePath =  "resources/assets/image/"
////		for (i in 0..<numPaddingTexts)
////		{
////			addTexture(UUID.randomUUID().toString(), defaultTexturePath, VK_FORMAT_R8G8B8A8_SRGB)
////		}
//		val c = CommandBuffer(renderer, cmdPool, oneTimeSubmit = true)
//		c.beginRecording()
//		textureMap.values.forEach { it.recordTextureTransition(c) }
//		c.endRecording()
//		c.submitAndWait(renderer, queue)
//		c.free(renderer, cmdPool)
//		textureMap.values.forEach { it.cleanupStgBuffer(renderer) }
//	}

}