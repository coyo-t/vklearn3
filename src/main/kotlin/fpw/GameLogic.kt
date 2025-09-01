package fpw

interface GameLogic: AutoCloseable
{
	fun init(context: Engine): InitData
	fun input(context: Engine, diffTimeMillis: Long)
	fun update(context: Engine, diffTimeMillis: Long)
}