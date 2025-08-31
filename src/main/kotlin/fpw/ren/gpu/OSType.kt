package fpw.ren.gpu

enum class OSType
{
	WINDOWZ,
	LINUXZ,
	MACINTOSHZ,
	SOLARIZ,
	DUDE_IDFK,
	;

	companion object
	{
		val isMacintosh
			get() = get() == MACINTOSHZ

		fun get (): OSType
		{
			val os = System.getProperty("os.name", "generic").lowercase()
			if ((os.indexOf("mac") >= 0) || (os.indexOf("darwin") >= 0))
			{
				return MACINTOSHZ
			}
			else if (os.indexOf("win") >= 0)
			{
				return WINDOWZ
			}
			else if (os.indexOf("nux") >= 0)
			{
				return LINUXZ
			}
			return DUDE_IDFK
		}
	}
}