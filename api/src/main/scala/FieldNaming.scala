package reactivemongo.api.bson

/**
 * Naming strategy, to map each class property to the corresponding field.
 *
 * {{{
 * import reactivemongo.api.bson.{ FieldNaming, MacroConfiguration }
 *
 * def initCfg(naming: FieldNaming): MacroConfiguration =
 *   MacroConfiguration(fieldNaming = naming)
 * }}}
 *
 * @see [[MacroConfiguration]]
 */
trait FieldNaming extends (String => String) {
  /**
   * Returns the field name for the class property.
   *
   * @param property the name of the case class property
   */
  def apply(property: String): String
}

/** Naming companion */
object FieldNaming {

  /**
   * For each class property, use the name
   * as is for its field (e.g. fooBar -> fooBar).
   *
   * {{{
   * import reactivemongo.api.bson.{ FieldNaming, MacroConfiguration }
   *
   * val cfg: MacroConfiguration =
   *   MacroConfiguration(fieldNaming = FieldNaming.Identity)
   * }}}
   */
  object Identity extends FieldNaming {
    def apply(property: String): String = property
    override val toString = "Identity"
  }

  /**
   * For each class property, use the snake case equivalent
   * to name its field (e.g. fooBar -> foo_bar).
   *
   * {{{
   * import reactivemongo.api.bson.{ FieldNaming, MacroConfiguration }
   *
   * val cfg: MacroConfiguration =
   *   MacroConfiguration(fieldNaming = FieldNaming.SnakeCase)
   * }}}
   */
  object SnakeCase extends FieldNaming {
    def apply(property: String): String = {
      val length = property.length
      val result = new StringBuilder(length * 2)
      var resultLength = 0
      var wasPrevTranslated = false

      for (i <- 0 until length) {
        var c = property.charAt(i)
        if (i > 0 || i != '_') {
          if (Character.isUpperCase(c)) {
            // append a underscore if the previous result wasn't translated
            if (!wasPrevTranslated && resultLength > 0 && result.charAt(resultLength - 1) != '_') {
              result.append('_')
              resultLength += 1
            }
            c = Character.toLowerCase(c)
            wasPrevTranslated = true
          } else {
            wasPrevTranslated = false
          }
          result.append(c)
          resultLength += 1
        }
      }

      // builds the final string
      result.toString()
    }

    override val toString = "SnakeCase"
  }

  /**
   * For each class property, use the pascal case equivalent
   * to name its field (e.g. fooBar -> FooBar).
   *
   * {{{
   * import reactivemongo.api.bson.{ FieldNaming, MacroConfiguration }
   *
   * val cfg: MacroConfiguration =
   *   MacroConfiguration(fieldNaming = FieldNaming.PascalCase)
   * }}}
   */
  object PascalCase extends FieldNaming {
    def apply(property: String): String = property.capitalize

    override val toString = "PascalCase"
  }

  /**
   * Naming using a custom transformation function.
   *
   * {{{
   * import reactivemongo.api.bson.{ FieldNaming, MacroConfiguration }
   *
   * def withNaming(f: String => String): MacroConfiguration =
   *   MacroConfiguration(fieldNaming = FieldNaming(f))
   * }}}
   */
  def apply(transformation: String => String): FieldNaming = new FieldNaming {
    def apply(property: String): String = transformation(property)
  }
}
