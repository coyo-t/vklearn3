import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.use


object FUtil
{

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