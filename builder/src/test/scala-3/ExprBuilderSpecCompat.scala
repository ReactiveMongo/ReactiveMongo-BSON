package reactivemongo.api.bson.builder

import reactivemongo.api.bson.{ BSONArray, BSONDocument, BSONString }

import TestUtils.symbol

trait ExprBuilderSpecCompat { self: ExprBuilderSpec =>
  private val builder = ExprBuilder.empty[Foo]

  "select" should {
    "create field reference expression" >> {
      "for top-level field" in {
        builder.select("id").writes() must beSuccessfulTry(BSONString("$id"))
      }

      "for nested field with 2 keys" in {
        builder.select("status", "name").writes() must beSuccessfulTry(
          BSONString("$status.name")
        )
      }

      "for deeply nested field with 3 keys" in {
        builder
          .select(
            "status",
            "details",
            "shortDescription"
          )
          .writes() must beSuccessfulTry(
          BSONString("$status.details.shortDescription")
        )
      }

      "with prefix" in {
        val builder1 = new ExprBuilder[Foo](Seq("root"))

        builder1.select("id").writes() must beSuccessfulTry(
          BSONString("$root.id")
        )
      }

      "for Int field type" in {
        builder.select("counter").writes() must beSuccessfulTry(
          BSONString("$counter")
        )
      }

      "for Double field type" in {
        builder.select("score").writes() must beSuccessfulTry(
          BSONString("$score")
        )
      }

      "for Seq field type" in {
        builder.select("tags").writes() must beSuccessfulTry(
          BSONString("$tags")
        )
      }

      "support nested field reference with 4 keys" in {
        builder
          .select(
            "location",
            "detectedAt",
            "position",
            "current"
          )
          .writes() must beSuccessfulTry(
          BSONString("$location.detectedAt.position.current")
        )
      }

      "support nested field reference with 5 keys" in {
        builder
          .select(
            "tracker",
            "commiter",
            "lastActivity",
            "details",
            "longDescription"
          )
          .writes() must beSuccessfulTry(
          BSONString("$tracker.commiter.lastActivity.details.longDescription")
        )
      }
    }
  }

  "nested field" should {
    "be resolved" in {
      val result = builder.getField(
        builder.select("location"),
        "updated"
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
      val tags = builder.select("tags")

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
