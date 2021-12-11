import reactivemongo.api.bson.{
  BSONDocument,
  BSONDocumentReader,
  BSONDocumentWriter,
  Macros
}

trait MacroExtraSpec { _: MacroSpec =>
  import MacroTest._

  "Case class" should {
    "be handled with implicits properties (deprecated)" >> {
      // WON'T BE SUPPORTED IN SCALA 3.x

      val doc1 = BSONDocument("pos" -> 2, "text" -> "str")
      val doc2 = BSONDocument("ident" -> "id", "value" -> 23.456D)
      val fixture1 = WithImplicit1(2, "str")
      val fixture2 = WithImplicit2("id", 23.456D)

      def readSpec1(r: BSONDocumentReader[WithImplicit1]) =
        r.readTry(doc1) must beSuccessfulTry(fixture1)

      def writeSpec2(w: BSONDocumentWriter[WithImplicit2[Double]]) =
        w.writeTry(fixture2) must beSuccessfulTry(doc2)

      "to generate reader" in readSpec1(Macros.reader[WithImplicit1])

      "to generate writer with type parameters" in writeSpec2(
        Macros.writer[WithImplicit2[Double]]
      )

      "to generate handler" in {
        val f1 = Macros.handler[WithImplicit1]
        val f2 = Macros.handler[WithImplicit2[Double]]

        readSpec1(f1) and {
          f1.writeTry(fixture1) must beSuccessfulTry(doc1)
        } and {
          writeSpec2(f2)
        } and {
          f2.readTry(doc2) must beSuccessfulTry(fixture2)
        }
      }
    }
  }
}
