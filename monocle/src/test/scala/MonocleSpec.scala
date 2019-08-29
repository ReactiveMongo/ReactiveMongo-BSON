import reactivemongo.api.bson._

final class MonocleSpec extends org.specs2.mutable.Specification {
  "Monocle" title

  // Requires import
  import reactivemongo.api.bson.monocle._

  val barDoc = BSONDocument(
    "lorem" -> 2,
    "ipsum" -> BSONDocument("dolor" -> 3))

  val topDoc = BSONDocument(
    "foo" -> 1,
    "bar" -> barDoc)

  "Lens" should {
    "manage simple field" >> {
      "as BSON value" in {
        val lens = field[BSONInteger]("foo")

        lens.set(BSONInteger(2))(topDoc) must_=== (topDoc ++ ("foo" -> 2))
      }

      "as supported value" in {
        val lens = field[Float]("foo")

        lens.set(2.3F)(topDoc) must_=== (topDoc ++ ("foo" -> 2.3F))
      }
    }

    "manage nested field" >> {
      "using composition" in {
        val lens =
          field[BSONDocument]("bar") composeOptional field[Double]("lorem")

        lens.set(1.23D)(topDoc) must_=== (topDoc ++ (
          "bar" -> (barDoc ++ ("lorem" -> 1.23D))))
      }

      "using operator '\\'" in {
        val lens = "bar" \ field[String]("lorem")

        lens.set("VALUE")(topDoc) must_=== (topDoc ++ (
          "bar" -> (barDoc ++ ("lorem" -> "VALUE"))))
      }
    }

    "manage deep field" in {
      val lens = "bar" \ "ipsum" \ field[Long]("dolor")

      lens.set(4L)(topDoc) must_=== (topDoc ++ (
        "bar" -> (barDoc ++ ("ipsum" -> BSONDocument("dolor" -> 4L)))))
    }
  }
}
