package reactivemongo.api.bson

import java.time.{
  Instant,
  LocalDate,
  LocalDateTime,
  OffsetDateTime,
  ZonedDateTime,
  ZoneId
}

import java.net.{ URL, URI }

import scala.util.{ Success, Try }

final class HandlerSpec extends org.specs2.mutable.Specification {
  "Handler" title

  import exceptions.TypeDoesNotMatchException

  "BSONBinary" should {
    "be read as byte array" in {
      val bytes = Array[Byte](1, 3, 5, 7)
      val bin = BSONBinary(bytes, Subtype.GenericBinarySubtype)

      bin.asTry[Array[Byte]] aka "read #1" must beSuccessfulTry(bytes) and {
        bin.asTry[Array[Byte]] aka "read #2" must beSuccessfulTry(bytes)
      }
    }
  }

  "Complex Document" should {
    "have a name == 'James'" in {
      doc.getAsTry[BSONString]("name") must beSuccessfulTry(BSONString("James"))
      doc.getAsTry[String]("name") must beSuccessfulTry("James")

      doc.getAsTry[BSONInteger]("name") must beFailedTry and {
        doc.getAsOpt[BSONInteger]("name") must beNone
      } and {
        doc.getAsTry[Int]("name") must beFailedTry
      } and {
        doc.getAsOpt[Int]("name") must beNone
      } and {
        doc.getAsTry[BSONNumberLike]("name") must beFailedTry
      } and {
        doc.get("name").get.asTry[String] must beSuccessfulTry("James")
      } and {
        doc.get("name").get.asTry[Int] must beFailedTry
      } and {
        doc.get("name").get.asOpt[String] must beSome("James")
      }
    }

    "have a score == 3.88" in {
      doc.getAsTry[BSONDouble]("score") must beSuccessfulTry(BSONDouble(3.88))
      doc.getAsTry[Double]("score") must beSuccessfulTry(3.88)

      doc.getAsTry[BSONInteger]("score") must beFailedTry
      doc.getAsTry[Int]("score") must beFailedTry

      doc.getAsTry[BSONNumberLike]("score") must beSuccessfulTry.like {
        case num => num.toDouble must beSuccessfulTry(3.88) and {
          num.toFloat must beSuccessfulTry(3.88F)
        } and {
          num.toLong must beSuccessfulTry(3L)
        } and {
          num.toInt must beSuccessfulTry(3)
        }
      } and {
        doc.getAsTry[BSONBooleanLike]("score").
          flatMap(_.toBoolean) must beSuccessfulTry(true)
      }
    }

    "be read" in {
      val reader1 = BSONDocumentReader.from(_.getAsTry[String]("name"))

      reader1.readTry(doc) must beSuccessfulTry("James") and {
        val before: PartialFunction[BSONDocument, BSONDocument] = {
          case _: BSONDocument => BSONDocument("name" -> "John")
        }
        val reader2: BSONDocumentReader[String] = reader1.beforeRead(before)

        reader2.readTry(doc) must beSuccessfulTry("John")
      } and {
        val reader3: BSONDocumentReader[Unit] = reader1.afterRead(_ => ())

        reader3.readTry(doc) must beSuccessfulTry({})
      }
    }

    "be written" in {
      val writer1 = BSONDocumentWriter { s: String =>
        BSONDocument(f"$$foo" -> s)
      }

      writer1.writeTry("bar") must beSuccessfulTry(BSONDocument(f"$$foo" -> "bar")) and {
        val writer2: BSONDocumentWriter[Int] =
          writer1.beforeWrite[Int](_.toString)

        writer2.writeTry(10) must beSuccessfulTry(
          BSONDocument(f"$$foo" -> "10"))

      } and {
        val expected = BSONDocument("lorem" -> "ipsum")
        val writer3: BSONDocumentWriter[String] = writer1.afterWrite {
          case _ => expected
        }

        writer3.writeTry("test") must beSuccessfulTry(expected)
      }
    }
  }

