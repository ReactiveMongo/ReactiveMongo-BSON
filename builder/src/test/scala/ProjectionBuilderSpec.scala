import reactivemongo.api.bson.BSONDocument
import reactivemongo.api.bson.builder.{ Expr, ProjectionBuilder, TestUtils }

final class ProjectionBuilderSpec
    extends org.specs2.mutable.Specification
    with ProjectionBuilderSpecCompat {

  "Projection builder".title

  import TestUtils.symbol

  "Basic projection operations" should {
    "include single field" in {
      ProjectionBuilder
        .empty[User]
        .includes(symbol("name"))
        .result() must_=== BSONDocument("name" -> 1)
    }

    "exclude single field" in {
      ProjectionBuilder
        .empty[User]
        .excludes(symbol("password"))
        .result() must_=== BSONDocument("password" -> 0)
    }

    "include multiple fields" in {
      ProjectionBuilder
        .empty[User]
        .includes(symbol("name"))
        .includes(symbol("email"))
        .result() must_=== BSONDocument("name" -> 1, "email" -> 1)
    }

    "exclude multiple fields" in {
      ProjectionBuilder
        .empty[User]
        .excludes(symbol("password"))
        .excludes(symbol("internalId"))
        .result() must_=== BSONDocument("password" -> 0, "internalId" -> 0)
    }

    "mix includes and excludes" in {
      ProjectionBuilder
        .empty[User]
        .includes(symbol("name"))
        .excludes(symbol("password"))
        .result() must_=== BSONDocument("name" -> 1, "password" -> 0)
    }

    "handle duplicate field projections (last wins)" in {
      ProjectionBuilder
        .empty[User]
        .includes(symbol("name"))
        .excludes(symbol("name"))
        .result() must_=== BSONDocument("name" -> 0)
    }
  }

  "Custom projection expressions" should {
    "support custom BSON expressions" in {
      ProjectionBuilder
        .empty[User]
        .project(
          "displayName",
          Expr.unsafe[User, String](BSONDocument(f"$$toUpper" -> f"$$name"))
        )
        .result() must_=== BSONDocument(
        "displayName" -> BSONDocument(f"$$toUpper" -> f"$$name")
      )
    }

    "support array slice expressions" in {
      ProjectionBuilder
        .empty[User]
        .project(
          "items",
          Expr.unsafe[User, Seq[String]](BSONDocument(f"$$slice" -> 5))
        )
        .result() must_=== BSONDocument(
        "items" -> BSONDocument(f"$$slice" -> 5)
      )
    }
  }

  "Nested field projection" should {
    "project nested field with nestedField" in {
      ProjectionBuilder
        .empty[UserWithAddress]
        .nestedField[Address, Address](symbol("address"))
        .at { nested => nested.includes(symbol("city")) }
        .result() must_=== BSONDocument("address.city" -> 1)
    }

    "project multiple nested fields" in {
      ProjectionBuilder
        .empty[UserWithAddress]
        .nestedField[Address, Address](symbol("address"))
        .at { nested =>
          nested.includes(symbol("city"))
          nested.includes(symbol("country"))
        }
        .result() must_=== BSONDocument(
        "address.city" -> 1,
        "address.country" -> 1
      )
    }
  }

  "Array field projection" should {
    "project positional operator for array field" in {
      ProjectionBuilder
        .empty[UserWithTags]
        .positional(symbol("tags"))
        .result() must_=== BSONDocument(f"tags.$$" -> 1)
    }

    "combine positional with other fields" in {
      ProjectionBuilder
        .empty[UserWithTags]
        .includes(symbol("name"))
        .positional(symbol("tags"))
        .result() must_=== BSONDocument("name" -> 1, f"tags.$$" -> 1)
    }
  }

  "Empty builder" should {
    "return empty document" in {
      ProjectionBuilder.empty[User].result() must_=== BSONDocument.empty
    }
  }

  // Test models
  case class User(
      name: String,
      email: String,
      password: String,
      internalId: String)

  case class Address(
      street: String,
      city: String,
      country: String)

  case class UserWithAddress(
      name: String,
      email: String,
      address: Address)

  case class UserWithTags(
      name: String,
      tags: List[String])
}
