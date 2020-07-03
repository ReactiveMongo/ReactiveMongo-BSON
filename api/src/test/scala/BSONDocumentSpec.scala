import reactivemongo.api.bson._

final class BSONDocumentSpec extends org.specs2.mutable.Specification {
  "BSONDocument" title

  "creation" should {
    "skip duplicate element" in {
      BSONDocument("foo" -> 1, "foo" -> 1) must_=== BSONDocument("foo" -> 1)
    }

    "skip duplicate element name (strict)" in {
      val expected = BSONDocument("lorem" -> 2, "foo" -> "bar")

      eqSpec(strict1, "#1", expected) and {
        eqSpec(strict2, "#2", expected)
      }
    }
  }

  "append" should {
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

      spec(doc1, BSONDocument(
        "Foo" -> 1, "Bar" -> 2, "Lorem" -> 3, "foo" -> 4)) and {
        spec(strict1, BSONDocument("lorem" -> 2, "foo" -> 4))
      } and {
        spec(strict2, BSONDocument("lorem" -> 2, "foo" -> 4))
      }
    }
  }

  "strict representation" should {
    "be updated without duplicate field" in {
      val s = BSONDocument.strict("Bar" -> 2, "Lorem" -> 3, "Foo" -> "other")
      val x = (doc1 ++ ("Foo" -> "other")).asStrict

      eqSpec(x, "dedup'ed as", s) and {
        eqSpec(
          doc1.asStrict /* no duplicate there at least*/ ,
          "with strict type", doc1)
      }
    }

    "be unchanged" in {
      def spec(d: BSONDocument) = eqSpec(d, "already strict", d) and {
        d must be(d)
      }

      spec(strict1) and spec(strict2)
    }
  }

  "removal" should {
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

  // ---

  lazy val doc1 = BSONDocument("Foo" -> 1, "Bar" -> 2, "Lorem" -> 3)

  lazy val strict1 = BSONDocument.strict(
    "foo" -> 1, "lorem" -> 2, "foo" -> "bar")

  lazy val strict2 = BSONDocument.strict(Seq(
    "foo" -> BSONInteger(1),
    "lorem" -> BSONInteger(2),
    "foo" -> BSONString("bar")))

  // ---

  @inline def eqSpec(d: BSONDocument, l: String, expected: BSONDocument) = {
    d must_=== expected
  } and {
    d.toMap.toSeq.sorted aka l must_=== d.elements.map {
      case BSONElement(k, v) => k -> v
    }.toSeq.sorted
  }
}
