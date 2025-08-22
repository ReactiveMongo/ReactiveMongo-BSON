import reactivemongo.api.bson.BSONDocument
import reactivemongo.api.bson.builder.{ Foo, UpdateBuilder }

trait UpdateBuilderSpecCompat { _: UpdateBuilderSpec =>
  "Nested path" should {
    "support deep nested field with optional parent" in {
      UpdateBuilder
        .empty[Foo]
        .nested(Symbol("status"), Symbol("details"))
        .at { _.set(Symbol("shortDescription"), "Updated description") }
        .result() must_=== BSONDocument(
        f"$$set" -> BSONDocument(
          "status.details.shortDescription" -> "Updated description"
        )
      )
    }

    "support deep nested field updates" in {
      UpdateBuilder
        .empty[Foo]
        .nested(Symbol("tracker"), Symbol("commiter"))
        .at { _.set(Symbol("username"), "Alice") }
        .result() must_=== BSONDocument(
        f"$$set" -> BSONDocument("tracker.commiter.username" -> "Alice")
      )
    }
  }
}
