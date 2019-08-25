package reactivemongo.api.bson

/**
 * Macros with 'Opts' suffix will take additional options in the form of
 * type parameters that will customize behaviour of
 * the macros during compilation.
 */
sealed trait MacroOptions

object MacroOptions {
  /**
   * The default options that are implied if invoking "non-Opts" method.
   * All other options extend this.
   */
  trait Default extends MacroOptions

  /** Print out generated code during compilation. */
  trait Verbose extends Default

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
