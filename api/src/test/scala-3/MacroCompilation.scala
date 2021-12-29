package reactivemongo.api.bson

object MacroCompilation:

  object WithConfig:
    val config = MacroConfiguration(fieldNaming = FieldNaming.SnakeCase)

    inline def resolve: MacroConfiguration = config

    inline def implicitConf: MacroConfiguration =
      ${ MacroImpl.implicitOptionsConfig }

  end WithConfig
end MacroCompilation
