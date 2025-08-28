package fpw
import party.iroiro.luajava.lua54.Lua54

class LuaCoyote: Lua54
{
	constructor(): super()

	constructor (init:LuaCoyote.()-> Unit): super()
	{
		init.invoke(this)
	}
}