import reactivemongo.api.bson.{ BSONArray, BSONDocument, BSONString }
import reactivemongo.api.bson.builder.{ FilterBuilder, Foo, TestUtils }

final class FilterBuilderSpec
    extends org.specs2.mutable.Specification
    with FilterBuilderSpecCompat {

  "Filter builder".title

  import TestUtils.symbol

  "Typed filters" should {
    "support operator" >> {
      f"$$eq" in {
        FilterBuilder
          .empty[Foo]
          .eq(symbol("id"), "test-id")
          .and() must_=== BSONDocument(
          "id" -> BSONDocument(f"$$eq" -> "test-id")
        )
      }

      f"$$gt" in {
        FilterBuilder
          .empty[Foo]
          .gt(symbol("score"), 50.0)
          .and() must_=== BSONDocument("score" -> BSONDocument(f"$$gt" -> 50.0))
      }

      f"$$lt" in {
        FilterBuilder
          .empty[Foo]
          .lt(symbol("score"), 200.0)
          .and() must_=== BSONDocument(
          "score" -> BSONDocument(f"$$lt" -> 200.0)
        )
      }

      f"$$in" in {
        val tags = Seq("tag1", "tag2")

        FilterBuilder
          .empty[Foo]
          .in(symbol("tags"), tags)
          .and() must_=== BSONDocument(
          "tags" -> BSONDocument(
            f"$$in" -> BSONArray(tags.map(BSONString(_)).toList)
          )
        )
      }

      f"$$nin" in {
        val tags = Seq("tag1", "tag2")

        FilterBuilder
          .empty[Foo]
          .nin(symbol("tags"), tags)
          .and() must_=== BSONDocument(
          "tags" -> BSONDocument(
            f"$$nin" -> BSONArray(tags.map(BSONString(_)).toList)
          )
        )
      }

      f"$$ne" in {
        FilterBuilder
          .empty[Foo]
          .ne(symbol("id"), "invalid-id")
          .and() must_=== BSONDocument(
          "id" -> BSONDocument(f"$$ne" -> "invalid-id")
        )
      }

      f"$$gte" in {
        val result = FilterBuilder.empty[Foo].gte(symbol("counter"), 10).and()

        result must_=== BSONDocument("counter" -> BSONDocument(f"$$gte" -> 10))
      }

      f"$$lte" in {
        val result = FilterBuilder.empty[Foo].lte(symbol("score"), 500.0).and()

        result must_=== BSONDocument("score" -> BSONDocument(f"$$lte" -> 500.0))
      }

      f"$$exists" in {
        val result = FilterBuilder.empty[Foo].exists(symbol("tags"), true).and()

        result must_=== BSONDocument(
          "tags" -> BSONDocument(f"$$exists" -> true)
        )
      }

      f"$$size" in {
        val result = FilterBuilder.empty[Foo].size(symbol("tags"), 2).and()

        result must_=== BSONDocument("tags" -> BSONDocument(f"$$size" -> 2))
      }
    }
  }

  "Logical operations" should {
    "combine empty builder" in {
      val builder = FilterBuilder.empty[Foo]

      builder.and() must_=== BSONDocument.empty and {
        builder.or() must_=== BSONDocument.empty
      } and {
        builder.result() must_=== BSONDocument.empty
      }
    }

    "return single clause directly without and wrapper" in {
      val expected = BSONDocument("id" -> BSONDocument(f"$$eq" -> "test-id"))
      val builder = FilterBuilder.empty[Foo].eq(symbol("id"), "test-id")

      builder.and() must_=== expected and {
        builder.or() must_=== expected
      }
    }

    "combine multiple clauses" >> {
      f"in $$and" in {
        FilterBuilder
          .empty[Foo]
          .eq(symbol("id"), "test-id")
          .eq(symbol("score"), 599.99)
          .and() must_=== BSONDocument(
          f"$$and" -> BSONArray(
            BSONDocument(f"id" -> BSONDocument(f"$$eq" -> "test-id")),
            BSONDocument(f"score" -> BSONDocument(f"$$eq" -> 599.99))
          )
        )
      }

      f"in $$or" in {
        FilterBuilder
          .empty[Foo]
          .eq(symbol("id"), "id1")
          .eq(symbol("score"), 599.98)
          .or() must_=== BSONDocument(
          f"$$or" -> BSONArray(
            BSONDocument(f"id" -> BSONDocument(f"$$eq" -> "id1")),
            BSONDocument(f"score" -> BSONDocument(f"$$eq" -> 599.98))
          )
        )
      }
    }

    f"combine in $$and & $$or" in {
      FilterBuilder
        .empty[Foo]
        .lt(symbol("counter"), 10)
        .or(Seq("id1", "id2")) { (filter, id) => filter.eq(symbol("id"), id) }
        .and() must_=== BSONDocument(
        f"$$and" -> BSONArray(
          BSONDocument("counter" -> BSONDocument(f"$$lt" -> 10)),
          BSONDocument(
            f"$$or" -> BSONArray(
              BSONDocument("id" -> BSONDocument(f"$$eq" -> "id1")),
              BSONDocument("id" -> BSONDocument(f"$$eq" -> "id2"))
            )
          )
        )
      )
    }

    "combine multiple operators" in {
      FilterBuilder
        .empty[Foo]
        .eq(symbol("id"), "test-id")
        .gt(symbol("score"), 50.0)
        .lt(symbol("score"), 200.0)
        .gte(symbol("counter"), 1)
        .and() must_=== BSONDocument(
        f"$$and" -> BSONArray(
          BSONDocument(f"id" -> BSONDocument(f"$$eq" -> "test-id")),
          BSONDocument(
            f"score" -> BSONDocument(f"$$gt" -> 50.0, f"$$lt" -> 200.0)
          ),
          BSONDocument(f"counter" -> BSONDocument(f"$$gte" -> 1))
        )
      )
    }

    f"combine multiple clauses in $$or" in {
      FilterBuilder
        .empty[Foo]
        .or(
          FilterBuilder.empty[Foo].eq(symbol("id"), "id1"),
          FilterBuilder.empty[Foo].gte(symbol("score"), 10.0),
          FilterBuilder.empty[Foo].lt(symbol("counter"), 5)
        )
        .result() must_=== BSONDocument(
        f"$$or" -> BSONArray(
          BSONDocument(f"id" -> BSONDocument(f"$$eq" -> "id1")),
          BSONDocument(f"score" -> BSONDocument(f"$$gte" -> 10.0)),
          BSONDocument(f"counter" -> BSONDocument(f"$$lt" -> 5))
        )
      )
    }
  }

  "Operation builder" should {
    "support not filter with eq" in {
      FilterBuilder
        .empty[Foo]
        .not(symbol("id"))
        .apply { _.eq("invalid-id") }
        .and() must_=== BSONDocument(
        "id" -> BSONDocument(f"$$not" -> BSONDocument(f"$$eq" -> "invalid-id"))
      )
    }

    "support not filter combined with other filters" in {
      FilterBuilder
        .empty[Foo]
        .not(symbol("id"))
        .apply { _.eq("invalid-id") }
        .eq(symbol("tags"), "tag1")
        .and() must_=== BSONDocument(
        f"$$and" -> BSONArray(
          BSONDocument("tags" -> BSONDocument(f"$$eq" -> "tag1")),
          BSONDocument(
            "id" -> BSONDocument(
              f"$$not" -> BSONDocument(f"$$eq" -> "invalid-id")
            )
          )
        )
      )
    }

    "support directly nested fields" in {
      FilterBuilder
        .empty[Foo]
        .nestedField(symbol("tracker"))
        .at { _.eq(symbol("id"), 12345L) }
        .result() must_=== BSONDocument(
        "tracker.id" -> BSONDocument(f"$$eq" -> 12345L)
      )
    }

    "support directly optional nested fields" in {
      FilterBuilder
        .empty[Foo]
        .nestedField(symbol("status"))
        .at {
          _.ne(symbol("name"), "Bar")
        }
        .result() must_=== BSONDocument(
        "status.name" -> BSONDocument(
          f"$$ne" -> "Bar"
        )
      )
    }
  }

  "Comment functionality" should {
    f"add $$comment to filter" in {
      FilterBuilder
        .empty[Foo]
        .eq(symbol("id"), "test-id")
        .comment("This is a test filter")
        .and() must_=== BSONDocument(
        f"$$and" -> BSONArray(
          BSONDocument(f"$$comment" -> "This is a test filter"),
          BSONDocument("id" -> BSONDocument(f"$$eq" -> "test-id"))
        )
      )
    }
  }
}
