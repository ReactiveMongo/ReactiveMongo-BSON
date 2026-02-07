import reactivemongo.api.bson.{
  BSONArray,
  BSONDocument,
  BSONDouble,
  BSONLong,
  BSONString
}
import reactivemongo.api.bson.builder.{ Expr, ExprBuilder, FilterBuilder, Foo }

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

  "Complex expression integration" should {
    "build non-trivial expression combining arithmetic, conditionals, strings and arrays" in {
      import Expr.implicits.mongoComparable

      val exprBuilder = ExprBuilder.empty[Foo]

      // Select fields from Foo, including nested fields
      val score = exprBuilder.select("score")
      val counter = exprBuilder.select("counter")
      val trackerId = exprBuilder.select("tracker", "id")
      val tags = exprBuilder.select("tags")
      val id = exprBuilder.select("id")

      // Build a complex expression:
      // 1. Calculate weighted score: (score * 2.0) + counter
      val multiplier = exprBuilder.from(2.0)
      val weightedScore = exprBuilder.multiply(score, multiplier)
      val totalScore = exprBuilder.add(weightedScore, counter)

      // 2. Conditional: if totalScore > 100, use trackerId, else 0
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

      // Use expressions with FilterBuilder to verify integration
      // 1. Using expr() to wrap entire boolean expression
      FilterBuilder
        .empty[Foo]
        .expr(exprBuilder.gte(totalScore, threshold))
        .result() must_=== BSONDocument(
        f"$$expr" -> BSONDocument(
          f"$$gte" -> BSONArray(
            BSONDocument(
              f"$$add" -> BSONArray(
                BSONDocument(
                  f"$$multiply" -> BSONArray(
                    BSONString(f"$$score"),
                    BSONDouble(2.0D)
                  )
                ),
                BSONString(f"$$counter")
              )
            ),
            BSONDouble(100.0)
          )
        )
      ) and {
        // 2. Using expression as value in eq comparison with existing field
        FilterBuilder
          .empty[Foo]
          .eq("quantity", conditionalId)
          .result() must_=== BSONDocument(
          "quantity" -> BSONDocument(
            f"$$eq" -> BSONDocument(
              f"$$cond" -> BSONDocument(
                "if" -> BSONDocument(
                  f"$$gte" -> BSONArray(
                    BSONDocument(
                      f"$$add" -> BSONArray(
                        BSONDocument(
                          f"$$multiply" -> BSONArray(
                            BSONString(f"$$score"),
                            BSONDouble(2.0D)
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
            )
          )
        )
      } and {
        // 3. Using expression as value in gte comparison with existing field
        FilterBuilder
          .empty[Foo]
          .gte("score", totalScore)
          .result() must_=== BSONDocument(
          "score" -> BSONDocument(
            f"$$gte" -> BSONDocument(
              f"$$add" -> BSONArray(
                BSONDocument(
                  f"$$multiply" -> BSONArray(
                    BSONString(f"$$score"),
                    BSONDouble(2.0D)
                  )
                ),
                BSONString(f"$$counter")
              )
            )
          )
        )
      } and {
        // 4. Using expression as value in ne comparison with string expression
        FilterBuilder
          .empty[Foo]
          .ne("id", processedId)
          .result() must_=== BSONDocument(
          "id" -> BSONDocument(
            f"$$ne" -> BSONDocument(
              f"$$concat" -> BSONArray(
                BSONString(f"$$id"),
                BSONString("-processed")
              )
            )
          )
        )
      } and {
        // 5. Combining multiple filters with expressions
        FilterBuilder
          .empty[Foo]
          .eq("id", "test")
          .gt("score", totalScore)
          .and() must_=== BSONDocument(
          f"$$and" -> BSONArray(
            BSONDocument("id" -> BSONDocument(f"$$eq" -> "test")),
            BSONDocument(
              "score" -> BSONDocument(
                f"$$gt" -> BSONDocument(
                  f"$$add" -> BSONArray(
                    BSONDocument(
                      f"$$multiply" -> BSONArray(
                        BSONString(f"$$score"),
                        BSONDouble(2.0D)
                      )
                    ),
                    BSONString(f"$$counter")
                  )
                )
              )
            )
          )
        )
      }
    }
  }

  "Expression functionality" should {
    f"add $$expr with aggregation expression" in {
      import reactivemongo.api.bson.builder.ExprBuilder

      val exprBuilder = ExprBuilder.empty[Foo]
      val score = exprBuilder.select("score")
      val ten = exprBuilder.from(10.0)
      val expr = exprBuilder.gt(score, ten)

      FilterBuilder.empty[Foo].expr(expr).result() must_=== BSONDocument(
        f"$$expr" -> BSONDocument(
          f"$$gt" -> BSONArray(BSONString(f"$$score"), 10.0)
        )
      )
    }

    f"combine $$expr with other filters" in {
      import reactivemongo.api.bson.builder.ExprBuilder

      val exprBuilder = ExprBuilder.empty[Foo]
      val score = exprBuilder.select("score")
      val counter = exprBuilder.select("counter")
      val expr = exprBuilder.lt(score, counter)

      FilterBuilder
        .empty[Foo]
        .eq("id", "test-id")
        .expr(expr)
        .and() must_=== BSONDocument(
        f"$$and" -> BSONArray(
          BSONDocument("id" -> BSONDocument(f"$$eq" -> "test-id")),
          BSONDocument(
            f"$$expr" -> BSONDocument(
              f"$$lt" -> BSONArray(
                BSONString(f"$$score"),
                BSONString(f"$$counter")
              )
            )
          )
        )
      )
    }

    f"use $$expr with computed values" in {
      import reactivemongo.api.bson.builder.ExprBuilder

      val exprBuilder = ExprBuilder.empty[Foo]
      val score = exprBuilder.select("score")
      val counter = exprBuilder.select("counter")
      val sum = exprBuilder.add(score, counter)
      val hundred = exprBuilder.from(100.0)
      val expr = exprBuilder.gte(sum, hundred)

      FilterBuilder.empty[Foo].expr(expr).result() must_=== BSONDocument(
        f"$$expr" -> BSONDocument(
          f"$$gte" -> BSONArray(
            BSONDocument(
              f"$$add" -> BSONArray(
                BSONString(f"$$score"),
                BSONString(f"$$counter")
              )
            ),
            100.0
          )
        )
      )
    }
  }
}
