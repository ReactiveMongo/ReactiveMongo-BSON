import reactivemongo.api.bson.BSONDocument
import reactivemongo.api.bson.builder.{ FilterBuilder, Foo }

trait FilterBuilderSpecCompat { _: FilterBuilderSpec =>
  "Nested path" should {
    "support deeply nested fields" in {
      FilterBuilder
        .empty[Foo]
        .nested(Symbol("tracker"), Symbol("commiter"), Symbol("lastActivity"))
        .at { _.exists(Symbol("details"), true) }
        .result() must_=== BSONDocument(
        "tracker.commiter.lastActivity.details" -> BSONDocument(
          f"$$exists" -> true
        )
      )
    }

    "support deeply optional nested fields" in {
      FilterBuilder
        .empty[Foo]
        .nested(Symbol("status"), Symbol("details"))
        .at {
          _.eq(Symbol("shortDescription"), "Bar")
        }
        .result() must_=== BSONDocument(
        "status.details.shortDescription" -> BSONDocument(
          f"$$eq" -> "Bar"
        )
      )
    }
  }
}
