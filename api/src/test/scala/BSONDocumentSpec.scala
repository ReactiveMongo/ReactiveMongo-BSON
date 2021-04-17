import reactivemongo.api.bson._

import scala.util.Success

final class BSONDocumentSpec extends org.specs2.mutable.Specification {
  "BSONDocument".title

  "Empty document" should {
    "be created" in {
      def spec(f: => BSONDocument) = {
        val doc = f

        doc.elements must beEmpty and {
          doc.isEmpty must beTrue
        } and {
          doc.contains("foo") must beFalse
        }
      }

      spec(BSONDocument()) and {
        spec(BSONDocument.empty)
      } and {
        spec(BSONDocument("foo" -> None))
      } and {
        spec(BSONDocument.empty ++ BSONDocument("foo" -> None))
      } and {
        spec(document)
      } and {
        spec(document())
      }
    }

    "be appended with a new element " in {
      val doc = BSONDocument.empty ++ ("foo" -> 1)

      doc must_=== BSONDocument("foo" -> 1) and (doc.contains(
        "foo"
      ) must beTrue)
    }
  }

  "Creation" should {
    "be successful" in {
      val elements = Seq(
        BSONElement("foo", BSONInteger(1)),
        BSONElement("bar", BSONDouble(2D))
      )

      BSONDocument("foo" -> 1, "bar" -> 2D).elements must_=== elements and {
        BSONDocument
          .safe("foo" -> 1, "bar" -> 2D)
          .map(_.elements) must_=== Success(elements)
      }
    }

    {
      type Foo = Tuple2[Int, String]
      implicit val failingWriter = BSONWriter[Foo] { _ =>
        throw new Exception("failing writer")
      }

      val elements = Seq(BSONElement("field2", BSONString("bar")))

      "ignore element with failing conversion" in {
        BSONDocument(
          "field1" -> (1 -> "value"),
          "field2" -> "bar"
        ).elements must_=== elements
      }

      "fail on writer error" in {
        BSONDocument.safe(
          "field1" -> (1 -> "value"),
          "field2" -> "bar"
        ) must beFailedTry[BSONDocument]
          .withThrowable[Exception]("failing writer")
      }
    }

    "skip duplicate element" in {
      BSONDocument("foo" -> 1, "foo" -> 1) must_=== BSONDocument("foo" -> 1)
    }

    "skip duplicate element name (strict)" in {
      val expected = BSONDocument("lorem" -> 2, "foo" -> "bar")

      eqSpec(strict1, "#1", expected) and {
        eqSpec(strict2, "#2", expected)
      }
    }

    "be done using builder" in {
      val builder1 = BSONDocument.newBuilder += ("foo" -> 1)
      val builder2 = BSONDocument.newBuilder

      builder2 ++= Option[ElementProducer]("bar" -> 2)

      builder1.result() must_=== BSONDocument("foo" -> 1) and {
        builder2.result() must_=== BSONDocument("bar" -> 2)
      } and {
        builder1 ++= Seq("ipsum" -> 3.45D, "dolor" -> 6L)

        builder1.result() must_=== BSONDocument(
          "foo" -> 1,
          "ipsum" -> 3.45D,
          "dolor" -> 6L
        )
      }
    }
  }

  "Append" should {
    "update list and map representation" in {
      def spec(doc: BSONDocument, expected: BSONDocument) = {
        val a = doc ++ BSONDocument("foo" -> 4)
        val b = doc ++ ("foo" -> 4)
        val c = doc ++ BSONElement("foo", BSONInteger(4))

        eqSpec(a, "a repr", expected) and {
          eqSpec(b, "b repr", expected)
        } and {
          eqSpec(c, "c repr", expected)
        }
      }

      spec(
        doc1,
        BSONDocument("Foo" -> 1, "Bar" -> 2, "Lorem" -> 3, "foo" -> 4)
      ) and {
        spec(strict1, BSONDocument("lorem" -> 2, "foo" -> 4))
      } and {
        spec(strict2, BSONDocument("lorem" -> 2, "foo" -> 4))
      }
    }
  }

  "Strict representation" should {
    "be updated without duplicate field" in {
      val s = BSONDocument.strict("Bar" -> 2, "Lorem" -> 3, "Foo" -> "other")
      val x = (doc1 ++ ("Foo" -> "other")).asStrict

      eqSpec(x, "dedup'ed as", s) and {
        eqSpec(
          doc1.asStrict /* no duplicate there at least*/,
          "with strict type",
          doc1
        )
      }
    }

    "be unchanged" in {
      def spec(d: BSONDocument) = eqSpec(d, "already strict", d) and {
        d must be(d)
      }

      spec(strict1) and spec(strict2)
    }
  }

  "Removal" should {
    "be successful" in {
      (doc1.--("Bar", "Lorem") must_=== BSONDocument("Foo" -> 1)) and {
        doc1.--("Foo", "Bar") must_=== BSONDocument("Lorem" -> 3)
      } and {
        (doc1 -- "Bar") contains ("Foo") must beTrue
      } and {
        // Retain strict'ness
        val su = strict1 -- "foo"

        su.asStrict must be(su)
      }
    }
  }

  "Field" should {
    import org.specs2.specification.core.Fragments

    val doc = BSONDocument(
      "i" -> 0,
      "l" -> (Int.MaxValue.toLong + 1L),
      "f" -> 0.1F,
      "d" -> (Float.MaxValue.toDouble + 1.2D)
    )

    "be resolved as double" >> {
      "successfully when compatible" in {
        doc.double("i") must beSome(0D) and {
          doc.double("l") must beSome(Int.MaxValue.toDouble + 1D)
        } and {
          doc.double("f").map(d => (d * 10D).toInt) must beSome(1)
        } and {
          doc.double("d") must beSome(Float.MaxValue.toDouble + 1.2D)
        }
      }
    }

    "be resolved as integer" >> {
      "successfully when compatible" in {
        doc.int("i") must beSome(0)
      }

      Fragments.foreach(Seq("l", "f", "d")) { n =>
        s"with error when not compatible '$n'" in {
          doc.int(n) must beNone
        }
      }
    }

    "be resolved as long" >> {
      "successfully when compatible" in {
        doc.long("i") must beSome(0L) and {
          doc.long("l") must beSome(Int.MaxValue.toLong + 1L)
        }
      }

      Fragments.foreach(Seq("f", "d")) { n =>
        s"with error when not compatible '$n'" in {
          doc.long(n) must beNone
        }
      }
    }
  }

  // ---

  lazy val doc1 = BSONDocument("Foo" -> 1, "Bar" -> 2, "Lorem" -> 3)

  lazy val strict1 =
    BSONDocument.strict("foo" -> 1, "lorem" -> 2, "foo" -> "bar")

  lazy val strict2 = BSONDocument.strict(
    Seq(
      "foo" -> BSONInteger(1),
      "lorem" -> BSONInteger(2),
      "foo" -> BSONString("bar")
    )
  )

  // ---

  @inline def eqSpec(d: BSONDocument, l: String, expected: BSONDocument) = {
    d must_=== expected
  } and {
    d.toMap.toSeq.sorted aka l must_=== d.elements.collect {
      case BSONElement(k, v) => k -> v
    }.toSeq.sorted
  }
}
