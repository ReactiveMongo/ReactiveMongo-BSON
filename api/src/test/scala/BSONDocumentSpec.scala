import reactivemongo.api.bson._

final class BSONDocumentSpec extends org.specs2.mutable.Specification {
  "BSONDocument" title

  "creation" should {
    "skip duplicate element" in {
      BSONDocument("foo" -> 1, "foo" -> 1) must_=== BSONDocument("foo" -> 1)
    }

    "skip duplicate element name (strict)" in {
      val expected = BSONDocument("foo" -> 1)

      strict1 must_=== expected and {
        strict1.toMap.toSeq must_=== strict1.elements.map {
          case BSONElement(n, v) => n -> v
        }.toSeq
      } and {
        strict2 must_=== expected
      } and {
        strict2.toMap.toSeq must_=== strict2.elements.map {
          case BSONElement(n, v) => n -> v
        }.toSeq
      } and {
        (strict1 ++ BSONDocument("foo" -> 1.2D)) must_=== expected
      } and {
        (strict1 ++ BSONElement("foo", BSONString("lorem"))) must_=== expected
      } and {
        (strict1 ++ ("foo" -> 3.45F)) must_=== expected
      }
    }
  }

  "append" should {
    "update list and map representation" in {
      def spec(doc: BSONDocument, expected: BSONDocument) = {
        val a = doc ++ BSONDocument("ipsum" -> 4)
        val b = doc ++ ("ipsum" -> 4)
        val c = doc ++ BSONElement("ipsum", BSONInteger(4))

        a must_=== expected and {
          a.toMap.toSeq must_=== a.elements.map {
            case BSONElement(k, v) => k -> v
          }.toSeq
        } and {
          b must_=== expected
        } and {
          b.toMap.toSeq must_=== b.elements.map {
            case BSONElement(k, v) => k -> v
          }.toSeq
        } and {
          c must_=== expected
        } and {
          c.toMap.toSeq must_=== c.elements.map {
            case BSONElement(k, v) => k -> v
          }.toSeq
        }
      }

      spec(doc1, BSONDocument(
        "Foo" -> 1, "Bar" -> 2, "Lorem" -> 3, "ipsum" -> 4)) and {
        spec(strict1, BSONDocument("foo" -> 1, "ipsum" -> 4))
      } and {
        spec(strict2, BSONDocument("foo" -> 1, "ipsum" -> 4))
      }
    }
  }

  "removal" should {
    "be successful" in {
      (doc1 -- ("Bar", "Lorem") must_=== BSONDocument("Foo" -> 1)) and
        (doc1 -- ("Foo", "Bar") must_=== BSONDocument("Lorem" -> 3)) and
        (doc1 -- ("Bar") contains ("Foo") must beTrue)
    }
  }

  // ---

  lazy val doc1 = BSONDocument("Foo" -> 1, "Bar" -> 2, "Lorem" -> 3)

  lazy val strict1 = BSONDocument.strict("foo" -> 1, "foo" -> 1.2D)

  lazy val strict2 = BSONDocument.strict(Seq(
    "foo" -> BSONInteger(1),
    "foo" -> BSONString("bar")))

}
