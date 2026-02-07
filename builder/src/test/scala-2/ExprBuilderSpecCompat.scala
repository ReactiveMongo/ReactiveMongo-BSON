package reactivemongo.api.bson.builder

import reactivemongo.api.bson.{ BSONArray, BSONDocument, BSONString }

trait ExprBuilderSpecCompat { self: ExprBuilderSpec =>
  private val builder = ExprBuilder.empty[Foo]

  "select" should {
    "create field reference expression" >> {
      "for top-level field" in {
        val expr = builder.select(Symbol("id"))

        expr.writes() must beSuccessfulTry(BSONString("$id"))
      }

      "for nested field" in {
        val expr = builder.select(Symbol("status"), Symbol("name"))

        expr.writes() must beSuccessfulTry(BSONString("$status.name"))
      }

      "with prefix" in {
        val builder1 = new ExprBuilder[Foo](Seq("root"))
        val expr = builder1.select(Symbol("id"))

        expr.writes() must beSuccessfulTry(BSONString("$root.id"))
      }

      "for Int field type" in {
        val expr = builder.select(Symbol("counter"))

        expr.writes() must beSuccessfulTry(BSONString("$counter"))
      }

      "for Double field type" in {
        val expr = builder.select(Symbol("score"))

        expr.writes() must beSuccessfulTry(BSONString("$score"))
      }

      "for Seq field type" in {
        val expr = builder.select(Symbol("tags"))

        expr.writes() must beSuccessfulTry(BSONString("$tags"))
      }

      "for deeply nested field with 3 keys" in {
        val expr = builder.select(
          Symbol("status"),
          Symbol("details"),
          Symbol("shortDescription")
        )

        expr.writes() must beSuccessfulTry(
          BSONString("$status.details.shortDescription")
        )
      }

      "for nested field reference with 4 keys" in {
        val expr = builder.select(
          Symbol("location"),
          Symbol("detectedAt"),
          Symbol("position"),
          Symbol("current")
        )

        expr.writes() must beSuccessfulTry(
          BSONString("$location.detectedAt.position.current")
        )
      }

      "for nested field reference with 5 keys" in {
        val expr = builder.select(
          Symbol("tracker"),
          Symbol("commiter"),
          Symbol("lastActivity"),
          Symbol("details"),
          Symbol("longDescription")
        )

        expr.writes() must beSuccessfulTry(
          BSONString(
            "$tracker.commiter.lastActivity.details.longDescription"
          )
        )
      }
    }
  }

  "nested field" should {
    "be resolved" in {
      val result = builder.getField(
        builder.select(Symbol("location")),
        Symbol("updated")
      )

      result.writes() must beSuccessfulTry(
        BSONDocument(
          f"$$getField" -> BSONDocument(
            "field" -> "updated",
            "input" -> BSONString(f"$$location")
          )
        )
      )
    }
  }

  "filter" should {
    "create filter expression for array field" in {
      val tags = builder.select(Symbol("tags"))

      val filtered = builder.filter(tags) { item =>
        builder.eq(item, builder.from("scala"))
      }

      filtered.writes() must beSuccessfulTry(
        BSONDocument(
          f"$$filter" -> BSONDocument(
            "input" -> BSONString(f"$$tags"),
            "cond" -> BSONDocument(
              f"$$eq" -> BSONArray(
                BSONString(f"$$this"),
                "scala"
              )
            ),
            "as" -> "this"
          )
        )
      )
    }
  }
}
