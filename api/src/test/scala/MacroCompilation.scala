package reactivemongo.api.bson

//import com.github.ghik.silencer.silent

// Need a sealed family directly in `bson` package for compile check there
sealed trait Family
case class Member1(n: Int) extends Family
case object Member2 extends Family

object MacroCompilation {
  object CompileUnion1 {
    implicit def reader1 = Macros.reader[Member1]
    implicit def writer1 = Macros.writer[Member1]

    implicit def handler2 = Macros.handler[Member2.type]

    implicit def reader: BSONDocumentReader[Family] = {
      Macros.readerOpts[Family, MacroOptions.Verbose]
    }
  }
}
