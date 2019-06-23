package reactivemongo.api.bson

import scala.util.Success

/**
 * Macros for generating `BSONReader` and `BSONWriter` implementations for case
 * at compile time. Invoking these macros is equivalent to writing anonymous
 * class implementations by hand.
 *
 * {{{
 * case class Person(name: String, surname: String)
 * implicit val personHandler = Macros.handler[Person]
 * }}}
 *
 * @see Macros.Options for specific options
 */
object Macros {
  import language.experimental.macros

  /**
   * Creates an instance of BSONReader for case class A
   * @see Macros
   */
  def reader[A]: BSONDocumentReader[A] = macro MacroImpl.reader[A, Options.Default]

  /** Creates an instance of BSONReader for case class A and takes additional options */
  def readerOpts[A, Opts <: Options.Default]: BSONDocumentReader[A] = macro MacroImpl.reader[A, Opts]

  /** Creates an instance of BSONWriter for case class A */
  def writer[A]: BSONDocumentWriter[A] = macro MacroImpl.writer[A, Options.Default]

  /** Creates an instance of BSONWriter for case class A and takes additional options */
  def writerOpts[A, Opts <: Options.Default]: BSONDocumentWriter[A] = macro MacroImpl.writer[A, Opts]

  /** Creates an instance of BSONReader and BSONWriter for case class A */
  def handler[A]: BSONDocumentHandler[A] = macro MacroImpl.handler[A, Options.Default]

  /**
   * Creates an instance of BSONReader and BSONWriter for case class A,
   * and takes additional options.
   */
  def handlerOpts[A, Opts <: Options.Default]: BSONDocumentHandler[A] = macro MacroImpl.handler[A, Opts]

  /**
   * Methods with 'Opts' postfix will take additional options in the form of
   * type parameters that will customize behaviour of
   * the macros during compilation.
   */
  object Options {

    /**
     * The default options that are implied if invoking "non-Opts" method.
     * All other options extend this.
     */
    trait Default

    /** Print out generated code during compilation. */
    trait Verbose extends Default

    /**
     * Uses the class simple name (i.e. Not the fully-qualified name).
     */
    trait SaveSimpleName extends Default

    /**
     * Use type parameter `A` as static type but use pattern matching to handle
     * different possible subtypes. This makes it easy to persist algebraic
     * data types(pattern where you have a sealed trait and several implementing
     * case classes). When writing a case class into BSON its dynamic type
     * will be pattern matched, when reading from BSON the pattern matching
     * will be done on the `className` string.
     *
     * @tparam Types to use in pattern matching. Listed in a "type list" \/
     */
    trait UnionType[Types <: \/[_, _]] extends Default

    /**
     * Same as [[UnionType]] but saving the class’ simple name io. the
     * fully-qualified name.
     * @tparam Types to use in pattern matching. Listed in a "type list" \/
     */
    trait SimpleUnionType[Types <: \/[_, _]]
      extends UnionType[Types] with SaveSimpleName with Default

    /**
     * Type for making type-level lists for UnionType.
     * If second parameter is another \/ it will be flattend out into a list
     * and so on. Using infix notation makes much more sense since it then
     * looks like a logical disjunction.
     *
     * `Foo \/ Bar \/ Baz` is interpreted as type Foo or type Bar or type Baz
     */
    @SuppressWarnings(Array("ClassNames"))
    trait \/[A, B]

    /**
     * For a sealed family (all implementations of a sealed trait
     * or defined explicit union types), this option enables the automatic
     * materialization of handlers for the member types.
     *
     * If used, make sure it cannot lead to type recursion issue
     * (disabled by default).
     */
    trait AutomaticMaterialization extends Default
  }

  /** Annotations to use on case classes that are being processed by macros. */
  object Annotations {
    import scala.annotation.{ StaticAnnotation, meta }

    /**
     * Specify a key different from field name in your case class.
     * Convenient to use when you'd like to leverage mongo's `_id` index
     * but don't want to actually use `_id` in your code.
     *
     * {{{
     * case class Website(@Key("_id") url: String)
     * }}}
     *
     * Generated handler will map the `url` field in your code
     * to `_id` field in BSON
     *
     * @param key the desired key to use in BSON
     */
    @meta.param
    case class Key(key: String) extends StaticAnnotation

    /** Ignores a field */
    @meta.param
    final class Ignore extends StaticAnnotation {
      override def hashCode: Int = 1278101060

