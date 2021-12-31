package reactivemongo.api.bson

object MacroCompilation:

  object WithConfig:
    val config = MacroConfiguration(fieldNaming = FieldNaming.SnakeCase)

    inline def resolve: MacroConfiguration = config

    inline def implicitConf: MacroConfiguration =
      ${ MacroImpl.implicitOptionsConfig }

  end WithConfig

end MacroCompilation

final class OtherCompilation {

  case class Product(
      _id: Int,
      sku: Option[String] = None,
      description: Option[String] = None,
      instock: Option[Int] = None)

  def foo(): Unit = {
    // See https://github.com/ReactiveMongo/ReactiveMongo-BSON/commit/d1a776b19e6ee07e868d3baa16c0c1ade8799fad#r62747279
    implicit val productHandler: BSONDocumentHandler[Product] =
      Macros.handler[Product]
    ()
  }
}
