import scala.util.{ Failure, Try }

import scala.concurrent.Await

import reactivemongo.bson.{
  BSONDocument => LegacyDocument,
  BSONObjectID => LegacyObjectID,
  BSONString => LegacyString
}

import reactivemongo.api.{
  Cursor,
  FailoverStrategy,
  ReadConcern,
  ReadPreference,
  WriteConcern
}

import reactivemongo.api.commands.{
  CommandError,
  UnitBox,
  WriteResult
}

import org.specs2.concurrent.ExecutionEnv
import org.specs2.specification.core.Fragments

import reactivemongo.api.bson.{
  BSONDocument,
  BSONInteger,
  BSONObjectID,
  BSONString,
  Macros
}

import reactivemongo.api.bson.collection.BSONCollection

final class CollectionSpec(implicit ee: ExecutionEnv)
  extends org.specs2.mutable.Specification {

  "BSON collection" title

  sequential

  import reactivemongo.api.bson.compat._

  import Common._

  case class User(
    _id: Option[BSONObjectID] = None, username: String, height: Double)

  implicit val userReads = Macros.reader[User]
  implicit val userWrites = Macros.writer[User]

  lazy val collectionName = s"test_users_${System identityHashCode this}"
  lazy val legacyColl = db(collectionName)
  lazy val collection = new BSONCollection(
    db, collectionName, new FailoverStrategy(), db.defaultReadPreference)

  "BSONCollection.save" should {
    "add object if there does not exist in database" in {
      // Check current document does not exist
      val query = LegacyDocument("username" -> LegacyString("John Doe"))

      legacyColl.find(query, Option.empty[BSONDocument]).
        one[BSONDocument] must beNone.await(1, timeout) and {

          // Add document..
          collection.insert.one(
            User(username = "John Doe", height = 12)) must beLike[WriteResult] {
              case result => result.ok must beTrue and {
                // Check data in mongodb..
                legacyColl.find(query, Option.empty[LegacyDocument]).
                  one[LegacyDocument] must beSome[LegacyDocument].which { d =>
                    d.get("_id") must beSome and (
                      d.get("username") must beSome(LegacyString("John Doe")))
                  }.await(1, timeout)
              }
            }.await(1, timeout)
        }
    }

    "update object there already exists in database" in {
      // Find saved object
      val fetched1 = Await.result(collection.find(
        BSONDocument("username" -> "John Doe"), Option.empty[BSONDocument]).
        one[User], timeout)

      fetched1 must beSome[User].which { u =>
        u._id.isDefined must beTrue and (u.username must_=== "John Doe")
      }

      // Update object..
      val newUser = fetched1.get.copy(username = "Jane Doe")
      val result = Await.result(
        collection.update(ordered = true).one(
          q = BSONDocument("username" -> "John Doe"),
          u = newUser,
          upsert = false,
          multi = false), timeout)
      result.ok must beTrue

      // Check data in mongodb..
      val fetched2 = Await.result(legacyColl.find(
        LegacyDocument("username" -> LegacyString("John Doe")),
        Option.empty[LegacyDocument]).one[LegacyDocument], timeout)

      fetched2 must beNone

      val fetched3 = Await.result(legacyColl.find(
        LegacyDocument("username" -> LegacyString("Jane Doe")),
        Option.empty[LegacyDocument]).one[LegacyDocument], timeout)

      fetched3 must beSome[LegacyDocument].which { d =>
        fetched1.get._id.map(
          implicitly[LegacyObjectID](_)) must beSome[LegacyObjectID].which {
            d.getAs[LegacyObjectID]("_id") must beSome(_)
          } and {
            d.get("username") must beSome(LegacyString("Jane Doe"))
          }
      }
    }

    "add object if does not exist but its field `_id` is set" in {
      // Check current document does not exist
      val query = LegacyDocument("username" -> LegacyString("Robert Roe"))
      val id = BSONObjectID.generate

      // Add document..
      collection.insert.one(User(
        _id = Some(id), username = "Robert Roe", height = 13)).map(_.ok) aka "saved" must beTrue.await(1, timeout) and {
        // Check data in mongodb..
        legacyColl.find(query, Option.empty[LegacyDocument]).
          one[LegacyDocument] must beSome[LegacyDocument].which { d =>
            d.getAs[LegacyObjectID]("_id").
              map(implicitly[BSONObjectID](_)) must beSome(id) and {
                d.get("username") must beSome(LegacyString("Robert Roe"))
              }
          }.await(1, timeout)
      }
    }

    "delete inserted user" in {
      val query = BSONDocument("username" -> "To Be Deleted")
      val id = BSONObjectID.generate
      val user = User(
        _id = Some(id), username = "To Be Deleted", height = 13)

      def find() = collection.find(query, Option.empty[BSONDocument]).one[User]

      collection.insert.one(user).map(_.ok) must beTrue.await(1, timeout) and {
        find() must beSome(user).awaitFor(timeout)
      } and {
        collection.delete.one(query).
          map(_.n) must beTypedEqualTo(1).awaitFor(timeout)
      } and {
        find() must beNone.awaitFor(timeout)
      }
    }
  }

  "BSONCollection.findAndModify" should {
    "be successful" in {
      val id = BSONObjectID.generate
      val updateOp = collection.updateModifier(
        User(
          _id = Some(id),
          username = "James Joyce", height = 1.264290338792695E+64),
        fetchNewObject = false, upsert = true)

      collection.findAndModify(
        selector = BSONDocument("_id" -> id),
        modifier = updateOp,
        sort = None,
        fields = None,
        bypassDocumentValidation = false,
        writeConcern = WriteConcern.Default,
        maxTime = None,
        collation = None,
        arrayFilters = Seq.empty).
        map(_.result[BSONDocument]) must beNone.await(1, timeout)
    }
  }

  "JSON QueryBuilder.merge" should {
    import reactivemongo.api.tests

    section("mongo2")
    "for MongoDB 2.6" >> {
      "write an BSONDocument with mongo query only if there are not options defined" in {
        val builder = tests.queryBuilder(collection).
          filter(BSONDocument("username" -> "John Doe"))

        val expected = BSONDocument(
          f"$$query" -> BSONDocument("username" -> "John Doe"),
          f"$$readPreference" -> BSONDocument("mode" -> "primary"))

        tests.merge(builder, ReadPreference.Primary) must_=== expected
      }

      "write an BSONDocument with only defined options" >> {
        val builder1 = tests.queryBuilder(collection).
          filter(BSONDocument("username" -> "John Doe")).
          sort(BSONDocument("age" -> 1))

        "with query builder #1" in {
          val expected = BSONDocument(
            f"$$query" -> BSONDocument("username" -> "John Doe"),
            f"$$orderby" -> BSONDocument("age" -> 1),
            f"$$readPreference" -> BSONDocument("mode" -> "primary"))

          tests.merge(builder1, ReadPreference.Primary) must_=== expected
        }

        "with query builder #2" in {
          val builder2 = builder1.comment("get john doe users sorted by age")

          val expected = BSONDocument(
            f"$$query" -> BSONDocument("username" -> "John Doe"),
            f"$$orderby" -> BSONDocument("age" -> 1),
            f"$$comment" -> "get john doe users sorted by age",
            f"$$readPreference" -> BSONDocument("mode" -> "primary"))

          tests.merge(builder2, ReadPreference.Primary) must_=== expected
        }
      }
    }
    section("mongo2")

    section("gt_mongo32")
    "for MongoDB >3.2" >> {
      "write an BSONDocument with mongo query only if there are not options defined" in {
        val builder = tests.queryBuilder(collection).
          filter(BSONDocument("username" -> "John Doe"))

        val expected = BSONDocument(
          "find" -> collection.name,
          "skip" -> 0,
          "tailable" -> false,
          "awaitData" -> false,
          "oplogReplay" -> false,
          "filter" -> BSONDocument("username" -> "John Doe"),
          "readConcern" -> BSONDocument("level" -> "local"),
          f"$$readPreference" -> BSONDocument("mode" -> "primary"))

        tests.merge(builder, ReadPreference.Primary) must_=== expected
      }

      "write an BSONDocument with only defined options" >> {
        val builder1 = tests.queryBuilder(collection).
          filter(BSONDocument("username" -> "John Doe")).
          sort(BSONDocument("age" -> 1))

        "with query builder #1" in {
          val expected = BSONDocument(
            "find" -> collection.name,
            "skip" -> 0,
            "tailable" -> false,
            "awaitData" -> false,
            "oplogReplay" -> false,
            "filter" -> BSONDocument("username" -> "John Doe"),
            "sort" -> BSONDocument("age" -> 1),
            "readConcern" -> BSONDocument("level" -> "local"),
            f"$$readPreference" -> BSONDocument("mode" -> "primary"))

          tests.merge(builder1, ReadPreference.Primary) must_=== expected
        }

        "with query builder #2" in {
          val builder2 = builder1.comment("get john doe users sorted by age")

          val expected = BSONDocument(
            "find" -> collection.name,
            "skip" -> 0,
            "tailable" -> false,
            "awaitData" -> false,
            "oplogReplay" -> false,
            "filter" -> BSONDocument("username" -> "John Doe"),
            "sort" -> BSONDocument("age" -> 1),
            "comment" -> "get john doe users sorted by age",
            "readConcern" -> BSONDocument("level" -> "local"),
            f"$$readPreference" -> BSONDocument("mode" -> "primary"))

          tests.merge(builder2, ReadPreference.Primary) must_=== expected
        }
      }
    }
    section("gt_mongo32")
  }

  "JSON collection" should {
    @inline def cursorAll: Cursor[BSONDocument] =
      collection.withReadPreference(ReadPreference.secondaryPreferred).
        find(BSONDocument.empty, Option.empty[BSONDocument]).cursor[BSONDocument]()

    "use read preference from the collection" in {
      import scala.language.reflectiveCalls
      val withPref = cursorAll.asInstanceOf[{ def preference: ReadPreference }]

      withPref.preference must_=== ReadPreference.secondaryPreferred
    }

    "find with empty criteria document" in {
      collection.find(BSONDocument.empty, Option.empty[BSONDocument]).
        sort(BSONDocument("updated" -> -1)).cursor[BSONDocument]().
        collect[List](
          Int.MaxValue, Cursor.FailOnError[List[BSONDocument]]()).
          aka("find with empty document") must not(throwA[Throwable]).
          await(1, timeout)
    }

    "find with selector and projection" in {
      collection.find(
        selector = BSONDocument("username" -> "Jane Doe"),
        projection = Option(BSONDocument("_id" -> 0))).
        cursor[BSONDocument]().headOption must beSome[BSONDocument].which {
          BSONDocument.pretty(_) must beTypedEqualTo("""{
  'username': 'Jane Doe',
  'height': 12.0
}""")

        }.await(1, timeout)
    }

    "count all matching document" in {
      def count(
        selector: Option[BSONDocument] = None,
        limit: Option[Int] = None,
        skip: Int = 0) = collection.count(
        selector, limit, skip, None, ReadConcern.Local)

      count() aka "all" must beTypedEqualTo(3L).await(1, timeout) and (
        count(Some(BSONDocument("username" -> "Jane Doe"))).
        aka("with query") must beTypedEqualTo(1L).await(1, timeout)) and {
          count(limit = Some(1)) must beTypedEqualTo(1L).await(1, timeout)
        }
    }
  }

  "Command result" should {
    import reactivemongo.api.bson.collection._

    val mini = BSONDocument("ok" -> 0)
    val withCode = mini ++ ("code" -> BSONInteger(1))
    val withErrmsg = mini ++ ("errmsg" -> BSONString("Foo"))
    val full = withCode ++ withErrmsg

    type Fixture = (BSONDocument, Boolean, Boolean)

    val pack = BSONSerializationPack
    val reader: pack.Reader[UnitBox.type] = CommonImplicits.UnitBoxReader

    Fragments.foreach(Seq[Fixture](
      (mini, false, false),
      (withCode, true, false),
      (withErrmsg, false, true),
      (full, true, true))) {
      case (origDoc, hasCode, hasErrmsg) =>
        s"represent CommandError from '${BSONDocument pretty origDoc}'" in {
          val res = Try(pack.deserialize[UnitBox.type](origDoc, reader))

          val mustHasCode = if (!hasCode) ok else {
            res aka "has code" must beLike {
              case Failure(CommandError.Code(1)) => ok
            }
          }

          val mustHasErrmsg = if (!hasErrmsg) ok else {
            res aka "has errmsg" must beLike {
              case Failure(CommandError.Message("Foo")) => ok
            }
          }

          mustHasCode and mustHasErrmsg
        }
    }
  }
}