      override def equals(that: Any): Boolean = that match {
        case _: Ignore => true
        case _ => false
      }
    }

    /**
     * Indicates that if a property is represented as a document itself,
     * the document fields are directly included in top document,
     * rather than nesting it.
     *
     * {{{
     * case class Range(start: Int, end: Int)
     *
     * case class LabelledRange(
     *   name: String,
     *   @Flatten range: Range)
     *
     * // Flattened
     * BSONDocument("name" -> "foo", "start" -> 0, "end" -> 1)
     *
     * // Rather than:
     * // BSONDocument("name" -> "foo", "range" -> BSONDocument(
     * //   "start" -> 0, "end" -> 1))
     * }}}
     */
    @meta.param
    final class Flatten extends StaticAnnotation {
      override def hashCode: Int = 488571557

      override def equals(that: Any): Boolean = that match {
        case _: Flatten => true
        case _ => false
      }
    }

    /**
     * Indicates that if an `Option` property is empty,
     * it will be represented by `BSONNull` rather than being omitted.
     *
     * {{{
     * case class Foo(
     *   title: String,
     *   @NoneAsNull description: Option[String])
     * }}}
     */
    @meta.param
    final class NoneAsNull extends StaticAnnotation {
      override def hashCode: Int = 1667526726

      override def equals(that: Any): Boolean = that match {
        case _: NoneAsNull => true
        case _ => false
      }
    }
  }

  /** Only for internal purposes */
  final class Placeholder private () {}

  /** Only for internal purposes */
  object Placeholder {
    private val instance = new Placeholder()

    implicit object Handler
      extends BSONDocumentReader[Placeholder]
      with BSONDocumentWriter[Placeholder]
      with BSONHandler[Placeholder] {

      def readDocument(bson: BSONDocument) = Success(instance)

      @com.github.ghik.silencer.silent
      def writeTry(pl: Placeholder) = Success(BSONDocument.empty)
    }
  }

  /** Only for internal purposes */
  final class LocalVar[@specialized T] {
    private var underlying: T = _

    def take(value: T): LocalVar[T] = {
      underlying = value
      this
    }

    def value(): T = underlying
  }

  /**
   * Naming strategy, to map each class property to the corresponding column.
   */
  trait ColumnNaming extends (String => String) {
    /**
     * Returns the column name for the class property.
     *
     * @param property the name of the case class property
     */
    def apply(property: String): String
  }

  /** Naming companion */
  object ColumnNaming {
    /** Keep the original property name. */
    object Identity extends ColumnNaming {
      def apply(property: String) = property
    }

    /**
     * For each class property, use the snake case equivalent
     * to name its column (e.g. fooBar -> foo_bar).
     */
    object SnakeCase extends ColumnNaming {
      private val re = "[A-Z]+".r

      def apply(property: String): String =
        re.replaceAllIn(property, { m => s"_${m.matched.toLowerCase}" })
    }

    /** Naming using a custom transformation function. */
    def apply(transformation: String => String): ColumnNaming =
      new ColumnNaming {
        def apply(property: String): String = transformation(property)
      }
  }

  trait Discriminate extends (String => String) {
    /**
     * Returns the value representing the specified type,
     * to be used as a discriminator within a sealed family.
     *
     * @param tname the name of type (class or object) to be discriminated
     */
    def apply(tname: String): String
  }

  object Discriminate {
    sealed class Function(f: String => String) extends Discriminate {
      def apply(tname: String) = f(tname)
    }

    /** Uses the type name as-is as value for the discriminator */
    object Identity extends Function(identity[String])

    /** Returns a `Discriminate` function from any `String => String`. */
    def apply(discriminate: String => String): Discriminate =
      new Function(discriminate)
  }

  trait DiscriminatorNaming extends (String => String) {
    /**
     * Returns the name for the discriminator column.
     * @param familyType the name of the famility type (sealed trait)
     */
    def apply(familyType: String): String
  }

  object DiscriminatorNaming {
    sealed class Function(f: String => String) extends DiscriminatorNaming {
      def apply(familyType: String) = f(familyType)
    }

    /** Always use "classname" as name for the discriminator column. */
    object Default extends Function(_ => "classname")

    /** Returns a naming according from any `String => String`. */
    def apply(naming: String => String): DiscriminatorNaming =
      new Function(naming)
  }
}
