package fpw

import java.lang.foreign.Arena
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.use


object FUtil
{
	fun createBuffer (sz: Long): ByteBuffer
	{
		return Arena.ofAuto().allocate(sz).asByteBuffer().apply {
			order(ByteOrder.nativeOrder())
		}
	}

	fun getFileBytes (at: Path): ByteBuffer
	{
		FileChannel.open(at, StandardOpenOption.READ).use { f ->
			return ByteBuffer.allocateDirect(f.size().toInt()).order(ByteOrder.nativeOrder()).apply {
				f.read(this)
				flip()
			}
		}
	}
}