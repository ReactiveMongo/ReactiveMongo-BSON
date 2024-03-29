package reactivemongo.api.bson

final class BSONObjectIDSpec extends org.specs2.mutable.Specification {
  "BSONObjectID".title

  "Object ID" should {
    "equal when created with string" in {
      val objectID = BSONObjectID.generate()

      BSONObjectID
        .parse(objectID.stringify)
        .aka("parsed from string") must beSuccessfulTry[BSONObjectID].like {
        case sameObjectID =>
          objectID.byteArray must_=== sameObjectID.byteArray and {
            BSONObjectID
              .parse(objectID.byteArray)
              .aka("parsed from bytes") must beSuccessfulTry(objectID)
          } and {
            sameObjectID.time must_=== objectID.time
          }
      }
    }

    "equal another instance of BSONObjectID with the same value" in {
      val objectID = BSONObjectID.generate()

      BSONObjectID
        .parse(objectID.stringify)
        .aka("parsed") must beSuccessfulTry[BSONObjectID].like {
        case sameObjectID =>
          sameObjectID must_=== objectID and {
            sameObjectID.time must_=== objectID.time
          }
      }
    }

    "not equal another newly generated instance of BSONObjectID" in {
      val objectID = BSONObjectID.generate()

      BSONObjectID
        .parse(BSONObjectID.generate().stringify)
        .aka("parsed") must beSuccessfulTry[BSONObjectID].like {
        case nextObjectID => objectID must not(beTypedEqualTo(nextObjectID))
      }
    }

    "fail gracefully for illegal 24 character strings" in {
      val shouldFail = List.fill(24)("z").mkString("")

      BSONObjectID.parse(shouldFail) must beAFailedTry[BSONObjectID]
        .withThrowable[IllegalArgumentException]
    }
  }

  "Digest" should {
    "generate strings equal each other" in {
      val objectID = "506fff5bb8f6b133007b5bcf"
      val hex = Digest.str2Hex(objectID)
      val string = Digest.hex2Str(hex)

      string must_=== objectID
    }

    "generate bytes equal bytes converted from string" in {
      val objectID = BSONObjectID.generate()
      val bytes = Digest.str2Hex(objectID.stringify)

      objectID.byteArray must_=== bytes and {
        BSONObjectID.parse(bytes) must beSuccessfulTry(objectID)
      }
    }
  }
}
