import reactivemongo.api.bson.EnumHelper

final class EnumHelperSpec extends org.specs2.mutable.Specification {
  "Enum helper".title

  "Color" should {
    "be resolved from" >> {
      "exact name" in {
        val valueOf = EnumHelper.strictValueOf[Color]

        valueOf("Red") must beSome(Color.Red) and {
          valueOf("Green") must beSome(Color.Green)
        } and {
          valueOf("Blue") must beSome(Color.Blue)
        } and {
          valueOf("red") must beNone
        } and {
          valueOf("green") must beNone
        } and {
          valueOf("blue") must beNone
        }
      }

      "uppercase name" in {
        val valueOf = EnumHelper.upperCaseValueOf[Color]

        valueOf("RED") must beSome(Color.Red) and {
          valueOf("GREEN") must beSome(Color.Green)
        } and {
          valueOf("BLUE") must beSome(Color.Blue)
        } and {
          valueOf("red") must beNone
        } and {
          valueOf("Green") must beNone
        } and {
          valueOf("blue") must beNone
        }
      }

      "lowercase name" in {
        val valueOf = EnumHelper.lowerCaseValueOf[Color]

        valueOf("red") must beSome(Color.Red) and {
          valueOf("green") must beSome(Color.Green)
        } and {
          valueOf("blue") must beSome(Color.Blue)
        } and {
          valueOf("RED") must beNone
        } and {
          valueOf("Green") must beNone
        } and {
          valueOf("BLUE") must beNone
        }
      }

      "insensitive name" in {
        val valueOf = EnumHelper.insensitiveValueOf[Color]

        valueOf("Red") must beSome(Color.Red) and {
          valueOf("green") must beSome(Color.Green)
        } and {
          valueOf("BLUE") must beSome(Color.Blue)
        } and {
          valueOf("reD") must beSome(Color.Red)
        } and {
          valueOf("greEn") must beSome(Color.Green)
        } and {
          valueOf("Blue") must beSome(Color.Blue)
        }
      }
    }
  }
}

enum Color:
  case Red, Green, Blue
