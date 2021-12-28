// Make sure implicit/given instances are properly resolved from outside

import reactivemongo.api.bson._

import org.specs2.matcher.TypecheckMatchers._

import TestUtils.typecheck

final class ImplicitSpec extends org.specs2.mutable.Specification {
  "Implicit".title

  "Identity handler" should {
    "be resolved for" >> {
      "opaque BSONValue" in {
        typecheck("""
          implicitly[BSONReader[BSONValue]]
          implicitly[BSONWriter[BSONValue]]
          implicitly[BSONHandler[BSONValue]]
        """) must succeed
      }

      "BSONString" in {
        typecheck("""
          implicitly[BSONReader[BSONString]]
          implicitly[BSONWriter[BSONString]]
          implicitly[BSONHandler[BSONString]]
        """) must succeed
      }

      "BSONSymbol" in {
        typecheck("""
          implicitly[BSONReader[BSONSymbol]]
          implicitly[BSONWriter[BSONSymbol]]
          implicitly[BSONHandler[BSONSymbol]]
        """) must succeed
      }

      "BSONInteger" in {
        typecheck("""
          implicitly[BSONReader[BSONInteger]]
          implicitly[BSONWriter[BSONInteger]]
          implicitly[BSONHandler[BSONInteger]]
        """) must succeed
      }

      "BSONDecimal" in {
        typecheck("""
          implicitly[BSONReader[BSONDecimal]]
          implicitly[BSONWriter[BSONDecimal]]
          implicitly[BSONHandler[BSONDecimal]]
        """) must succeed
      }

      "BSONArray" in {
        typecheck("""
          implicitly[BSONReader[BSONArray]]
          implicitly[BSONWriter[BSONArray]]
          implicitly[BSONHandler[BSONArray]]
        """) must succeed
      }

      "BSONDocument" in {
        typecheck("""
          implicitly[BSONReader[BSONDocument]]
          implicitly[BSONWriter[BSONDocument]]
          implicitly[BSONHandler[BSONDocument]]

          implicitly[BSONDocumentReader[BSONDocument]]
          implicitly[BSONDocumentWriter[BSONDocument]]
        """) must succeed
      }

      "BSONBoolean" in {
        typecheck("""
          implicitly[BSONReader[BSONBoolean]]
          implicitly[BSONWriter[BSONBoolean]]
          implicitly[BSONHandler[BSONBoolean]]
        """) must succeed
      }

      "BSONLong" in {
        typecheck("""
          implicitly[BSONReader[BSONLong]]
          implicitly[BSONWriter[BSONLong]]
          implicitly[BSONHandler[BSONLong]]
        """) must succeed
      }

      "BSONDouble" in {
        typecheck("""
          implicitly[BSONReader[BSONDouble]]
          implicitly[BSONWriter[BSONDouble]]
          implicitly[BSONHandler[BSONDouble]]
        """) must succeed
      }

      "BSONObjectID" in {
        typecheck("""
          implicitly[BSONReader[BSONObjectID]]
          implicitly[BSONWriter[BSONObjectID]]
          implicitly[BSONHandler[BSONObjectID]]
        """) must succeed
      }

      "BSONBinary" in {
        typecheck("""
          implicitly[BSONReader[BSONBinary]]
          implicitly[BSONWriter[BSONBinary]]
          implicitly[BSONHandler[BSONBinary]]
        """) must succeed
      }

      "BSONDateTime" in {
        typecheck("""
          implicitly[BSONReader[BSONDateTime]]
          implicitly[BSONWriter[BSONDateTime]]
          implicitly[BSONHandler[BSONDateTime]]
        """) must succeed
      }

      "BSONTimestamp" in {
        typecheck("""
          implicitly[BSONReader[BSONTimestamp]]
          implicitly[BSONWriter[BSONTimestamp]]
          implicitly[BSONHandler[BSONTimestamp]]
        """) must succeed
      }

      "BSONMinKey" in {
        typecheck("""
          implicitly[BSONReader[BSONMinKey]]
          implicitly[BSONWriter[BSONMinKey]]
          implicitly[BSONHandler[BSONMinKey]]
        """) must succeed
      }

      "BSONMaxKey" in {
        typecheck("""
          implicitly[BSONReader[BSONMaxKey]]
          implicitly[BSONWriter[BSONMaxKey]]
          implicitly[BSONHandler[BSONMaxKey]]
        """) must succeed
      }

      "BSONNull" in {
        typecheck("""
          implicitly[BSONReader[BSONNull]]
          implicitly[BSONWriter[BSONNull]]
          implicitly[BSONHandler[BSONNull]]
        """) must succeed
      }

      "BSONUndefined" in {
        typecheck("""
          implicitly[BSONReader[BSONUndefined]]
          implicitly[BSONWriter[BSONUndefined]]
          implicitly[BSONHandler[BSONUndefined]]
        """) must succeed
      }

      "BSONRegex" in {
        typecheck("""
          implicitly[BSONReader[BSONRegex]]
          implicitly[BSONWriter[BSONRegex]]
          implicitly[BSONHandler[BSONRegex]]
        """) must succeed
      }
    }
  }
}
