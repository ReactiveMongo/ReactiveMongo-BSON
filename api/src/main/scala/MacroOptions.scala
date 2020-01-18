package reactivemongo.api.bson

/**
 * Macros with 'Opts' suffix will take additional options in the form of
 * type parameters that will customize behaviour of
 * the macros during compilation.
 *
 * {{{
 * import reactivemongo.api.bson.{ BSONDocumentWriter, Macros, MacroOptions }
 *
 * case class Bar(score: Float)
 *
 * val w: BSONDocumentWriter[Bar] =
 *   Macros.using[MacroOptions.Default].writer[Bar]
 * }}}
 */
sealed trait MacroOptions

/**
 * [[MacroOptions]] factories & utilities.
 */
object MacroOptions {
  /**
   * The default options that are implied if invoking "non-Opts" method.
   * All other options extend this.
   */
  trait Default extends MacroOptions

  /**
   * The options to print out generated code during compilation.
   *
   * {{{
   * import reactivemongo.api.bson.{ BSONDocumentWriter, Macros, MacroOptions }
   *
   * case class Bar(score: Float)
   *
   * val w: BSONDocumentWriter[Bar] =
   *   Macros.using[MacroOptions.Verbose].writer[Bar]
   * }}}
   */
  trait Verbose extends Default

  /** The options to disable compilation warnings. */
  trait DisableWarnings extends Default

  /**
   * This allows to restrict the handling of family
   * to only some subtypes (not required to be sealed in this case).
   *
   * {{{
   * import reactivemongo.api.bson.{
   *   BSONDocumentWriter, Macros, MacroOptions
   * }, MacroOptions.\/
   *
   * trait Family
   * case class TypeA(n: Int) extends Family
   * case class TypeB(s: String) extends Family
   * case class TypeC(f: Float) extends Family
   *
   * val writer: BSONDocumentWriter[Family] = {
   *   implicit val a = Macros.writer[TypeA]
   *   implicit val b = Macros.writer[TypeB]
   *
   *   Macros.using[MacroOptions.UnionType[TypeA \/ TypeB]].writer[Family]
   * }
   * }}}
   *
   * @tparam Types to restrict the subtypes to handle
   * @see \/
   */
  trait UnionType[Types <: \/[_, _]] extends Default

  /**
   * Type for making type-level lists for UnionType.
   * If second parameter is another \/ it will be flattend out into a list
   * and so on. Using infix notation makes much more sense since it then
   * looks like a logical disjunction.
   *
   * `Foo \/ Bar \/ Baz` is interpreted as type Foo or type Bar or type Baz
   *
   * @see UnionType
   */
  @SuppressWarnings(Array("ClassNames"))
  trait \/[A, B]

  /**
   * For a sealed family (all implementations of a sealed trait
   * or defined explicit union types), this option enables the automatic
   * materialization of handlers for the member types.
   *
   * If used, make sure it cannot lead to type recursion issue
   * (reason why it's not disabled by default).
   *
   * {{{
   * import reactivemongo.api.bson.{ BSONDocumentReader, Macros, MacroOptions }
   *
   * sealed trait Family
   * case class TypeA(n: Int) extends Family
   * case class TypeB(s: String) extends Family
   * case class TypeC(f: Float) extends Family
   *
   * val reader: BSONDocumentReader[Family] =
   *   Macros.using[MacroOptions.AutomaticMaterialization].reader[Family]
   *   // Automatically/internally materializes the readers for Type{A,B,C}
   * }}}
   */
  trait AutomaticMaterialization extends Default

  // ---

  trait ValueOf[O <: MacroOptions]

  trait LowPriorityValueOfImplicits {
    /**
     * Low priority implicit used when some explicit MacroOptions
     * instance is passed.
     */
    implicit def lowPriorityDefault[O <: MacroOptions]: ValueOf[O] =
      new ValueOf[O] {}
  }

  object ValueOf extends LowPriorityValueOfImplicits {

    /**
     * This will be the default that's passed when no MacroOptions is specified.
     */
    implicit object optionsDefault extends ValueOf[MacroOptions]
  }
}
