package com.catsofwar

interface GameLogic: AutoCloseable
{
	fun init(context: EngineContext)
	fun input(context: EngineContext, diffTimeMillis: Long)
	fun update(context: EngineContext, diffTimeMillis: Long)
}