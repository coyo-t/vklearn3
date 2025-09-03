package fpw.ren

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
		val osType by lazy {
			val os = System.getProperty("os.name", "generic").lowercase()
			when {
				os.indexOf("mac") >= 0 || os.indexOf("darwin") >= 0
					-> MACINTOSHZ
				os.indexOf("win") >= 0
					-> WINDOWZ
				os.indexOf("nux") >= 0
					-> LINUXZ
				else
					-> DUDE_IDFK
			}
		}

		val isMacintosh
			get() = osType == MACINTOSHZ

	}
}