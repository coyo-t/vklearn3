package fpw.ren

import fpw.FUtil
import fpw.Image
import fpw.Renderer
import fpw.ren.gpu.CommandBuffer
import fpw.ren.gpu.CommandPool
import fpw.ren.gpu.CommandQueue
import org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_SRGB
import java.util.*
import kotlin.io.path.Path


class TextureManager
{
	private val textureMap = mutableMapOf<String, Texture>()

	fun addTexture(vkCtx: Renderer, id: String, srcImage: Image, format: Int): Texture
	{
		return textureMap.getOrPut(id) {
			Texture(vkCtx, id, srcImage, format)
		}
	}

	fun addTexture (vkCtx: Renderer, id: String, texturePath: String, format: Int): Texture?
	{
		try
		{
			val srcImage = Image.load(Path(texturePath))!!
			return addTexture(vkCtx, id, srcImage, format)
		}
		catch (e: Exception)
		{
			FUtil.logError(e) {"Could not load texture [$texturePath]" }
			return null
		}
	}

	fun cleanup(vkCtx: Renderer)
	{
		textureMap.values.forEach { it.cleanup(vkCtx) }
		textureMap.clear()
	}

	fun getTexture(texturePath: String): Texture
	{
		return textureMap[texturePath]!!
	}

	fun transitionTexts(vkCtx: Renderer, cmdPool: CommandPool, queue: CommandQueue)
	{
//		Logger.debug("Recording texture transitions")
		val numTextures = textureMap.size
		val numPaddingTexts = numTextures
		val defaultTexturePath =  "resources/assets/image/"
		for (i in 0..<numPaddingTexts)
		{
			addTexture(vkCtx, UUID.randomUUID().toString(), defaultTexturePath, VK_FORMAT_R8G8B8A8_SRGB)
		}
		val c = CommandBuffer(vkCtx, cmdPool, oneTimeSubmit = true)
		c.beginRecording()
		textureMap.values.forEach { it.recordTextureTransition(c) }
		c.endRecording()
		c.submitAndWait(vkCtx, queue)
		c.free(vkCtx, cmdPool)
		textureMap.values.forEach { it.cleanupStgBuffer(vkCtx) }
	}

}