  "Complex Array" should {
    "be of size = 6" in {
      array.size must_=== 6
    }

    "have a an int = 2 at index 2" in {
      array.get(1) must beSome(BSONInteger(1)) and (
        array.getAsOpt[Int](1) must beSome(1))
    }

    "get bsondocument at index 3" in {
      array.getAsOpt[BSONDocument](3) must beSome.which {
        _.getAsOpt[String]("name") must beSome("Joe")
      }
    }

    "get bsonarray at index 4" in {
      val tdoc = array.getAsTry[BSONDocument](4)

      tdoc must beFailedTry.withThrowable[TypeDoesNotMatchException]

      array.getAsTry[BSONArray](4) must beSuccessfulTry[BSONArray].
        which { tarray =>
          tarray.getAsOpt[BSONLong](0) must beSome(BSONLong(0L)) and (
            tarray.getAsOpt[BSONBooleanLike](0).
            map(_.toBoolean) must beSome(Success(false)))
        }
    }
  }

  "Map" should {
    import reactivemongo.api.bson

    "write primitives values" in {
      val input = Map("a" -> 1, "b" -> 2)
      val result = bson.mapKeyWriter[String, Int].writeTry(input)

      result must beSuccessfulTry(BSONDocument("a" -> 1, "b" -> 2))
    }

    "read primitives values" in {
      val input = BSONDocument("a" -> 1, "b" -> 2)
      val handler = implicitly[BSONReader[Map[String, Int]]]

      handler.readTry(input) must beSuccessfulTry(Map("a" -> 1, "b" -> 2))
    }

    case class Foo(label: String, count: Int)
    implicit val fooWriter = BSONDocumentWriter[Foo] { foo =>
      BSONDocument("label" -> foo.label, "count" -> foo.count)
    }
    implicit val fooReader = BSONDocumentReader.from[Foo] { document =>
      for {
        label <- document.getAsTry[String]("label")
        count <- document.getAsTry[Int]("count")
      } yield Foo(label, count)
    }

    "write complex values" in {
      val expectedResult = BSONDocument(
        "a" -> BSONDocument("label" -> "foo", "count" -> 10),
        "b" -> BSONDocument("label" -> "foo2", "count" -> 20))
      val input = Map("a" -> Foo("foo", 10), "b" -> Foo("foo2", 20))
      val result = mapWriter[Foo].writeTry(input)

      result must beSuccessfulTry(expectedResult)
    }

    "read complex values" in {
      val expectedResult = Map("a" -> Foo("foo", 10), "b" -> Foo("foo2", 20))
      val input = BSONDocument(
        "a" -> BSONDocument("label" -> "foo", "count" -> 10),
        "b" -> BSONDocument("label" -> "foo2", "count" -> 20))
      val handler = implicitly[BSONReader[Map[String, Foo]]]

      handler.readTry(input) must beSuccessfulTry(expectedResult)
    }
  }

