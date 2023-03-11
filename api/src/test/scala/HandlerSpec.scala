package reactivemongo.api.bson

import java.util.{ Locale, UUID }

import java.net.{ URI, URL }

import java.time.{
  Instant,
  LocalDate,
  LocalDateTime,
  LocalTime,
  OffsetDateTime,
  ZoneId,
  ZonedDateTime
}

import scala.util.{ Failure, Success }

import org.specs2.specification.core.Fragments

final class HandlerSpec
    extends org.specs2.mutable.Specification
    with HandlerExtraSpec {

  "Handler".title

  import exceptions.TypeDoesNotMatchException

  "BSONBinary" should {
    "be read as byte array" in {
      val bytes = Array[Byte](1, 3, 5, 7)
      val bin = BSONBinary(bytes, Subtype.GenericBinarySubtype)

      bin.asTry[Array[Byte]] aka "read #1" must beSuccessfulTry(bytes) and {
        bin.asTry[Array[Byte]] aka "read #2" must beSuccessfulTry(bytes)
      }
    }

    "be read as UUID" in {
      val uuid = UUID.randomUUID()

      BSONBinary(uuid).asTry[UUID] must beSuccessfulTry(uuid)
    }
  }

  "Complex Document" should {
    "have a name == 'James'" in {
      doc.getAsTry[BSONString]("name") must beSuccessfulTry(
        BSONString("James")
      ) and {
        doc.getAsTry[String]("name") must beSuccessfulTry("James")
      } and {
        doc.getRawAsTry[BSONString]("name") must beSuccessfulTry(
          BSONString("James")
        )
      } and {
        doc.getRawAsTry[String]("name") must beSuccessfulTry("James")
      } and {
        doc.getAsTry[BSONInteger]("name") must beFailedTry
      } and {
        doc.getAsOpt[BSONInteger]("name") must beNone
      } and {
        doc.getOrElse[BSONInteger](
          "name",
          BSONInteger(-1)
        ) must_=== BSONInteger(-1)

      } and {
        doc.getAsTry[Int]("name") must beFailedTry
      } and {
        doc.getAsOpt[Int]("name") must beNone
      } and {
        doc.getOrElse[Int]("name", -1) must_=== -1
      } and {
        doc.getOrElse[String]("name", "foo") must_=== "James"
      } and {
        doc.getAsTry[BSONNumberLike]("name") must beFailedTry
      } and {
        doc.get("name").get.asTry[String] must beSuccessfulTry("James")
      } and {
        doc.get("name").get.asTry[Int] must beFailedTry
      } and {
        doc.get("name").get.asOpt[String] must beSome("James")
      } and {
        implicit def r: BSONReader[Option[String]] =
          BSONReader[Option[String]] {
            case BSONString(str) => Some(str)
            case _               => None
          }

        doc.getRawAsTry[Option[String]]("missing") must beSuccessfulTry(
          Option.empty[String]
        ) and {
          doc.getRawAsTry[Option[String]]("name") must beSuccessfulTry(
            Some("James")
          )
        }
      }
    }

    "have a score == 3.88" in {
      doc.getAsTry[BSONDouble]("score") must beSuccessfulTry(BSONDouble(3.88))
      doc.getAsTry[Double]("score") must beSuccessfulTry(3.88)

      doc.getAsTry[BSONInteger]("score") must beFailedTry
      doc.getAsTry[Int]("score") must beFailedTry

      doc.getAsTry[BSONNumberLike]("score") must beSuccessfulTry.like {
        case num =>
          num.toDouble must beSuccessfulTry(3.88) and {
            num.toFloat must beSuccessfulTry(3.88F)
          } and {
            num.toLong must beSuccessfulTry(3L)
          } and {
            num.toInt must beSuccessfulTry(3)
          }
      } and {
        doc
          .getAsTry[BSONBooleanLike]("score")
          .flatMap(_.toBoolean) must beSuccessfulTry(true)
      }
    }

    "be read" in {
      val reader1 = BSONDocumentReader.from(_.getAsTry[String]("name"))

      reader1.readTry(doc) must beSuccessfulTry("James") and {
        val before: PartialFunction[BSONDocument, BSONDocument] = {
          case _: BSONDocument => BSONDocument("name" -> "John")
        }
        val reader2: BSONDocumentReader[String] = reader1.beforeRead(before)
        val reader3: BSONDocumentReader[String] =
          reader1.beforeReadTry(_ => Success(BSONDocument("name" -> "John")))

        val handler1 = BSONDocumentHandler.provided[String](
          reader2,
          writer = BSONDocumentWriter[String] { _ => ??? }
        )

        // Make sure the type BSONDocumentHandler is preserved
        val handler2: BSONDocumentHandler[String] = handler1.beforeRead(before)
        val handler3: BSONDocumentHandler[String] =
          handler1.beforeReadTry { (_: BSONDocument) =>
            Success(BSONDocument("name" -> "John"))
          }

        reader2.readTry(doc) must beSuccessfulTry("John") and {
          reader3.readTry(doc) must beSuccessfulTry("John")
        } and {
          handler2.readOpt(doc) must beSome("John")
        } and {
          handler3.readOpt(doc) must beSome("John")
        }
      } and {
        val reader3: BSONDocumentReader[Unit] = reader1.afterRead(_ => ())

        reader3.readTry(doc) must_=== Success({})
      }
    }

    "be written" in {
      val writer1 = BSONDocumentWriter { (s: String) =>
        BSONDocument(f"$$foo" -> s)
      }
      val expected1 = BSONDocument(f"$$foo" -> "bar")

      val zero = BSONDocument("_value" -> "zero")
      val one = BSONDocument("_value" -> "one")

      def partialSpec(w: BSONDocumentWriter[Int]) = {
        w.writeTry(0) must beSuccessfulTry(zero) and {
          w.writeOpt(0) must beSome(zero)
        } and {
          w.writeTry(1) must beSuccessfulTry(one)
        } and {
          w.writeOpt(1) must beSome(one)
        } and {
          w.writeTry(2) must beFailedTry[BSONDocument]
        } and {
          w.writeOpt(2) must beNone
        }
      }

      writer1.writeTry("bar") must beSuccessfulTry(expected1) and {
        val writer2: BSONDocumentWriter[Int] =
          writer1.beforeWrite[Int](_.toString)

        writer2.writeTry(10) must beSuccessfulTry(
          BSONDocument(f"$$foo" -> "10")
        ) and {
          writer2.writeOpt(10) must beSome(BSONDocument(f"$$foo" -> "10"))
        }
      } and {
        val expected = BSONDocument("lorem" -> "ipsum")
        val writer3: BSONDocumentWriter[String] = writer1.afterWrite {
          case _ => expected
        }

        val writer4: BSONDocumentWriter[String] =
          writer1.afterWriteTry(_ => Success(expected))

        val handler1 = BSONDocumentHandler.provided[String](
          reader = BSONDocumentReader[String] { _ => ??? },
          writer = writer3
        )

        // Make sure type BSONDocumentHandler is preserved with afterWrite
        val handler2: BSONDocumentHandler[String] = handler1.afterWrite {
          case _ => expected
        }

        val handler3: BSONDocumentHandler[String] =
          handler1.afterWriteTry(_ => Success(expected))

        writer3.writeTry("test") must beSuccessfulTry(expected) and {
          writer3.writeOpt("test") must beSome(expected)
        } and {
          writer4.writeTry("test") must beSuccessfulTry(expected)
        } and {
          writer4.writeOpt("test") must beSome(expected)
        } and {
          handler2.writeTry("test") must beSuccessfulTry(expected)
        } and {
          handler3.writeTry("test") must beSuccessfulTry(expected)
        }
      } and {
        partialSpec(BSONDocumentWriter.option[Int] {
          case 0 => Some(zero)
          case 1 => Some(one)
          case _ => None
        })
      } and {
        partialSpec(BSONDocumentWriter.collect[Int] {
          case 0 => zero
          case 1 => one
        })
      } and {
        partialSpec(BSONDocumentWriter.collectFrom[Int] {
          case 0 => Success(zero)
          case 1 => Success(one)
        })
      }
    }
  }

  "Complex Array" should {
    "be of size = 6" in {
      array.size must_=== 6
    }

    "have a an int = 2 at index 2" in {
      array.get(1) must beSome(BSONInteger(1)) and (array.getAsOpt[Int](
        1
      ) must beSome(1))
    }

    "get bsondocument at index 3" in {
      array.getAsOpt[BSONDocument](3) must beSome.which {
        _.getAsOpt[String]("name") must beSome("Joe")
      }
    }

    "get bsonarray at index 4" in {
      val tdoc = array.getAsTry[BSONDocument](4)

      tdoc must beFailedTry.withThrowable[TypeDoesNotMatchException]

      array.getAsTry[BSONArray](4) must beSuccessfulTry[BSONArray].which {
        tarray =>
          tarray.getAsOpt[BSONLong](0) must beSome(BSONLong(0L)) and (tarray
            .getAsOpt[BSONBooleanLike](0)
            .map(_.toBoolean) must beSome(Success(false)))
      }
    }
  }

  "Map" should {
    import reactivemongo.api.bson

    "with primitive/safe values" >> {
      "write" in {
        val input = Map("a" -> 1, "b" -> 2)
        val result1 = bson.mapKeyWriter[String, Int].writeTry(input)
        def result2 = bson.mapSafeWriter[Int].writeTry(input)

        result1 must beSuccessfulTry(BSONDocument("a" -> 1, "b" -> 2)) and {
          result2 must_=== result1
        }
      }

      "read" in {
        val input = BSONDocument("a" -> 1, "b" -> 2)
        val handler = implicitly[BSONReader[Map[String, Int]]]

        handler.readTry(input) must beSuccessfulTry(
          Map("a" -> 1, "b" -> 2)
        ) and {
          implicitly[BSONReader[Map[Char, Int]]]
            .readTry(input) must beSuccessfulTry(Map('a' -> 1, 'b' -> 2))
        }
      }
    }

    "with BSON values" >> {
      "write" in {
        val input = Map("a" -> BSONInteger(1), "b" -> BSONInteger(2))
        val result1 = bson.mapKeyWriter[String, BSONInteger].writeTry(input)
        def result2 = bson.bsonMapWriter[BSONInteger].writeTry(input)

        result1 must beSuccessfulTry(BSONDocument("a" -> 1, "b" -> 2)) and {
          result2 must_=== result1
        }
      }

      "read" in {
        val input = BSONDocument("a" -> 1, "b" -> 2)
        val handler = implicitly[BSONReader[Map[String, BSONInteger]]]

        handler.readTry(input) must beSuccessfulTry(
          Map("a" -> BSONInteger(1), "b" -> BSONInteger(2))
        )
      }
    }

    "with structured values" >> {
      case class Foo(label: String, count: Int)
      implicit val fooWriter: BSONDocumentWriter[Foo] =
        BSONDocumentWriter[Foo] { foo =>
          BSONDocument("label" -> foo.label, "count" -> foo.count)
        }

      implicit val fooReader: BSONDocumentReader[Foo] =
        BSONDocumentReader.from[Foo] { document =>
          for {
            label <- document.getAsTry[String]("label")
            count <- document.getAsTry[Int]("count")
          } yield Foo(label, count)
        }

      "write" in {
        val expectedResult = BSONDocument(
          "a" -> BSONDocument("label" -> "foo", "count" -> 10),
          "b" -> BSONDocument("label" -> "foo2", "count" -> 20)
        )
        val input = Map("a" -> Foo("foo", 10), "b" -> Foo("foo2", 20))
        val result = mapWriter[Foo].writeTry(input)

        result must beSuccessfulTry(expectedResult)
      }

      "read" in {
        val expectedResult = Map("a" -> Foo("foo", 10), "b" -> Foo("foo2", 20))
        val input = BSONDocument(
          "a" -> BSONDocument("label" -> "foo", "count" -> 10),
          "b" -> BSONDocument("label" -> "foo2", "count" -> 20)
        )
        val handler = implicitly[BSONReader[Map[String, Foo]]]

        handler.readTry(input) must_=== Success(expectedResult)
      }

      "fail" in {
        val input = BSONDocument(
          "a" -> BSONDocument("label" -> "foo", "count" -> 10),
          "b" -> BSONDocument("wrong" -> "foo2")
        )
        val handler = implicitly[BSONReader[Map[String, Foo]]]

        handler.readTry(input) must beFailedTry
      }
    }

    "support keys" >> {
      "with Locale type as language tag" in {
        val kr = implicitly[KeyReader[Locale]]
        val kw = implicitly[KeyWriter[Locale]]

        val locale = Locale.FRANCE
        val repr = locale.toLanguageTag

        kw.writeTry(locale) must beSuccessfulTry(repr) and {
          kr.readTry(repr) must beSuccessfulTry(locale)
        }
      }

      "with UUID type" in {
        val kr = implicitly[KeyReader[UUID]]
        val kw = implicitly[KeyWriter[UUID]]

        val uuid = UUID.randomUUID()
        val repr = uuid.toString

        kw.writeTry(uuid) must beSuccessfulTry(repr) and {
          kr.readTry(repr) must beSuccessfulTry(uuid)
        }
      }
    }

    "fails from array" in {
      BSONDocument("foo" -> BSONArray()).getAsTry[BSONDocument](
        "foo"
      ) must_=== Failure(TypeDoesNotMatchException("BSONDocument", "BSONArray"))
    }
  }

  "BSONDateTime" should {
    val time = System.currentTimeMillis()
    val bson = BSONDateTime(time)
    val instant = Instant.ofEpochMilli(time)
    val defaultZone = ZoneId.systemDefault
    val offset = defaultZone.getRules.getOffset(instant)
    val localDateTime =
      LocalDateTime.ofEpochSecond(time / 1000, instant.getNano, offset)

    "as Instant" >> {
      val handler = implicitly[BSONHandler[Instant]]

      "be read" in {
        handler.readTry(bson) must beSuccessfulTry(instant)
      }

      "be written" in {
        handler.writeTry(instant) must beSuccessfulTry(bson)
      }
    }

    "as LocalDateTime" >> {
      val handler = implicitly[BSONHandler[LocalDateTime]]

      "be read" in {
        handler.readTry(bson) must beSuccessfulTry(localDateTime)
      }

      "be written" in {
        handler.writeTry(localDateTime) must beSuccessfulTry(bson)
      }
    }

    "as LocalDate" >> {
      val handler = implicitly[BSONHandler[LocalDate]]
      val localDate = localDateTime.toLocalDate
      val dateBson =
        BSONDateTime(localDate.atStartOfDay.toEpochSecond(offset) * 1000)

      "be read" in {
        handler.readTry(bson) must beSuccessfulTry(localDate) and {
          handler.readTry(dateBson) must beSuccessfulTry(localDate)
        }
      }

      "be written" in {
        handler.writeTry(localDate) must beSuccessfulTry(dateBson)
      }
    }

    "as OffsetDateTime" >> {
      val handler = implicitly[BSONHandler[OffsetDateTime]]
      val offsetDateTime = localDateTime.atOffset(offset)

      "be read" in {
        handler.readTry(bson) must beSuccessfulTry(offsetDateTime)
      }

      "be written" in {
        handler.writeTry(offsetDateTime) must beSuccessfulTry(bson)
      }
    }

    "as ZonedDateTime" >> {
      val handler = implicitly[BSONHandler[ZonedDateTime]]
      val zonedDateTime = localDateTime.atZone(defaultZone)

      "be read" in {
        handler.readTry(bson) must beSuccessfulTry(zonedDateTime)
      }

      "be written" in {
        handler.writeTry(zonedDateTime) must beSuccessfulTry(bson)
      }
    }
  }

  "BSONNumberLike" should {
    val reader = implicitly[BSONReader[BSONNumberLike]]

    "read BSONTimestamp" in {
      val time = System.nanoTime() / 1000L
      val bson = BSONTimestamp(time)

      reader.readOpt(bson).map(_.toLong) must beSome(Success(time))
    }
  }

  "Boolean" should {
    val reader = implicitly[BSONReader[BSONBooleanLike]]

    "be read from BSON null" in {
      val doc = BSONDocument("null" -> BSONNull, "undefined" -> BSONUndefined)

      reader.readOpt(BSONNull).map(_.toBoolean) must beSome(
        Success(false)
      ) and {
        doc.booleanLike("null") must beSome(false)
      } and {
        reader.readOpt(BSONUndefined).map(_.toBoolean) must beSome(
          Success(false)
        )
      } and {
        doc.booleanLike("undefined") must beSome(false)
      }
    }
  }

  "Double" should {
    val handler = implicitly[BSONHandler[Double]]

    "be read from BSONDecimal" >> {
      val dec = BSONDecimal.fromBigDecimal(BigDecimal(123.45D))

      "successfully if <= Double.MaxValue" in {
        dec.flatMap(handler.readTry) must beSuccessfulTry(123.45D)
      }

      "with error if > Double.MaxValue" in {
        val maxDouble = BigDecimal(Double.MaxValue)
        val d = BSONDecimal.fromBigDecimal(maxDouble + maxDouble)

        d.flatMap(handler.readTry) must beFailedTry[Double]
      }
    }

    "be read from BSONDouble" in {
      handler.readTry(BSONDouble(123.45D)) must beSuccessfulTry(123.45D)
    }

    "be read from BSONInteger" in {
      handler.readTry(BSONInteger(123)) must beSuccessfulTry(123D)
    }

    "be read from BSONLong" in {
      handler.readTry(BSONLong(123L)) must beSuccessfulTry(123D)
    }
  }

  "Float" should {
    val handler = implicitly[BSONHandler[Float]]

    "be read from BSONDecimal" >> {
      val dec = BSONDecimal.fromBigDecimal(BigDecimal("123.45"))

      "successfully if <= Float.MaxValue" in {
        dec.flatMap(handler.readTry) must beSuccessfulTry(123.45F)
      }

      "with error if > Float.MaxValue" in {
        val maxDouble = BigDecimal(Float.MaxValue.toDouble)
        val d = BSONDecimal.fromBigDecimal(maxDouble + maxDouble)

        d.flatMap(handler.readTry) must beFailedTry[Float]
      }
    }

    "be read from BSONDouble" in {
      handler.readTry(BSONDouble(123.45D)) must beSuccessfulTry(123.45F)
    }

    "be read from BSONInteger" in {
      handler.readTry(BSONInteger(123)) must beSuccessfulTry(123F)
    }

    "be read from BSONLong" in {
      handler.readTry(BSONLong(123L)) must beSuccessfulTry(123F)
    }
  }

  "Integer" should {
    val handler = implicitly[BSONHandler[Int]]

    "be read from BSONDecimal" >> {
      "successfully if whole < Int.MaxValue" in {
        BSONDecimal
          .fromLong(123L)
          .flatMap(handler.readTry) must beSuccessfulTry(123)
      }

      "with error if not whole" in {
        BSONDecimal
          .fromBigDecimal(BigDecimal(123.45D))
          .flatMap(handler.readTry) must beFailedTry[Int]
      }
    }

    "be read from BSONDouble" >> {
      "successfully if whole < Int.MaxValue" in {
        handler.readTry(BSONDouble(123D)) must beSuccessfulTry(123)
      }

      "with error if not whole" in {
        handler.readTry(BSONDouble(123.45D)) must beFailedTry[Int]
      }
    }

    "be read from BSONInteger" in {
      handler.readTry(BSONInteger(123)) must beSuccessfulTry(123)
    }

    "be read from BSONLong" >> {
      "successfully if < Int.MaxValue" in {
        handler.readTry(BSONLong(123L)) must beSuccessfulTry(123)
      }

      "with error if >= Int.MaxValue" in {
        handler
          .readTry(BSONLong(Int.MaxValue.toLong + 1L)) must beFailedTry[Int]

      }
    }
  }

  "Byte" should {
    val handler = implicitly[BSONHandler[Byte]]
    val expected = 123.toByte

    "be read from BSONDecimal" >> {
      "successfully if whole < Byte.MaxValue" in {
        BSONDecimal
          .fromLong(123L)
          .flatMap(handler.readTry) must beSuccessfulTry(expected)
      }

      "with error if not whole" in {
        BSONDecimal
          .fromBigDecimal(BigDecimal(123.45D))
          .flatMap(handler.readTry) must beFailedTry[Byte]
      }
    }

    "be read from BSONDouble" >> {
      "successfully if whole < Byte.MaxValue" in {
        handler.readTry(BSONDouble(123D)) must beSuccessfulTry(expected)
      }

      "with error if not whole" in {
        handler.readTry(BSONDouble(123.45D)) must beFailedTry[Byte]
      }
    }

    "be read from BSONInteger" in {
      handler.readTry(BSONInteger(123)) must beSuccessfulTry(expected)
    }

    "be read from BSONLong" >> {
      "successfully if < Byte.MaxValue" in {
        handler.readTry(BSONLong(123L)) must beSuccessfulTry(expected)
      }

      "with error if >= Byte.MaxValue" in {
        handler
          .readTry(BSONLong(Byte.MaxValue.toLong + 1L)) must beFailedTry[Byte]

      }
    }
  }

  "Short" should {
    val handler = implicitly[BSONHandler[Short]]
    val expected = 123.toShort

    "be read from BSONDecimal" >> {
      "successfully if whole < Short.MaxValue" in {
        BSONDecimal
          .fromLong(123L)
          .flatMap(handler.readTry) must beSuccessfulTry(expected)
      }

      "with error if not whole" in {
        BSONDecimal
          .fromBigDecimal(BigDecimal(123.45D))
          .flatMap(handler.readTry) must beFailedTry[Short]
      }
    }

    "be read from BSONDouble" >> {
      "successfully if whole < Short.MaxValue" in {
        handler.readTry(BSONDouble(123D)) must beSuccessfulTry(expected)
      }

      "with error if not whole" in {
        handler.readTry(BSONDouble(123.45D)) must beFailedTry[Short]
      }
    }

    "be read from BSONInteger" in {
      handler.readTry(BSONInteger(123)) must beSuccessfulTry(expected)
    }

    "be read from BSONLong" >> {
      "successfully if < Short.MaxValue" in {
        handler.readTry(BSONLong(123L)) must beSuccessfulTry(expected)
      }

      "with error if >= Short.MaxValue" in {
        handler
          .readTry(BSONLong(Short.MaxValue.toLong + 1L)) must beFailedTry[Short]

      }
    }
  }

  "Character" should {
    val handler = implicitly[BSONHandler[Char]]

    "be read from BSONDecimal" >> {
      "successfully if whole < Char.MaxValue" in {
        BSONDecimal
          .fromLong('x'.toLong)
          .flatMap(handler.readTry) must beSuccessfulTry('x')
      }

      "with error if not whole" in {
        BSONDecimal
          .fromBigDecimal(BigDecimal(123.45D))
          .flatMap(handler.readTry) must beFailedTry[Char]
      }
    }

    "be read from BSONDouble" >> {
      "successfully if whole < Char.MaxValue" in {
        handler.readTry(BSONDouble('y'.toDouble)) must beSuccessfulTry('y')
      }

      "with error if not whole" in {
        handler.readTry(BSONDouble(123.45D)) must beFailedTry[Char]
      }
    }

    "be read from BSONInteger" in {
      handler.readTry(BSONInteger('z'.toInt)) must beSuccessfulTry('z')
    }

    "be read from BSONLong" >> {
      "successfully if < Char.MaxValue" in {
        handler.readTry(BSONLong('é'.toLong)) must beSuccessfulTry('é')
      }

      "with error if >= Char.MaxValue" in {
        handler
          .readTry(BSONLong(Char.MaxValue.toLong + 1L)) must beFailedTry[Char]

      }
    }

    "be read from BSONString" >> {
      "successfully if size == 1" in {
        handler.readTry(BSONString("a")) must beSuccessfulTry('a')
      }

      "with error if size < 0 or size > 1" in {
        handler.readTry(BSONString("")) must beFailedTry[Char] and {
          handler.readTry(BSONString("xy")) must beFailedTry[Char]
        }
      }
    }
  }

  "Long" should {
    val handler = implicitly[BSONHandler[Long]]

    "be read from BSONDecimal" >> {
      val dec = BSONDecimal.fromLong(123L)

      "successfully if whole < Long.MaxValue" in {
        dec.flatMap(handler.readTry) must beSuccessfulTry(123)
      }

      "with error if not whole" in {
        val d = BSONDecimal.fromBigDecimal(BigDecimal(s"${Long.MaxValue}0"))

        d.flatMap(handler.readTry) must beFailedTry[Long]
      }
    }

    "be read from BSONDouble" >> {
      "successfully if whole" in {
        handler.readTry(BSONDouble(123D)) must beSuccessfulTry(123L)
      }

      "with error if not whole" in {
        handler.readTry(BSONDouble(123.45D)) must beFailedTry[Long]
      }
    }

    "be read from BSONInteger" in {
      handler.readTry(BSONInteger(123)) must beSuccessfulTry(123L)
    }

    "be read from BSONLong" in {
      handler.readTry(BSONLong(123L)) must beSuccessfulTry(123L)
    }

    "be read from BSONDateTime" in {
      handler.readTry(BSONDateTime(12345L)) must beSuccessfulTry(12345L)
    }
  }

  "BSONString" should {
    val reader: BSONReader[String] =
      BSONReader.collect[String] { case BSONString(str) => str }

    "be read #1" in {
      reader
        .afterRead(_ => 1)
        .readTry(BSONString("lorem"))
        .aka("mapped BSON") must beSuccessfulTry(1)
    }

    "be read #2" in {
      val handler1 =
        BSONHandler.provided(reader, BSONWriter[String] { _ => ??? })

      // Make sure the type BSONHandler is preserved
      val handler2: BSONHandler[String] = handler1.beforeRead {
        case BSONInteger(i) => BSONString(s"lorem:${i}")
      }

      reader.beforeRead { case BSONInteger(i) => BSONString(s"lorem:${i}") }
        .readTry(BSONInteger(2)) must beSuccessfulTry("lorem:2") and {
        handler2.readOpt(BSONInteger(3)) must beSome("lorem:3")
      }
    }

    "be read as Locale" in {
      BSONString("fr-FR").asTry[Locale] must beSuccessfulTry(Locale.FRANCE)
    }

    "be read as URL" in {
      BSONString("http://reactivemongo.org").asTry[URL] must beSuccessfulTry(
        new URL("http://reactivemongo.org")
      )
    }

    "be read as URI" in {
      BSONString("http://reactivemongo.org").asTry[URI] must beSuccessfulTry(
        new URI("http://reactivemongo.org")
      )
    }

    "be read as UUID" in {
      val uuid = UUID.randomUUID()

      BSONString(uuid.toString).asTry[UUID] must beSuccessfulTry(uuid)
    }

    val writer = BSONWriter { BSONString(_: String) }

    "be provided as safe writer" in {
      SafeBSONWriter
        .unapply(implicitly[BSONWriter[String]])
        .aka("writer") must beSome[SafeBSONWriter[String]]
    }

    "be written #1" in {
      val handler1 = BSONHandler
        .provided[String](reader = BSONReader[String] { _ => ??? }, writer)

      // Make sure the type BSONHandler is preserved with afterWrite
      val handler2: BSONHandler[String] = handler1.afterWrite {
        case BSONString(_) => BSONInteger(4)
      }

      writer.afterWrite { case BSONString(_) => BSONInteger(3) }
        .writeTry("foo") must beSuccessfulTry(BSONInteger(3)) and {
        handler2.writeOpt("bar") must beSome(BSONInteger(4))
      }
    }

    "be written #2" in {
      writer
        .beforeWrite((_: (Int, Int)).toString)
        .writeTry(1 -> 2)
        .aka("mapped BSON") must beSuccessfulTry(BSONString("(1,2)"))
    }

    "be written from Locale" in {
      implicitly[BSONWriter[Locale]]
        .writeTry(Locale.FRANCE) must beSuccessfulTry(BSONString("fr-FR"))
    }

    "be written from URL" in {
      implicitly[BSONWriter[URL]].writeTry(
        new URL("http://reactivemongo.org")
      ) must beSuccessfulTry(BSONString("http://reactivemongo.org"))
    }

    "be written from URI" in {
      implicitly[BSONWriter[URI]].writeTry(
        new URI("http://reactivemongo.org")
      ) must beSuccessfulTry(BSONString("http://reactivemongo.org"))
    }

    "be written from UUID" in {
      val uuid = UUID.randomUUID()

      implicitly[BSONWriter[UUID]].writeTry(uuid) must beSuccessfulTry(
        BSONString(uuid.toString)
      )

    }
  }

  "Custom class" should {
    case class Foo(bar: String)
    implicit val w = BSONWriter[Foo] { f => BSONString(f.bar) }
    implicit val r = BSONReader.collect[Foo] { case BSONString(s) => Foo(s) }

    val foo = Foo("lorem")
    val bson = BSONString("lorem")

    "be written" >> {
      "as a single value" in {
        w.writeTry(foo) must beSuccessfulTry(bson)
      }

      "as an array" in {
        val aw = BSONWriter.sequence(w.writeTry _)
        val seq = Seq(foo, Foo("bar"))
        val arr = BSONArray(bson, BSONString("bar"))

        aw.writeTry(seq) must beSuccessfulTry(arr) and {
          aw.writeOpt(seq) must beSome(arr)
        }
      }
    }

    "be read" in {
      r.readTry(bson) must beSuccessfulTry(foo)
    }

    "be handled" >> {
      def spec(h: BSONHandler[Foo]) = {
        h.writeTry(foo) must beSuccessfulTry(bson) and {
          h.writeOpt(foo) must beSome(bson)
        } and {
          h.readTry(bson) must beSuccessfulTry(foo)
        } and {
          h.readOpt(bson) must beSome(foo)
        }
      }

      "provided there are reader and writer" in {
        spec(implicitly[BSONHandler[Foo]])
      }

      "using safe functions" in {
        import scala.util.{ Failure, Success }

        spec(
          BSONHandler.from[Foo](
            read = {
              case BSONString(bar) => Success(Foo(bar))
              case _               => Failure(new IllegalArgumentException())
            },
            write = { foo => Success(BSONString(foo.bar)) }
          )
        )
      }

      "using optional functions" in {
        spec(
          BSONHandler.option[Foo](
            read = {
              case BSONString(bar) => Some(Foo(bar))
              case _               => None
            },
            write = { foo => Some(BSONString(foo.bar)) }
          )
        )
      }

      "using partial functions" in {
        spec(
          BSONHandler.collect[Foo](
            read = { case BSONString(bar) => Foo(bar) },
            write = { case foo => BSONString(foo.bar) }
          )
        )
      }
    }
  }

  "Reader" should {
    "be created for a single value from an Option based function" in {
      val r = BSONReader.option[String] {
        case BSONInteger(0) => Some("zero")
        case BSONInteger(1) => Some("one")
        case _              => None
      }

      r.readTry(BSONInteger(0)) must beSuccessfulTry("zero") and {
        r.readTry(BSONInteger(1)) must beSuccessfulTry("one")
      } and {
        r.readTry(BSONInteger(2)) must beFailedTry[String]
          .withThrowable[exceptions.ValueDoesNotMatchException]
      } and {
        r.readOpt(BSONInteger(0)) must beSome("zero")
      } and {
        r.readOpt(BSONInteger(1)) must beSome("one")
      } and {
        r.readOpt(BSONInteger(3)) must beNone
      }
    }

    {
      val r = BSONDocumentReader.option[(String, Int)] { doc =>
        for {
          s <- doc.string("a")
          i <- doc.int("b")
        } yield s -> i
      }

      "be created for a document from an Option based function" in {
        r.readTry(BSONDocument("a" -> "A", "b" -> 1)) must beSuccessfulTry(
          "A" -> 1
        ) and {
          // Missing 'b'
          val doc = BSONDocument("a" -> "B")

          r.readTry(doc) must beFailedTry[(String, Int)] and {
            r.readOpt(doc) must beNone
          }
        } and {
          // Missing 'a'
          val doc = BSONDocument("b" -> 2)

          r.readTry(doc) must beFailedTry[(String, Int)] and {
            r.readOpt(doc) must beNone
          }
        } and {
          // Wrong type for 'a'
          val doc = BSONDocument("a" -> 3, "b" -> 4)

          r.readTry(doc) must beFailedTry[(String, Int)] and {
            r.readOpt(doc) must beNone
          }
        } and {
          // Wrong type for 'b'
          val doc = BSONDocument("a" -> "C", "b" -> 5.6D)

          r.readTry(doc) must beFailedTry[(String, Int)] and {
            r.readOpt(doc) must beNone
          }
        }
      }

      "be created for BSON array" in {
        val seqReader: BSONReader[Seq[(String, Int)]] =
          BSONReader.sequence(r.readTry _)

        val arr1 = BSONArray(
          BSONDocument("a" -> "A", "b" -> 1),
          BSONDocument("a" -> "B", "b" -> 2),
          BSONDocument("a" -> "C", "b" -> 3)
        )

        val seq1 = Seq("A" -> 1, "B" -> 2, "C" -> 3)

        val arr2 = BSONArray(
          BSONDocument("a" -> "A", "b" -> 1),
          BSONDocument("a" -> "B")
        )

        val arr3 =
          BSONArray(BSONDocument("b" -> 1), BSONDocument("a" -> "B", "b" -> 2))

        seqReader.readTry(arr1) must_=== Success(seq1) and {
          seqReader.readOpt(arr1) must_=== Some(seq1)
        } and {
          seqReader.readTry(arr2) must beFailedTry[Seq[(String, Int)]]
        } and {
          seqReader.readOpt(arr2) must beNone
        } and {
          seqReader.readTry(arr3) must beFailedTry[Seq[(String, Int)]]
        } and {
          seqReader.readOpt(arr3) must beNone
        }
      }
    }

    "be widened" >> {
      trait Foo
      case class Bar(v: Int) extends Foo
      val bar = Bar(0)

      "as BSONReader" in {
        val barReader = BSONReader[Bar] { _ => bar }
        val barHandler = BSONHandler.provided(
          barReader,
          BSONWriter[Bar](b => BSONInteger(b.v))
        )

        val fooReader: BSONReader[Foo] = barReader.widen[Foo]
        val fooHandler: BSONHandler[Foo] = barHandler.widen[Foo]

        fooReader.readTry(BSONInteger(1)) must beSuccessfulTry(bar) and {
          fooReader.readOpt(BSONInteger(1)) must beSome(bar)
        } and {
          fooHandler.readTry(BSONInteger(1)) must beSuccessfulTry(bar)
        } and {
          fooHandler.readOpt(BSONInteger(1)) must beSome(bar)
        }
      }

      "as BSONDocumentReader" in {
        val barReader = BSONDocumentReader[Bar] { _ => bar }
        val barHandler = BSONDocumentHandler.provided(
          barReader,
          BSONDocumentWriter[Bar](_ => BSONDocument.empty)
        )

        val fooReader: BSONDocumentReader[Foo] = barReader.widen[Foo]
        val fooHandler: BSONDocumentHandler[Foo] = barHandler.widen[Foo]

        fooReader.readTry(BSONDocument.empty) must beSuccessfulTry(bar) and {
          fooReader.readOpt(BSONDocument.empty) must beSome(bar)
        } and {
          fooHandler.readTry(BSONDocument.empty) must beSuccessfulTry(bar)
        } and {
          fooHandler.readOpt(BSONDocument.empty) must beSome(bar)
        }
      }
    }
  }

  "Writer" should {
    {
      def partialSpec(w: BSONWriter[String]) = {
        w.writeTry("zero") must beSuccessfulTry(BSONInteger(0)) and {
          w.writeTry("one") must beSuccessfulTry(BSONInteger(1))
        } and {
          w.writeOpt("zero") must beSome(BSONInteger(0))
        } and {
          w.writeOpt("one") must beSome(BSONInteger(1))
        } and {
          w.writeTry("3") must beFailedTry[BSONValue]
        } and {
          w.writeOpt("4") must beNone
        }
      }

      "be created from an Option based function" in {
        partialSpec(BSONWriter.option[String] {
          case "zero" => Some(BSONInteger(0))
          case "one"  => Some(BSONInteger(1))
          case _      => None
        })
      }

      "be created from a partial function" in {
        partialSpec(BSONWriter.collect[String] {
          case "zero" => BSONInteger(0)
          case "one"  => BSONInteger(1)
        })
      }
    }

    "be narrowed" >> {
      trait Foo
      case class Bar(v: Int) extends Foo
      val bar = Bar(0)

      "as BSONWriter" in {
        val fooWriter = BSONWriter[Foo](_ => BSONInteger(1))
        val fooHandler =
          BSONHandler.provided(BSONReader[Foo](_ => bar), fooWriter)

        val barWriter: BSONWriter[Bar] = fooWriter.narrow[Bar]
        val barHandler: BSONHandler[Bar] = fooHandler.narrow[Bar]

        barWriter.writeTry(bar) must beSuccessfulTry(BSONInteger(1)) and {
          barWriter.writeOpt(bar) must beSome(BSONInteger(1))
        } and {
          barHandler.writeTry(bar) must beSuccessfulTry(BSONInteger(1))
        } and {
          barHandler.writeOpt(bar) must beSome(BSONInteger(1))
        }
      }

      "as BSONDocumentWriter" in {
        val doc = BSONDocument("bar" -> 1)
        val fooWriter = BSONDocumentWriter[Foo](_ => doc)
        val fooHandler = BSONDocumentHandler.provided(
          BSONDocumentReader[Foo](_ => bar),
          fooWriter
        )

        val barWriter: BSONDocumentWriter[Bar] = fooWriter.narrow[Bar]
        val barHandler: BSONDocumentHandler[Bar] = fooHandler.narrow[Bar]

        barWriter.writeTry(bar) must beSuccessfulTry(doc) and {
          barWriter.writeOpt(bar) must beSome(doc)
        } and {
          barHandler.writeTry(bar) must beSuccessfulTry(doc)
        } and {
          barHandler.writeOpt(bar) must beSome(doc)
        }
      }
    }
  }

  "Field" should {
    "be read" in {
      val reader = BSONDocumentReader.field[String]("foo")

      reader
        .readTry(BSONDocument("foo" -> "bar"))
        .aka("field") must beSuccessfulTry("bar")

    }

    "be written" in {
      val writer = BSONDocumentWriter.field[Int]("bar")

      writer.writeTry(2) must beSuccessfulTry(BSONDocument("bar" -> 2))
    }
  }

  "Tuple" should {
    "be handle from document" >> {
      val invalidDoc = BSONDocument("ok" -> 0)
      val invalidProduct = Tuple1(0)
      val invalidArr = BSONArray("ko")

      def w[T <: Product](
          writer: BSONWriter[T]
        )(implicit
          ev: scala.reflect.ClassTag[T]
        ) =
        BSONWriter.collectFrom[Product] { case `ev`(t) => writer.writeTry(t) }

      Fragments.foreach(
        Seq[Tuple5[BSONValue, BSONValue, Product, BSONWriter[
          Product
        ], BSONReader[_]]](
          // BSONDocumentX.tuple2
          Tuple5(
            BSONDocument("name" -> "Foo", "age" -> 20),
            invalidDoc,
            ("Foo", 20),
            w(BSONDocumentWriter.tuple2[String, Int]("name", "age")),
            BSONDocumentReader.tuple2[String, Int]("name", "age")
          ),
          // BSONDocumentX.tuple3
          Tuple5(
            BSONDocument(
              "firstName" -> "Foo",
              "lastName" -> "Bar",
              "age" -> 20
            ),
            invalidDoc,
            ("Foo", "Bar", 20),
            w(
              BSONDocumentWriter
                .tuple3[String, String, Int]("firstName", "lastName", "age")
            ),
            BSONDocumentReader
              .tuple3[String, String, Int]("firstName", "lastName", "age")
          ),
          // BSONDocumentX.tuple4
          Tuple5(
            BSONDocument(
              "firstName" -> "Foo",
              "lastName" -> "Bar",
              "age" -> 20,
              "score" -> 1.23D
            ),
            invalidDoc,
            ("Foo", "Bar", 20, 1.23D),
            w(
              BSONDocumentWriter.tuple4[String, String, Int, Double](
                "firstName",
                "lastName",
                "age",
                "score"
              )
            ),
            BSONDocumentReader.tuple4[String, String, Int, Double](
              "firstName",
              "lastName",
              "age",
              "score"
            )
          ),
          // BSONDocumentX.tuple5
          Tuple5(
            BSONDocument(
              "firstName" -> "Foo",
              "lastName" -> "Bar",
              "age" -> 20,
              "score" -> 1.23D,
              "days" -> BSONArray(3, 5)
            ),
            invalidDoc,
            ("Foo", "Bar", 20, 1.23D, Seq(3, 5)),
            w(
              BSONDocumentWriter.tuple5[String, String, Int, Double, Seq[Int]](
                "firstName",
                "lastName",
                "age",
                "score",
                "days"
              )
            ),
            BSONDocumentReader.tuple5[String, String, Int, Double, Seq[Int]](
              "firstName",
              "lastName",
              "age",
              "score",
              "days"
            )
          ),
          // BSONX.tuple2
          Tuple5(
            BSONArray("Foo", 20),
            invalidArr,
            ("Foo", 20),
            w(BSONWriter.tuple2[String, Int]),
            BSONReader.tuple2[String, Int]
          ),
          // BSONX.tuple3
          Tuple5(
            BSONArray("Foo", "Bar", 20),
            invalidArr,
            ("Foo", "Bar", 20),
            w(BSONWriter.tuple3[String, String, Int]),
            BSONReader.tuple3[String, String, Int]
          ),
          // BSONX.tuple4
          Tuple5(
            BSONArray("Foo", "Bar", 20, 1.23D),
            invalidArr,
            ("Foo", "Bar", 20, 1.23D),
            w(BSONWriter.tuple4[String, String, Int, Double]),
            BSONReader.tuple4[String, String, Int, Double]
          ),
          // BSONX.tuple5
          Tuple5(
            BSONArray("Foo", "Bar", 20, 1.23D, BSONArray(3, 5)),
            invalidArr,
            ("Foo", "Bar", 20, 1.23D, Seq(3, 5)),
            w(BSONWriter.tuple5[String, String, Int, Double, Seq[Int]]),
            BSONReader.tuple5[String, String, Int, Double, Seq[Int]]
          )
        )
      ) {
        case (bson, invalidBson, tuple, writer, reader) =>
          s"from ${BSONValue pretty bson} as arity ${tuple.productArity}" in {
            writer.writeTry(tuple) must beSuccessfulTry(bson) and {
              writer.writeOpt(tuple) must beSome(bson)
            } and {
              writer.writeTry(invalidProduct) must beFailedTry
            } and {
              writer.writeOpt(invalidProduct) must beNone
            } and {
              reader.readTry(bson) must beSuccessfulTry(tuple)
            } and {
              reader.readOpt(bson) must beSome(tuple)
            } and {
              reader.readTry(invalidBson) must beFailedTry
            } and {
              reader.readOpt(invalidBson) must beNone
            }
          }
      }
    }
  }

  "LocalTime" should {
    val writer = implicitly[BSONWriter[LocalTime]]
    val reader = implicitly[BSONReader[LocalTime]]

    "be represented as BSON long (nano precision)" in {
      val localTime = LocalTime.now()

      writer.writeTry(localTime) must beSuccessfulTry[BSONValue].like {
        case repr @ BSONLong(v) =>
          v must_=== localTime.toNanoOfDay and {
            reader.readTry(repr) must beSuccessfulTry(localTime)
          }
      }
    }
  }

  // ---

  lazy val doc = {
    @SuppressWarnings(Array("TryGet"))
    def bson = BSONDocument(
      "name" -> "James",
      "age" -> 27,
      "surname1" -> Some("Jim"),
      "surname2" -> None,
      "surname3" -> Option.empty[String],
      "score" -> 3.88,
      "online" -> true,
      "_id" -> BSONObjectID.parse("5117c6391aa562a90098f621").get,
      "contact" -> BSONDocument(
        "emails" -> BSONArray(
          Some("james@example.org"),
          None,
          Some("spamaddrjames@example.org")
        ),
        "adress" -> BSONString("coucou")
      ),
      "lastSeen" -> BSONLong(1360512704747L),
      "missing" -> BSONNull
    )

    bson
  }

  lazy val array = BSONArray(
    BSONString("elem0"),
    None,
    1,
    2.222,
    BSONDocument("name" -> "Joe"),
    BSONArray(0L),
    "pp[4]"
  )

  case class Artist(
      name: String,
      birthDate: Instant,
      albums: List[Album])

  val neilYoung = Artist(
    "Neil Young",
    Instant.parse("1982-10-11T17:30:00Z"),
    List(
      new Album(
        "Everybody Knows this is Nowhere",
        1969,
        Some("hello".getBytes("UTF-8")),
        List(
          "Cinnamon Girl",
          "Everybody Knows this is Nowhere",
          "Round & Round (it Won't Be Long)",
          "Down By the River",
          "Losing End (When You're On)",
          "Running Dry (Requiem For the Rockets)",
          "Cowgirl in the Sand"
        )
      )
    )
  )

  implicit def albumHandler: BSONDocumentHandler[Album] =
    BSONDocumentHandler.from[Album](
      read = { (doc: BSONDocument) =>
        for {
          n <- doc.getAsTry[String]("name")
          r <- doc.getAsTry[Int]("releaseYear")
          c <- doc.getAsUnflattenedTry[Array[Byte]]("certificate")
          t <- doc.getAsTry[List[String]]("tracks")
        } yield new Album(n, r, c, t)
      },
      write = { (album: Album) =>
        Success(
          BSONDocument(
            "name" -> album.name,
            "releaseYear" -> album.releaseYear,
            "certificate" -> album.certificate,
            "tracks" -> album.tracks
          )
        )
      }
    )

  implicit def artistHandler: BSONDocumentHandler[Artist] =
    BSONDocumentHandler.from[Artist](
      read = { (doc: BSONDocument) =>
        for {
          name <- doc.getAsTry[String]("name")
          birthDate <- doc.getAsTry[Instant]("birthDate")
          arts <- doc.getAsTry[List[Album]]("albums")
        } yield Artist(name, birthDate, arts)
      },
      write = { (artist: Artist) =>
        Success(
          BSONDocument(
            "name" -> artist.name,
            "birthDate" -> artist.birthDate,
            "albums" -> artist.albums
          )
        )
      }
    )

  "Neil Young" should {
    "produce the expected pretty representation" in {
      BSON.writeDocument(neilYoung) must beSuccessfulTry[BSONDocument].which {
        doc =>
          BSONDocument.pretty(doc) must_=== """{
  'name': 'Neil Young',
  'birthDate': ISODate('1982-10-11T17:30:00Z'),
  'albums': [
    {
      'name': 'Everybody Knows this is Nowhere',
      'releaseYear': 1969,
      'certificate': BinData(0, 'aGVsbG8='),
      'tracks': [
        'Cinnamon Girl',
        'Everybody Knows this is Nowhere',
        'Round & Round (it Won\'t Be Long)',
        'Down By the River',
        'Losing End (When You\'re On)',
        'Running Dry (Requiem For the Rockets)',
        'Cowgirl in the Sand'
      ]
    }
  ]
}""".replaceAll("\r", "") and {
            val ny2 = BSON.readDocument[Artist](doc)
            def allSongs = doc
              .getAsOpt[List[Album]]("albums")
              .getOrElse(List.empty)
              .flatMap(_.tracks)

            allSongs must_=== List(
              "Cinnamon Girl",
              "Everybody Knows this is Nowhere",
              "Round & Round (it Won't Be Long)",
              "Down By the River",
              "Losing End (When You're On)",
              "Running Dry (Requiem For the Rockets)",
              "Cowgirl in the Sand"
            ) and {
              ny2 must beSuccessfulTry(neilYoung)
            }
          }
      }
    }
  }
}

// ---

final class Album(
    val name: String,
    val releaseYear: Int,
    val certificate: Option[Array[Byte]],
    val tracks: List[String]) {

  import java.util.Arrays

  override def hashCode: Int =
    (name, releaseYear, certificate.map(Arrays.hashCode(_)), tracks).hashCode

  override def equals(that: Any): Boolean = that match {
    case a: Album =>
      (certificate, a.certificate) match {
        case (None, None) =>
          Tuple3(name, releaseYear, tracks) == Tuple3(
            a.name,
            a.releaseYear,
            a.tracks
          )

        case (Some(x), Some(y)) =>
          Tuple3(name, releaseYear, tracks) == Tuple3(
            a.name,
            a.releaseYear,
            a.tracks
          ) && Arrays.equals(x, y)

        case _ => false
      }

    case _ => false
  }
}
