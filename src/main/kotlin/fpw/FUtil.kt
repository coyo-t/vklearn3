package fpw

import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.println
import kotlin.use


object FUtil
{
	val RESOURCES_PATH = Path("./resources/").normalize().toAbsolutePath()
	val ASSETS_PATH = RESOURCES_PATH/"assets/"

	private fun memToBuffer (m: MemorySegment)
		= m.asByteBuffer().order(ByteOrder.nativeOrder())

	fun createBuffer (sz: Long): ByteBuffer
	{
		return memToBuffer(createMemory(sz))
	}

	fun createBuffer (sz: Int) = createBuffer(sz.toLong())

	fun createMemoryAt (addr: Long, size: Long): MemorySegment
	{
		return MemorySegment.ofAddress(addr).reinterpret(size)
	}

	fun createMemory (sz: Long): MemorySegment
	{
		return Arena.ofAuto().allocate(sz)
	}

	fun createMemory (sz: Int) = createMemory(sz.toLong())

	fun getFileBytes (at: Path): ByteBuffer
	{
		FileChannel.open(at, StandardOpenOption.READ).use { f ->
			return createBuffer(f.size()).apply {
				f.read(this)
				flip()
			}
		}
	}

	object ANSI {
		const val RESET = "\u001B[0m"

		const val BLACK = "\u001B[30m"
		const val RED = "\u001B[31m"
		const val GREEN = "\u001B[32m"
		const val YELLOW = "\u001B[33m"
		const val BLUE = "\u001B[34m"
		const val PURPLE = "\u001B[35m"
		const val CYAN = "\u001B[36m"
		const val WHITE = "\u001B[37m"

		const val BLACK_BACKGROUND = "\u001B[40m"
		const val RED_BACKGROUND = "\u001B[41m"
		const val GREEN_BACKGROUND = "\u001B[42m"
		const val YELLOW_BACKGROUND = "\u001B[43m"
		const val BLUE_BACKGROUND = "\u001B[44m"
		const val PURPLE_BACKGROUND = "\u001B[45m"
		const val CYAN_BACKGROUND = "\u001B[46m"
		const val WHITE_BACKGROUND = "\u001B[47m"
	}

	fun logInfo (f:String, vararg args:Any?)
	{
		println("${ANSI.GREEN}ovo${ANSI.RESET} $f".format(*args))
	}

	fun logWarn (f:String, vararg args:Any?)
	{
		println("${ANSI.YELLOW}v_v $f${ANSI.RESET}".format(*args))
	}

	fun logDebug (f:String, vararg args:Any?)
	{
		println("${ANSI.BLUE}._.${ANSI.RESET} $f".format(*args))
	}

	fun logError (f:String, vararg args:Any?)
	{
		println("${ANSI.RED}x_x $f${ANSI.RESET}".format(*args))
	}

	fun logError (t: Throwable, k:()->String)
	{
		println(
			"${ANSI.RED}x_x ${k.invoke()}\n" +
			t.stackTraceToString() +
			ANSI.RESET
		)
	}
}