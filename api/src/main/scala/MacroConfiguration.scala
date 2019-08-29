package reactivemongo.api.bson

/** Macro configuration */
sealed trait MacroConfiguration {
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

object MacroConfiguration {
  type Aux[O <: MacroOptions] = MacroConfiguration { type Opts = O }

  val defaultDiscriminator = "className"

  /**
   * @param naming the naming strategy
   * @param discriminator See [[MacroConfiguration.discriminator]]
   * @param typeNaming See [[MacroConfiguration.typeNaming]]
   */
  def apply[Opts <: MacroOptions](
    fieldNaming: FieldNaming = FieldNaming.Identity,
    discriminator: String = defaultDiscriminator,
    typeNaming: TypeNaming = TypeNaming.FullName)(implicit opts: MacroOptions.ValueOf[Opts]): MacroConfiguration.Aux[Opts] = new Impl(opts, fieldNaming, discriminator, typeNaming)

  /** Default configuration instance */
  implicit def default[Opts <: MacroOptions: MacroOptions.ValueOf]: MacroConfiguration.Aux[Opts] = apply()

  /** Configuration using [[TypeNaming$.SimpleName]] */
  @inline def simpleTypeName[Opts <: MacroOptions: MacroOptions.ValueOf]: MacroConfiguration.Aux[Opts] = apply(typeNaming = TypeNaming.SimpleName)

  private final class Impl[O <: MacroOptions](
    val options: MacroOptions.ValueOf[O],
    val fieldNaming: FieldNaming,
    val discriminator: String,
    val typeNaming: TypeNaming) extends MacroConfiguration {
    type Opts = O
  }

  protected def default: MacroConfiguration.Aux[MacroOptions] = apply()
}
