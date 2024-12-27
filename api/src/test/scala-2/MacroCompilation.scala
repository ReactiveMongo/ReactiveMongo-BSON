package reactivemongo.api.bson

//import com.github.ghik.silencer.silent

// Need a sealed family directly in `bson` package for compile check there
sealed trait Family
case class Member1(n: Int) extends Family
case object Member2 extends Family

trait UT2
case class UA2(n: Int) extends UT2
case class UB2(s: String) extends UT2

object MacroCompilation {

  object CompileUnion1 {
    implicit def reader1: BSONDocumentReader[Member1] = Macros.reader[Member1]
    implicit def writer1: BSONDocumentWriter[Member1] = Macros.writer[Member1]

    implicit def handler2: BSONDocumentHandler[Member2.type] =
      Macros.handler[Member2.type]

    @com.github.ghik.silencer.silent(".*Member2.*\\ no\\ case\\ accessor.*")
    implicit def reader2: BSONDocumentReader[Family] = {
      Macros.readerOpts[Family, MacroOptions.Verbose]
    }
  }

  object CompileUnion2 {
    import MacroOptions._

    val reader = Macros.readerOpts[UT2, UnionType[UA2 \/ UB2]
      with AutomaticMaterialization]

    val writer = Macros.writerOpts[UT2, UnionType[UA2 \/ UB2]
      with AutomaticMaterialization]
    /* Was failing `writer` with:

[error] ../api/src/test/scala/MacroCompilation.scala:30:35: local val macroCfg$macro$21 in value $anonfun is never used
[error]     val writer = Macros.writerOpts[UT2, UnionType[UA2 \/ UB2] with AutomaticMaterialization]
[error]                                   ^
     */

    locally {
      BSONDocumentHandler.provided(reader, writer)
    }
  }

  object WithConfig {
    import language.experimental.macros

    val config = MacroConfiguration(fieldNaming = FieldNaming.SnakeCase)

    def resolve: MacroConfiguration = macro MacroImpl.withOptionsConfig

    def implicitConf: MacroConfiguration = macro MacroImpl.implicitOptionsConfig
  }
}
