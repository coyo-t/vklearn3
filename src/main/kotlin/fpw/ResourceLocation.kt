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

	override fun toString(): String
	{
		return "$namespace:$path"
	}

	companion object
	{
		var DEFAULT_NAMESPACE = "fpw"

		fun fromParts (ns:String, p:String): ResourceLocation
		{
			return ResourceLocation(ns, p)
		}

		/// create a resource location using the default namespace
		fun create (p:String): ResourceLocation
		{
			return fromParts(DEFAULT_NAMESPACE, p)
		}

		fun tryParse (n:String): ResourceLocation
		{
			val sl = n.split(':')

			if (sl.size == 1)
			{
				return create(sl.first())
			}
			return fromParts(sl[0], sl[1])
		}
	}

}