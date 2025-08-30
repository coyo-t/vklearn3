package fpw

import org.lwjgl.stb.STBImage.*
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout.JAVA_INT
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.div

class Image private constructor (
	val wide: Int,
	val tall: Int,
	val data: MemorySegment,
)
{

	companion object
	{
		val cbcb = Path("./resources/assets/").normalize().toAbsolutePath()

		fun load (p: Path): Image
		{
			val finp = cbcb/p
			Arena.ofConfined().use { arena ->
				val wp = arena.allocate(JAVA_INT)
				val hp = arena.allocate(JAVA_INT)
				val cp = arena.allocate(JAVA_INT)
				val pp = arena.allocateFrom(finp.toString(), Charsets.US_ASCII)

				val tryRead = nstbi_load(
					pp.address(),
					wp.address(),
					hp.address(),
					cp.address(),
					4,
				)
				check(tryRead != 0L) {
					"couldnt load image @$finp: ${stbi_failure_reason()}"
				}
				val wide = wp.get(JAVA_INT, 0)
				val tall = hp.get(JAVA_INT, 0)
				val chans = cp.get(JAVA_INT, 0)
				val outData = FUtil.createMemory(wide*tall*chans)
				outData.copyFrom(FUtil.createMemoryAt(tryRead, outData.byteSize()))
				nstbi_image_free(tryRead)
				return Image(
					wide = wide,
					tall = tall,
					data = outData,
				)
			}
		}
	}
}