import reactivemongo.api.bson.BSONDocument
import reactivemongo.api.bson.builder.{ Foo, UpdateBuilder }

trait UpdateBuilderSpecCompat { self: UpdateBuilderSpec =>
  "Nested path" should {
    "support deep nested field with optional parent" in {
      UpdateBuilder
        .empty[Foo]
        .nested("status", "details")
        .at { _.set("shortDescription", "Updated description") }
        .result() must_=== BSONDocument(
        f"$$set" -> BSONDocument(
          "status.details.shortDescription" -> "Updated description"
        )
      )
    }

    "support deep nested field updates" in {
      UpdateBuilder
        .empty[Foo]
        .nested("tracker", "commiter")
        .at { _.set("username", "Alice") }
        .result() must_=== BSONDocument(
        f"$$set" -> BSONDocument("tracker.commiter.username" -> "Alice")
      )
    }
  }
}
