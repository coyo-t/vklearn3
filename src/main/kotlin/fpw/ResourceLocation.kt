package fpw

class ResourceLocation
private constructor (
	val namespace: String,
	val path: String,
)
{

	private val _hash = namespace.hashCode() * 292202 + path.hashCode()


	override fun hashCode () = _hash

	override fun equals(other: Any?): Boolean
	{
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as ResourceLocation

		return (
			_hash == other._hash &&
			namespace == other.namespace &&
			path == other.path
		)
	}

	companion object
	{
		var DEFAULT_NAMESPACE = "fpw"

		@JvmStatic
		fun fromParts (ns:String, p:String): ResourceLocation
		{
			return ResourceLocation(ns, p)
		}

		@JvmStatic
		fun withDefaultNameSpace (p:String): ResourceLocation
		{
			return fromParts(DEFAULT_NAMESPACE, p)
		}

		@JvmStatic
		fun tryParse (n:String): ResourceLocation
		{
			val sl = n.split(':')

			if (sl.size == 1)
			{
				return withDefaultNameSpace(sl.first())
			}
			return fromParts(sl[0], sl[1])
		}
	}

}