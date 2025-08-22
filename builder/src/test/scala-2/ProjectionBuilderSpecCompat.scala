import reactivemongo.api.bson.BSONDocument
import reactivemongo.api.bson.builder.ProjectionBuilder

trait ProjectionBuilderSpecCompat { _: ProjectionBuilderSpec =>
  "Nested path" should {
    "be projected" in {
      ProjectionBuilder
        .empty[UserWithAddress]
        .nested(Symbol("address"))
        .at { nested => nested.includes(Symbol("city")) }
        .result() must_=== BSONDocument("address.city" -> 1)
    }
  }
}
