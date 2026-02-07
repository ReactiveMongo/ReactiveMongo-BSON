import reactivemongo.api.bson.{
  BSONArray,
  BSONDocument,
  BSONDouble,
  BSONLong,
  BSONString
}
import reactivemongo.api.bson.builder.{ ExprBuilder, Foo, ProjectionBuilder }

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

  "Complex expression integration" should {
    "build non-trivial expression projections combining arithmetic, conditionals, strings and arrays" in {
      val exprBuilder = ExprBuilder.empty[Foo]

      // Select fields from Foo, including nested fields
      val score = exprBuilder.select(Symbol("score"))
      val counter = exprBuilder.select(Symbol("counter"))
      val trackerId = exprBuilder.select(Symbol("tracker"), Symbol("id"))
      val tags = exprBuilder.select(Symbol("tags"))
      val id = exprBuilder.select(Symbol("id"))

      // Build a complex expression:
      // 1. Calculate weighted score: (score * 2.0) + counter
      val multiplier = exprBuilder.from(2.0)
      val weightedScore = exprBuilder.multiply(score, multiplier)
      val totalScore = exprBuilder.add(weightedScore, counter)

      // 2. Conditional: if totalScore >= 100, use trackerId, else 0
      val threshold = exprBuilder.from(100.0)
      val zero = exprBuilder.from(0L)
      val conditionalId = exprBuilder.cond(
        exprBuilder.gte(totalScore, threshold),
        trackerId,
        zero
      )

      // 3. String operation: concatenate id with "-processed"
      val suffix = exprBuilder.from("-processed")
      val processedId = exprBuilder.concat(id, suffix)

      // 4. Array operation with conditional: first tag or "default"
      val firstTag = exprBuilder.first(tags)
      val defaultTag = exprBuilder.from("default")
      val tagOrDefault = exprBuilder.ifNull(firstTag, defaultTag)

      // Create projection using the expressions
      val projection = ProjectionBuilder
        .empty[Foo]
        .includes(Symbol("id"))
        .project("calculatedScore", totalScore)
        .project("selectedId", conditionalId)
        .project("processedId", processedId)
        .project("primaryTag", tagOrDefault)
        .result()

      // Verify the complete BSON structure for the projection
      projection must_=== BSONDocument(
        "id" -> 1,
        "calculatedScore" -> BSONDocument(
          f"$$add" -> BSONArray(
            BSONDocument(
              f"$$multiply" -> BSONArray(
                BSONString(f"$$score"),
                BSONDouble(2.0)
              )
            ),
            BSONString(f"$$counter")
          )
        ),
        "selectedId" -> BSONDocument(
          f"$$cond" -> BSONDocument(
            "if" -> BSONDocument(
              f"$$gte" -> BSONArray(
                BSONDocument(
                  f"$$add" -> BSONArray(
                    BSONDocument(
                      f"$$multiply" -> BSONArray(
                        BSONString(f"$$score"),
                        BSONDouble(2.0)
                      )
                    ),
                    BSONString(f"$$counter")
                  )
                ),
                BSONDouble(100.0)
              )
            ),
            "then" -> BSONString(f"$$tracker.id"),
            "else" -> BSONLong(0L)
          )
        ),
        "processedId" -> BSONDocument(
          f"$$concat" -> BSONArray(
            BSONString(f"$$id"),
            BSONString("-processed")
          )
        ),
        "primaryTag" -> BSONDocument(
          f"$$ifNull" -> BSONArray(
            BSONDocument(f"$$first" -> BSONString(f"$$tags")),
            BSONString("default")
          )
        )
      )
    }
  }
}
