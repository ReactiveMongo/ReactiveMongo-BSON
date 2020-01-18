package reactivemongo.api.bson

/**
 * Macro configuration;
 *
 * It allows to configure compile time options,
 * and behaviour to be retained at runtime (field & type naming).
 *
 * {{{
 * import reactivemongo.api.bson.{
 *   BSONDocumentReader, MacroConfiguration, Macros
 * }
 *
 * case class Foo(name: String)
 *
 * val r1: BSONDocumentReader[Foo] = Macros.configured.reader[Foo]
 *
 * val r2: BSONDocumentReader[Foo] = Macros.configured(
 *   MacroConfiguration.simpleTypeName).reader[Foo]
 *
 * }}}
 */
sealed trait MacroConfiguration { // TODO: Link to reactivemongo-site when BSON doc is up to date
  /** Compile-time options for the JSON macros */
  type Opts <: MacroOptions

  /** Naming strategy for fields */
  def fieldNaming: FieldNaming

  /** Naming strategy for type names */
  def typeNaming: TypeNaming

  /**
   * Name of the type discriminator field
   * (for sealed family; see [[MacroConfiguration$.defaultDiscriminator]])
   */
  def discriminator: String
}

/** [[MacroConfiguration]] factories and utilities */
object MacroConfiguration {
  type Aux[O <: MacroOptions] = MacroConfiguration { type Opts = O }

  /** `"className"` */
  val defaultDiscriminator = "className"

  /**
   * {{{
   * import reactivemongo.api.bson.MacroConfiguration
   *
   * val customCfg = MacroConfiguration(discriminator = "_type")
   * }}}
   *
   * @param naming the naming strategy
   * @param discriminator See [[MacroConfiguration.discriminator]]
   * @param typeNaming See [[MacroConfiguration.typeNaming]]
   * @tparam Opts the compile time options (see [[MacroOptions]])
   */
  def apply[Opts <: MacroOptions](
    fieldNaming: FieldNaming = FieldNaming.Identity,
    discriminator: String = defaultDiscriminator,
    typeNaming: TypeNaming = TypeNaming.FullName)(implicit opts: MacroOptions.ValueOf[Opts]): MacroConfiguration.Aux[Opts] = new Impl(opts, fieldNaming, discriminator, typeNaming)

  /** The default configuration instance */
  implicit def default[Opts <: MacroOptions: MacroOptions.ValueOf]: MacroConfiguration.Aux[Opts] = apply()

  /** A configuration using [[TypeNaming$.SimpleName]] */
  @inline def simpleTypeName[Opts <: MacroOptions: MacroOptions.ValueOf]: MacroConfiguration.Aux[Opts] = apply(typeNaming = TypeNaming.SimpleName)

  private final class Impl[O <: MacroOptions](
    val options: MacroOptions.ValueOf[O],
    val fieldNaming: FieldNaming,
    val discriminator: String,
    val typeNaming: TypeNaming) extends MacroConfiguration {
    type Opts = O
  }

  //protected def default: MacroConfiguration.Aux[MacroOptions] = apply()
}
