package fpw

import org.lwjgl.stb.STBImage.*
import org.lwjgl.system.MemoryStack
import java.lang.foreign.MemorySegment
import java.nio.file.Path
import kotlin.io.path.div

class Image private constructor (
	val wide: Int,
	val tall: Int,
	val data: MemorySegment,
)
{

	companion object
	{
		fun load (p: Path): Image?
		{
			val finp = FUtil.ASSETS_PATH/p
			MemoryStack.stackPush().use { arena ->
				val wp = arena.mallocInt(1)
				val hp = arena.mallocInt(1)
				val cp = arena.mallocInt(1)
				val pp = arena.ASCII(finp.toString())

				val tryRead = stbi_load(
					pp,
					wp,
					hp,
					cp,
					4,
				)
				if (tryRead == null)
				{
					FUtil.logError("couldnt load image @$finp: ${stbi_failure_reason()}")
					return null
				}
				val wide = wp[0]
				val tall = hp[0]
				val chans = cp[0]
				val outData = FUtil.createMemory(wide*tall*chans)
				val adr = MemorySegment.ofBuffer(tryRead).address()
				outData.copyFrom(FUtil.createMemoryAt(adr, outData.byteSize()))
				stbi_image_free(tryRead)
				return Image(
					wide = wide,
					tall = tall,
					data = outData,
				)
			}
		}
	}
}