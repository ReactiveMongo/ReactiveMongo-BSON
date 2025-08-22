import reactivemongo.api.bson.BSONDocument
import reactivemongo.api.bson.builder.{ FilterBuilder, Foo }

trait FilterBuilderSpecCompat { self: FilterBuilderSpec =>
  "Nested path" should {
    "support deeply nested fields" in {
      FilterBuilder
        .empty[Foo]
        .nested("tracker", "commiter", "lastActivity")
        .at { _.exists("details", true) }
        .result() must_=== BSONDocument(
        "tracker.commiter.lastActivity.details" -> BSONDocument(
          f"$$exists" -> true
        )
      )
    }

    "support deeply optional nested fields" in {
      FilterBuilder
        .empty[Foo]
        .nested("status", "details")
        .at {
          _.eq("shortDescription", "Bar")
        }
        .result() must_=== BSONDocument(
        "status.details.shortDescription" -> BSONDocument(
          f"$$eq" -> "Bar"
        )
      )
    }
  }
}
