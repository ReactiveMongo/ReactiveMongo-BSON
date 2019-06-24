package reactivemongo.api.bson

/**
 * Naming strategy, to map each class to a discriminator value.
 */
trait TypeNaming extends (Class[_] => String) {
  /**
   * Returns the name for the given type.
   */
  def apply(tpe: Class[_]): String
}

object TypeNaming {
  private val separators = Array[Char]('$', '.')

  /** Uses the class fully qualified name (e.g. `java.lang.String`). */
  object FullName extends TypeNaming {
    def apply(tpe: Class[_]): String =
      tpe.getName.split(separators).mkString(".")
  }

  /** Uses the class simple name (e.g. `String`). */
  object SimpleName extends TypeNaming {
    def apply(tpe: Class[_]): String =
      // Do not use getSimpleName, as buggy with some scala/jvm combination
      tpe.getName.split(separators).lastOption.mkString
  }

  /** Creates a type naming according the given function. */
  def apply(f: Class[_] => String): TypeNaming = new TypeNaming {
    def apply(tpe: Class[_]): String = f(tpe)
  }
}
