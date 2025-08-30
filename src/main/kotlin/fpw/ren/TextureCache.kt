package fpw.ren

import fpw.Image
import fpw.Main
import fpw.ren.gpu.GPUCommandBuffer
import fpw.ren.gpu.GPUCommandPool
import fpw.ren.gpu.GPUCommandQueue
import fpw.ren.gpu.GPUContext
import org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_SRGB
import java.util.*
import kotlin.io.path.Path


class TextureCache
{
	private val textureMap = mutableMapOf<String, Texture>()

	fun addTexture(vkCtx: GPUContext, id: String, srcImage: Image, format: Int): Texture
	{
		return textureMap.getOrPut(id) {
			Texture(vkCtx, id, srcImage, format)
		}
	}

	fun addTexture (vkCtx: GPUContext, id: String, texturePath: String, format: Int): Texture?
	{
		try
		{
			val srcImage = Image.load(Path(texturePath))
			return addTexture(vkCtx, id, srcImage, format)
		}
		catch (e: Exception)
		{
			Main.logError(e) {"Could not load texture [$texturePath]" }
			return null
		}
	}

	fun cleanup(vkCtx: GPUContext)
	{
		textureMap.forEach { (k, t) -> t.cleanup(vkCtx) }
		textureMap.clear()
	}

	fun getAsList () = textureMap.values.toList()

	fun getTexture(texturePath: String): Texture
	{
		return textureMap[texturePath]!!
	}

	fun transitionTexts(vkCtx: GPUContext, cmdPool: GPUCommandPool, queue: GPUCommandQueue)
	{
//		Logger.debug("Recording texture transitions")
		val numTextures = textureMap.size
		val numPaddingTexts = numTextures
		val defaultTexturePath =  "resources/assets/image/"
		for (i in 0..<numPaddingTexts)
		{
			addTexture(vkCtx, UUID.randomUUID().toString(), defaultTexturePath, VK_FORMAT_R8G8B8A8_SRGB)
		}
		val cmdBuf = GPUCommandBuffer(vkCtx, cmdPool, primary = true, oneTimeSubmit = true)
		cmdBuf.record {
			textureMap.forEach { (k, v) -> v.recordTextureTransition(cmdBuf) }
		}
		cmdBuf.submitAndWait(vkCtx, queue)
		cmdBuf.cleanup(vkCtx, cmdPool)
		textureMap.forEach { (k, v) -> v.cleanupStgBuffer(vkCtx) }
//		Logger.debug("Recorded texture transitions")
	}

}