  "BSONDateTime" should {
    val time = System.currentTimeMillis()
    val bson = BSONDateTime(time)
    val instant = Instant.ofEpochMilli(time)
    val defaultZone = ZoneId.systemDefault
    val offset = defaultZone.getRules.getOffset(instant)
    val localDateTime = LocalDateTime.ofEpochSecond(
      time / 1000, instant.getNano, offset)

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
      val dateBson = BSONDateTime(
        localDate.atStartOfDay.toEpochSecond(offset) * 1000)

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
      val time = System.currentTimeMillis()
      val num = time / 1000L
      val bson = BSONTimestamp(num)

      reader.readOpt(bson).map(_.toLong) must beSome(Success(num * 1000L))
    }
  }

  "BSONString" should {
    val reader = BSONReader.collect[String] { case BSONString(str) => str }

    "be read #1" in {
      reader.afterRead(_ => 1).readTry(BSONString("lorem")).
        aka("mapped BSON") must beSuccessfulTry(1)
    }

    "be read #2" in {
      reader.beforeRead {
        case BSONInteger(i) => BSONString(s"lorem:${i}")
      }.readTry(BSONInteger(2)) must beSuccessfulTry("lorem:2")
    }

    "be read as URL" in {
      BSONString("http://reactivemongo.org").
        asTry[URL] must beSuccessfulTry(new URL("http://reactivemongo.org"))
    }

    "be read as URI" in {
      BSONString("http://reactivemongo.org").
        asTry[URI] must beSuccessfulTry(new URI("http://reactivemongo.org"))
    }

    val writer = BSONWriter { str: String => BSONString(str) }

    "be provided as safe writer" in {
      SafeBSONWriter.unapply(implicitly[BSONWriter[String]]).
        aka("writer") must beSome[SafeBSONWriter[String]]
    }

    "be written #1" in {
      writer.afterWrite {
        case BSONString(_) => BSONInteger(3)
      }.writeTry("foo") must beSuccessfulTry(BSONInteger(3))
    }

    "be written #2" in {
      writer.beforeWrite((_: (Int, Int)).toString).writeTry(1 -> 2).
        aka("mapped BSON") must beSuccessfulTry(BSONString("(1,2)"))
    }

    "be written from URL" in {
      implicitly[BSONWriter[URL]].writeTry(
        new URL("http://reactivemongo.org")) must beSuccessfulTry(
          BSONString("http://reactivemongo.org"))
    }

    "be written from URI" in {
      implicitly[BSONWriter[URI]].writeTry(
        new URI("http://reactivemongo.org")) must beSuccessfulTry(
          BSONString("http://reactivemongo.org"))
    }
  }

  "Custom class" should {
    case class Foo(bar: String)
    implicit val w = BSONWriter[Foo] { f => BSONString(f.bar) }
    implicit val r = BSONReader.collect[Foo] { case BSONString(s) => Foo(s) }

    val foo = Foo("lorem")
    val bson = BSONString("lorem")

    "be written" in {
      w.writeTry(foo) must beSuccessfulTry(bson)
    }

    "be read" in {
      r.readTry(bson) must beSuccessfulTry(foo)
    }

    "be handled (provided there are reader and writer)" in {
      val h = implicitly[BSONHandler[Foo]]

      h.writeTry(foo) must beSuccessfulTry(bson) and {
        h.readTry(bson) must beSuccessfulTry(foo)
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
          Some("spamaddrjames@example.org")),
        "adress" -> BSONString("coucou")),
      "lastSeen" -> BSONLong(1360512704747L))

    bson
  }

  lazy val array = BSONArray(
    BSONString("elem0"),
    None,
    1,
    2.222,
    BSONDocument(
      "name" -> "Joe"),
    BSONArray(0L),
    "pp[4]")

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
          "Cowgirl in the Sand"))))

  implicit object AlbumHandler extends BSONDocumentWriter[Album] with BSONDocumentReader[Album] {
    def writeTry(album: Album): Try[BSONDocument] = Success(BSONDocument(
      "name" -> album.name,
      "releaseYear" -> album.releaseYear,
      "certificate" -> album.certificate,
      "tracks" -> album.tracks))

    def readDocument(doc: BSONDocument) = for {
      n <- doc.getAsTry[String]("name")
      r <- doc.getAsTry[Int]("releaseYear")
      c <- doc.getAsUnflattenedTry[Array[Byte]]("certificate")
      t <- doc.getAsTry[List[String]]("tracks")
    } yield new Album(n, r, c, t)
  }

  implicit object ArtistHandler extends BSONDocumentWriter[Artist] with BSONDocumentReader[Artist] {
    def writeTry(artist: Artist): Try[BSONDocument] =
      Success(BSONDocument(
        "name" -> artist.name,
        "birthDate" -> artist.birthDate,
        "albums" -> artist.albums))

    def readDocument(doc: BSONDocument) = for {
      name <- doc.getAsTry[String]("name")
      birthDate <- doc.getAsTry[Instant]("birthDate")
      arts <- doc.getAsTry[List[Album]]("albums")
    } yield Artist(name, birthDate, arts)
  }

  "Neil Young" should {
    "produce the expected pretty representation" in {
      BSON.writeDocument(neilYoung) must beSuccessfulTry[BSONDocument].
        which { doc =>
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
            def allSongs = doc.getAsOpt[List[Album]]("albums").
              getOrElse(List.empty).flatMap(_.tracks)

            allSongs must_=== List(
              "Cinnamon Girl",
              "Everybody Knows this is Nowhere",
              "Round & Round (it Won't Be Long)",
              "Down By the River",
              "Losing End (When You're On)",
              "Running Dry (Requiem For the Rockets)",
              "Cowgirl in the Sand") and {
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
    (name, releaseYear,
      certificate.map(Arrays.hashCode(_)), tracks).hashCode

  override def equals(that: Any): Boolean = that match {
    case a: Album => (certificate, a.certificate) match {
      case (None, None) =>
        Tuple3(name, releaseYear, tracks) == Tuple3(
          a.name, a.releaseYear, a.tracks)

      case (Some(x), Some(y)) =>
        Tuple3(name, releaseYear, tracks) == Tuple3(
          a.name, a.releaseYear, a.tracks) && Arrays.equals(x, y)

      case _ => false
    }

    case _ => false
  }
}
