import java.time.OffsetDateTime

import reactivemongo.api.bson.{
  BSONArray,
  BSONDocument,
  BSONInteger,
  BSONString
}
import reactivemongo.api.bson.builder.{ DetectedAt, Foo, UpdateBuilder }
import reactivemongo.api.bson.builder.TestUtils.{ symbol, typecheck }

import org.specs2.matcher.TypecheckMatchers._

final class UpdateBuilderSpec
    extends org.specs2.mutable.Specification
    with UpdateBuilderSpecCompat {

  "UpdateBuilder".title

  "Basic update operations" should {
    f"support $$set operator" in {
      UpdateBuilder
        .empty[Foo]
        .set(symbol("id"), "test-id")
        .result() must_=== BSONDocument(
        f"$$set" -> BSONDocument("id" -> "test-id")
      )
    }

    f"support $$set with multiple fields" in {
      UpdateBuilder
        .empty[Foo]
        .set(symbol("id"), "test-id")
        .set(symbol("counter"), 42)
        .result() must_=== BSONDocument(
        f"$$set" -> BSONDocument(
          "id" -> "test-id",
          "counter" -> 42
        )
      )
    }

    f"support $$unset operator" in {
      UpdateBuilder
        .empty[Foo]
        .unset(symbol("status"))
        .result() must_=== BSONDocument(
        f"$$unset" -> BSONDocument("status" -> 1)
      )
    }

    f"do not support $$unset for a non-optional field" in {
      typecheck(
        """UpdateBuilder.empty[Foo].unset(symbol("id"))"""
      ) must failWith(".*")
    }

    f"support $$inc operator" in {
      UpdateBuilder
        .empty[Foo]
        .inc(symbol("counter"), 1)
        .result() must_=== BSONDocument(
        f"$$inc" -> BSONDocument("counter" -> 1)
      )
    }

    f"support $$inc with negative value" in {
      UpdateBuilder
        .empty[Foo]
        .inc(symbol("counter"), -5)
        .result() must_=== BSONDocument(
        f"$$inc" -> BSONDocument("counter" -> -5)
      )
    }

    f"support $$mul operator" in {
      UpdateBuilder
        .empty[Foo]
        .mul(symbol("counter"), 2)
        .result() must_=== BSONDocument(
        f"$$mul" -> BSONDocument("counter" -> 2)
      )
    }

    f"support $$mul with decimal" in {
      UpdateBuilder
        .empty[Foo]
        .mul(symbol("score"), 1.5)
        .result() must_=== BSONDocument(
        f"$$mul" -> BSONDocument("score" -> 1.5)
      )
    }

    f"support $$rename operator" in {
      UpdateBuilder
        .empty[Foo]
        .rename(symbol("id"), "identifier")
        .result() must_=== BSONDocument(
        f"$$rename" -> BSONDocument("id" -> "identifier")
      )
    }

    f"support $$currentDate with date type" in {
      UpdateBuilder
        .empty[DetectedAt]
        .currentDate(
          symbol("updated"),
          UpdateBuilder.CurrentDateType.Date
        )
        .result() must_=== BSONDocument(
        f"$$currentDate" -> BSONDocument(
          "updated" -> BSONDocument(f"$$type" -> "date")
        )
      )
    }

    f"support $$currentDate with timestamp type" in {
      UpdateBuilder
        .empty[DetectedAt]
        .currentDate(
          symbol("updated"),
          UpdateBuilder.CurrentDateType.Timestamp
        )
        .result() must_=== BSONDocument(
        f"$$currentDate" -> BSONDocument(
          "updated" -> BSONDocument(f"$$type" -> "timestamp")
        )
      )
    }
  }

  "Comparison operations" should {
    f"support $$max operator" in {
      UpdateBuilder
        .empty[Foo]
        .max(symbol("counter"), 100)
        .result() must_=== BSONDocument(
        f"$$max" -> BSONDocument("counter" -> 100)
      )
    }

    f"support $$min operator" in {
      UpdateBuilder
        .empty[Foo]
        .min(symbol("counter"), 5)
        .result() must_=== BSONDocument(
        f"$$min" -> BSONDocument("counter" -> 5)
      )
    }

    "combine max and min operations" in {
      UpdateBuilder
        .empty[Foo]
        .max(symbol("counter"), 100)
        .min(symbol("quantity"), 1L)
        .result() must_=== BSONDocument(
        f"$$max" -> BSONDocument("counter" -> 100),
        f"$$min" -> BSONDocument("quantity" -> 1L)
      )
    }
  }

  "Array operations" should {
    f"support $$push operator" in {
      UpdateBuilder
        .empty[Foo]
        .push(symbol("tags"), "scala")
        .result() must_=== BSONDocument(
        f"$$push" -> BSONDocument("tags" -> "scala")
      )
    }

    f"support $$push with multiple values" in {
      UpdateBuilder
        .empty[Foo]
        .push(symbol("tags"), "scala")
        .push(symbol("tags"), "mongodb")
        .result() must_=== BSONDocument(
        f"$$push" -> BSONDocument(
          "tags" -> "scala",
          "tags" -> "mongodb"
        )
      )
    }

    f"support $$addToSet operator" in {
      UpdateBuilder
        .empty[Foo]
        .addToSet(symbol("tags"), "unique-tag")
        .result() must_=== BSONDocument(
        f"$$addToSet" -> BSONDocument("tags" -> "unique-tag")
      )
    }

    f"support $$addToSet with $$each modifier" in {
      val tags = Seq("tag1", "tag2", "tag3")

      UpdateBuilder
        .empty[Foo]
        .addToSetEach(symbol("tags"), tags)
        .result() must_=== BSONDocument(
        f"$$addToSet" -> BSONDocument(
          "tags" -> BSONDocument(
            f"$$each" -> BSONArray("tag1", "tag2", "tag3")
          )
        )
      )
    }

    f"support $$pop with last strategy" in {
      UpdateBuilder
        .empty[Foo]
        .pop(symbol("tags"), UpdateBuilder.PopStrategy.Last)
        .result() must_=== BSONDocument(
        f"$$pop" -> BSONDocument("tags" -> 1)
      )
    }

    f"support $$pop with first strategy" in {
      UpdateBuilder
        .empty[Foo]
        .pop(symbol("tags"), UpdateBuilder.PopStrategy.First)
        .result() must_=== BSONDocument(
        f"$$pop" -> BSONDocument("tags" -> -1)
      )
    }

    f"support $$pull operator" in {
      UpdateBuilder
        .empty[Foo]
        .pull(symbol("tags"), "old-tag")
        .result() must_=== BSONDocument(
        f"$$pull" -> BSONDocument("tags" -> "old-tag")
      )
    }

    f"support $$pullAll operator" in {
      val tagsToRemove = Seq("old", "deprecated")

      UpdateBuilder
        .empty[Foo]
        .pullAll(symbol("tags"), tagsToRemove)
        .result() must_=== BSONDocument(
        f"$$pullAll" -> BSONDocument(
          "tags" -> BSONArray("old", "deprecated")
        )
      )
    }

    f"support $$pull with expression" in {
      val condition = BSONDocument(f"$$gte" -> 100)

      UpdateBuilder
        .empty[Foo]
        .pullExpr(symbol("tags"), condition)
        .result() must_=== BSONDocument(
        f"$$pull" -> BSONDocument("tags" -> condition)
      )
    }
  }

  "Advanced array operations" should {
    f"support $$push with $$each and $$slice" in {
      UpdateBuilder
        .empty[Foo]
        .pushEach(
          symbol("tags"),
          Seq("tag1", "tag2", "tag3"),
          slice = Some(UpdateBuilder.PushSlice.Last(10))
        )
        .result() must_=== BSONDocument(
        f"$$push" -> BSONDocument(
          "tags" -> BSONDocument(
            f"$$each" -> BSONArray("tag1", "tag2", "tag3"),
            f"$$slice" -> -10
          )
        )
      )
    }

    f"support $$push with $$each and $$sort ascending" in {
      UpdateBuilder
        .empty[Foo]
        .pushEach(
          symbol("tags"),
          Seq("z", "a", "m"),
          sort = Some(UpdateBuilder.PushSort.Ascending)
        )
        .result() must_=== BSONDocument(
        f"$$push" -> BSONDocument(
          "tags" -> BSONDocument(
            f"$$each" -> BSONArray("z", "a", "m"),
            f"$$sort" -> 1
          )
        )
      )
    }

    f"support $$push with $$each and $$sort descending" in {
      UpdateBuilder
        .empty[Foo]
        .pushEach(
          symbol("tags"),
          Seq("z", "a", "m"),
          sort = Some(UpdateBuilder.PushSort.Descending)
        )
        .result() must_=== BSONDocument(
        f"$$push" -> BSONDocument(
          "tags" -> BSONDocument(
            f"$$each" -> BSONArray("z", "a", "m"),
            f"$$sort" -> -1
          )
        )
      )
    }

    f"support $$push with $$each and $$position" in {
      UpdateBuilder
        .empty[Foo]
        .pushEach(
          symbol("tags"),
          Seq("first", "second"),
          position = Some(0)
        )
        .result() must_=== BSONDocument(
        f"$$push" -> BSONDocument(
          "tags" -> BSONDocument(
            f"$$each" -> BSONArray("first", "second"),
            f"$$position" -> 0
          )
        )
      )
    }

    f"support $$push with all modifiers" in {
      UpdateBuilder
        .empty[Foo]
        .pushEach(
          symbol("tags"),
          Seq("new1", "new2"),
          slice = Some(UpdateBuilder.PushSlice.First(5)),
          sort = Some(UpdateBuilder.PushSort.Ascending),
          position = Some(2)
        )
        .result() must_=== BSONDocument(
        f"$$push" -> BSONDocument(
          "tags" -> BSONDocument(
            f"$$each" -> BSONArray("new1", "new2"),
            f"$$slice" -> 5,
            f"$$sort" -> 1,
            f"$$position" -> 2
          )
        )
      )
    }
  }

  "Mixed operations" should {
    "combine multiple update operators" in {
      UpdateBuilder
        .empty[Foo]
        .set(symbol("id"), "updated-id")
        .inc(symbol("counter"), 1)
        .push(symbol("tags"), "new-tag")
        .result() must_=== BSONDocument(
        f"$$set" -> BSONDocument("id" -> "updated-id"),
        f"$$inc" -> BSONDocument("counter" -> 1),
        f"$$push" -> BSONDocument("tags" -> "new-tag")
      )
    }

    "combine set, unset, and inc" in {
      UpdateBuilder
        .empty[Foo]
        .set(symbol("id"), "test-id")
        .unset(symbol("status"))
        .inc(symbol("counter"), 5)
        .result() must_=== BSONDocument(
        f"$$set" -> BSONDocument("id" -> "test-id"),
        f"$$unset" -> BSONDocument("status" -> 1),
        f"$$inc" -> BSONDocument("counter" -> 5)
      )
    }

    "support comprehensive operations mix" in {
      UpdateBuilder
        .empty[Foo]
        .set(symbol("id"), "final-id")
        .inc(symbol("counter"), 10)
        .mul(symbol("quantity"), 2L)
        .max(symbol("counter"), 50)
        .min(symbol("quantity"), 1L)
        .push(symbol("tags"), "new")
        .addToSet(symbol("tags"), "unique")
        .result() must_=== BSONDocument(
        f"$$set" -> BSONDocument("id" -> "final-id"),
        f"$$inc" -> BSONDocument("counter" -> 10),
        f"$$mul" -> BSONDocument("quantity" -> 2L),
        f"$$max" -> BSONDocument("counter" -> 50),
        f"$$min" -> BSONDocument("quantity" -> 1L),
        f"$$push" -> BSONDocument("tags" -> "new"),
        f"$$addToSet" -> BSONDocument("tags" -> "unique")
      )
    }

    "combine array operations" in {
      UpdateBuilder
        .empty[Foo]
        .addToSet(symbol("tags"), "unique-item")
        .pop(symbol("tags"), UpdateBuilder.PopStrategy.Last)
        .result() must_=== BSONDocument(
        f"$$addToSet" -> BSONDocument("tags" -> "unique-item"),
        f"$$pop" -> BSONDocument("tags" -> 1)
      )
    }
  }

  "Nested field operations" should {
    "support single nested field update" in {
      val time = OffsetDateTime.now()

      UpdateBuilder
        .empty[Foo]
        .nestedField(symbol("location"))
        .at { _.set(symbol("updated"), time) }
        .result() must_=== BSONDocument(
        f"$$set" -> BSONDocument("location.updated" -> time)
      )
    }

    "support multiple nested field updates" in {
      val time = OffsetDateTime.now()

      UpdateBuilder
        .empty[Foo]
        .nestedField(symbol("status"))
        .at {
          _.set(symbol("updated"), time).set(symbol("name"), "Foo")
        }
        .result() must_=== BSONDocument(
        f"$$set" -> BSONDocument(
          "status.updated" -> time,
          "status.name" -> "Foo"
        )
      )
    }

    "support nested field with different operations" in {
      val time = OffsetDateTime.now()

      UpdateBuilder
        .empty[Foo]
        .nestedField(symbol("location"))
        .at { nested =>
          nested.set(symbol("updated"), time).unset(symbol("detectedAt"))
        }
        .result() must_=== BSONDocument(
        f"$$set" -> BSONDocument("location.updated" -> time),
        f"$$unset" -> BSONDocument("location.detectedAt" -> 1)
      )
    }

    "combine nested and regular updates" in {
      UpdateBuilder
        .empty[Foo]
        .set(symbol("id"), "user-123")
        .nestedField(symbol("status"))
        .at { _.set(symbol("name"), "Bar") }
        .inc(symbol("counter"), 1)
        .result() must_=== BSONDocument(
        f"$$set" -> BSONDocument(
          "id" -> "user-123",
          "status.name" -> "Bar"
        ),
        f"$$inc" -> BSONDocument("counter" -> 1)
      )
    }

    "support multiple different nested objects" in {
      UpdateBuilder
        .empty[Foo]
        .nestedField(symbol("status"))
        .at { _.set(symbol("name"), "Lorem") }
        .nestedField(symbol("tracker"))
        .at { _.set(symbol("id"), 12345L) }
        .result() must_=== BSONDocument(
        f"$$set" -> BSONDocument(
          "status.name" -> "Lorem",
          "tracker.id" -> 12345L
        )
      )
    }

  }

  "Conditional operations" should {
    "support ifSome with defined value" in {
      val maybeEmail: Option[String] = Some("user@example.com")

      UpdateBuilder
        .empty[Foo]
        .ifSome(maybeEmail) { (builder, email) =>
          builder.set(symbol("id"), email)
        }
        .result() must_=== BSONDocument(
        f"$$set" -> BSONDocument("id" -> "user@example.com")
      )
    }

    "support ifSome with None value" in {
      val maybeEmail: Option[String] = None

      UpdateBuilder
        .empty[Foo]
        .ifSome(maybeEmail) { (builder, email) =>
          builder.set(symbol("id"), email)
        }
        .result() must_=== BSONDocument.empty
    }

    "support ifSome combined with other operations" in {
      val maybeName: Option[String] = Some("Alice")

      UpdateBuilder
        .empty[Foo]
        .set(symbol("id"), "test-id")
        .ifSome(maybeName) { (builder, name) =>
          builder.set(symbol("counter"), name.length)
        }
        .inc(symbol("quantity"), 1L)
        .result() must_=== BSONDocument(
        f"$$set" -> BSONDocument(
          "id" -> "test-id",
          "counter" -> 5
        ),
        f"$$inc" -> BSONDocument("quantity" -> 1L)
      )
    }
  }

  "Empty builder" should {
    "return empty document" in {
      UpdateBuilder.empty[Foo].result() must_=== BSONDocument.empty
    }

    "allow chaining after creation" in {
      val builder = UpdateBuilder.empty[Foo]

      builder.set(
        symbol("id"),
        "test"
      )

      builder.result() must_=== BSONDocument(
        f"$$set" -> BSONDocument("id" -> "test")
      )
    }
  }

  "Untyped operations" should {
    "allow custom untyped updates" in {
      UpdateBuilder
        .empty[Foo]
        .untyped(
          symbol("id"),
          f"$$set",
          BSONString("custom-value")
        )
        .result() must_=== BSONDocument(
        f"$$set" -> BSONDocument("id" -> "custom-value")
      )
    }

    "combine untyped with typed operations" in {
      UpdateBuilder
        .empty[Foo]
        .set(
          symbol("id"),
          "typed-id"
        )
        .untyped(symbol("counter"), f"$$inc", BSONInteger(10))
        .result() must_=== BSONDocument(
        f"$$set" -> BSONDocument("id" -> "typed-id"),
        f"$$inc" -> BSONDocument("counter" -> 10)
      )
    }
  }
}
