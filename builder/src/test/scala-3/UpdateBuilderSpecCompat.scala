import reactivemongo.api.bson.{ BSONArray, BSONDocument, BSONString }
import reactivemongo.api.bson.builder.{ ExprBuilder, Foo, UpdateBuilder }

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

  "Expression-based updates" should {
    import reactivemongo.api.bson.builder.Expr.implicits.mongoComparable

    "support comprehensive ExprBuilder with UpdateBuilder operations" in {
      val exprBuilder = ExprBuilder.empty[Foo]

      // Create field reference expressions
      val counterExpr = exprBuilder.select("counter")
      val scoreExpr = exprBuilder.select("score")
      val quantityExpr = exprBuilder.select("quantity")

      // Create computed expressions using arithmetic operators
      val incrementedCounter =
        exprBuilder.add(counterExpr, exprBuilder.from(10))
      val doubledScore = exprBuilder.multiply(scoreExpr, exprBuilder.from(2.0))
      val adjustedQuantity = exprBuilder.subtract(
        quantityExpr,
        exprBuilder.from(5L)
      )

      // Create a conditional expression using comparison
      val bonusPoints = exprBuilder.cond(
        exprBuilder.gte(counterExpr, exprBuilder.from(100)),
        exprBuilder.from(50),
        exprBuilder.from(10)
      )

      // Build update document using expressions
      val result = UpdateBuilder
        .empty[Foo]
        .set("counter", incrementedCounter)
        .set("score", doubledScore)
        .set("quantity", adjustedQuantity)
        .inc("counter", bonusPoints)
        .result()

      // Verify the generated BSON document structure
      result must_=== BSONDocument(
        f"$$set" -> BSONDocument(
          "counter" -> BSONDocument(
            f"$$add" -> BSONArray(BSONString(f"$$counter"), 10)
          ),
          "score" -> BSONDocument(
            f"$$multiply" -> BSONArray(BSONString(f"$$score"), 2.0)
          ),
          "quantity" -> BSONDocument(
            f"$$subtract" -> BSONArray(BSONString(f"$$quantity"), 5L)
          )
        ),
        f"$$inc" -> BSONDocument(
          "counter" -> BSONDocument(
            f"$$cond" -> BSONDocument(
              "if" -> BSONDocument(
                f"$$gte" -> BSONArray(BSONString(f"$$counter"), 100)
              ),
              "then" -> 50,
              "else" -> 10
            )
          )
        )
      )
    }
  }
}
