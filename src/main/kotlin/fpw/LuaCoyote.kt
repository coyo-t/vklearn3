package fpw
import fpw.FUtil.ASSETS_PATH
import party.iroiro.luajava.lua54.Lua54
import party.iroiro.luajava.value.LuaValue
import kotlin.io.path.div

class LuaCoyote: Lua54
{
	constructor(): super()

	constructor (init:LuaCoyote.()-> Unit): super()
	{
		init.invoke(this)
	}

	fun run (r: ResourceLocation): LuaValue
	{
		run(FUtil.getFileBytes(ASSETS_PATH/r.path), "")
		return get()
	}
}