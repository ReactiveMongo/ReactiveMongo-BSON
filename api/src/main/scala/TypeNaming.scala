package reactivemongo.api.bson

/**
 * Naming strategy, to map each class to a discriminator value.
 *
 * {{{
 * import reactivemongo.api.bson.{ MacroConfiguration, TypeNaming }
 *
 * val cfg1 = MacroConfiguration(typeNaming = TypeNaming.FullName)
 *
 * val cfg2 = MacroConfiguration(typeNaming = TypeNaming.FullName)
 *
 * val cfg3 = MacroConfiguration(
 *   typeNaming = TypeNaming { cls: Class[_] =>
 *     "_" + cls.getSimpleName
 *   })
 * }}}
 */
trait TypeNaming extends (Class[_] => String) {
  /**
   * Returns the name for the given type.
   *
   * {{{
   * import scala.reflect.ClassTag
   *
   * import reactivemongo.api.bson.TypeNaming
   *
   * def foo[T](n: TypeNaming)(implicit ct: ClassTag[T]): String =
   *   n(ct.runtimeClass)
   * }}}
   */
  def apply(tpe: Class[_]): String

  /**
   * Composes the naming with the given function `f`,
   * applied on the first result.
   *
   * {{{
   * import reactivemongo.api.bson.TypeNaming
   *
   * val tpeNaming: TypeNaming =
   *   TypeNaming.SimpleName.andThen(_.toLowerCase)
   * }}}
   */
  def andThen(f: String => String): TypeNaming =
    TypeNaming(super.andThen[String](f))
}

/** [[TypeNaming]] factories */
object TypeNaming {
  private val separators = Array[Char]('$', '.')

  /**
   * Uses the class fully qualified name (e.g. `java.lang.String`).
   *
   * The package naming must be stable (or would require data migrations).
   */
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

  /**
   * Creates a type naming according the given function.
   *
   * {{{
   * import reactivemongo.api.bson.{ MacroConfiguration, TypeNaming }
   *
   * val configWithCustomNaming = MacroConfiguration(
   *   typeNaming = TypeNaming { cls: Class[_] =>
   *     "custom:" + cls.getSimpleName
   *   })
   * }}}
   */
  def apply(f: Class[_] => String): TypeNaming = new TypeNaming {
    def apply(tpe: Class[_]): String = f(tpe)
  }
}
