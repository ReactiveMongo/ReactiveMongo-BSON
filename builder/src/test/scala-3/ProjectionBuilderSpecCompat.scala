import reactivemongo.api.bson.BSONDocument
import reactivemongo.api.bson.builder.ProjectionBuilder

trait ProjectionBuilderSpecCompat { self: ProjectionBuilderSpec =>
  "Nested path" should {
    "be projected" in {
      ProjectionBuilder
        .empty[UserWithAddress]
        .nested("address")
        .at { nested => nested.includes("city") }
        .result() must_=== BSONDocument("address.city" -> 1)
    }
  }
}
