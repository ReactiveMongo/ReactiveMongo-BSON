import reactivemongo.api.bson.{ BSONString, FieldNaming, TypeNaming }

final class NamingSpec extends org.specs2.mutable.Specification {
  "Naming" title

  "Naming for property fooBar" should {
    "be preserved with Identity" in {
      FieldNaming.Identity("fooBar") must_=== "fooBar"
    }

    "return foo_bar with snake case" in {
      FieldNaming.SnakeCase("fooBar") must_=== "foo_bar"
    }

    "return FooBar with pascal case" in {
      FieldNaming.PascalCase("fooBar") must_=== "FooBar"
    }
  }

  "Naming for type BSONString" should {
    "be full name" in {
      TypeNaming.FullName(classOf[BSONString]) must_=== (
        "reactivemongo.api.bson.BSONString")
    }

    "be simple name" in {
      TypeNaming.SimpleName(classOf[BSONString]) must_=== "BSONString"
    }
  }
}
