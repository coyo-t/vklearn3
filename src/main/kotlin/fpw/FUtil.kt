package fpw

import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
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
		return createMemory(sz).asByteBuffer().apply {
			order(ByteOrder.nativeOrder())
		}
	}

	fun createBuffer (sz: Int) = createBuffer(sz.toLong())

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
}