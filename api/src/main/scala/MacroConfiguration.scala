package reactivemongo.api.bson

/** Macro configuration */
sealed trait MacroConfiguration {
  /** Compile-time options for the JSON macros */
  type Opts <: Macros.Options

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
  type Aux[O <: Macros.Options] = MacroConfiguration { type Opts = O }

  val defaultDiscriminator = "className"

  /**
   * @param naming the naming strategy
   * @param discriminator See [[MacroConfiguration.discriminator]]
   * @param typeNaming See [[MacroConfiguration.typeNaming]]
   */
  def apply[Opts <: Macros.Options](
    fieldNaming: FieldNaming = FieldNaming.Identity,
    discriminator: String = defaultDiscriminator,
    typeNaming: TypeNaming = TypeNaming.FullName)(implicit opts: Macros.Options.ValueOf[Opts]): MacroConfiguration.Aux[Opts] = new Impl(opts, fieldNaming, discriminator, typeNaming)

  /** Default configuration instance */
  implicit def default[Opts <: Macros.Options: Macros.Options.ValueOf]: MacroConfiguration.Aux[Opts] = apply()

  /** Configuration using [[TypeNaming$.SimpleName]] */
  @inline def simpleTypeName[Opts <: Macros.Options: Macros.Options.ValueOf]: MacroConfiguration.Aux[Opts] = apply(typeNaming = TypeNaming.SimpleName)

  private final class Impl[O <: Macros.Options](
    val options: Macros.Options.ValueOf[O],
    val fieldNaming: FieldNaming,
    val discriminator: String,
    val typeNaming: TypeNaming) extends MacroConfiguration {
    type Opts = O
  }

  protected def default: MacroConfiguration.Aux[Macros.Options] = apply()
}
