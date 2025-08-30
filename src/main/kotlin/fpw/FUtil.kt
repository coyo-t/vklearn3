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
	private fun memToBuffer (m: MemorySegment)
		= m.asByteBuffer().order(ByteOrder.nativeOrder())

	fun createBuffer (sz: Long): ByteBuffer
	{
		return memToBuffer(createMemory(sz))
	}

	fun createBuffer (sz: Int) = createBuffer(sz.toLong())

	fun createBufferAt (addr: Long, size: Long)
		= memToBuffer(createMemoryAt(addr, size))

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
}