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
      implicit def ord = Ord

      def spec(doc: BSONDocument, expected: BSONDocument) = {
        val a = doc ++ BSONDocument("foo" -> 4)
        val b = doc ++ ("foo" -> 4)
        val c = doc ++ BSONElement("foo", BSONInteger(4))

        a must_=== expected and {
          a.toMap.toSeq.sorted aka "a repr" must_=== a.elements.map {
            case BSONElement(k, v) => k -> v
          }.toSeq.sorted
        } and {
          b must_=== expected
        } and {
          b.toMap.toSeq.sorted aka "b repr" must_=== b.elements.map {
            case BSONElement(k, v) => k -> v
          }.toSeq.sorted
        } and {
          c must_=== expected
        } and {
          c.toMap.toSeq.sorted aka "c repr" must_=== c.elements.map {
            case BSONElement(k, v) => k -> v
          }.toSeq.sorted
        }
      }

      spec(doc1, BSONDocument(
        "Foo" -> 1, "Bar" -> 2, "Lorem" -> 3, "foo" -> 4)) and {
        spec(strict1, BSONDocument("foo" -> 1))
      } and {
        spec(strict2, BSONDocument("foo" -> 1))
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

  private object Ord extends scala.math.Ordering[(String, BSONValue)] {
    def compare(x: (String, BSONValue), y: (String, BSONValue)): Int =
      x._1 compare y._1
  }
}
