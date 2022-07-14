import reactivemongo.api.bson._

final case class AnnotatedPerson(
    name: String,
    @Macros.Annotations.Writer(Foo.oiw) @Macros.Annotations.Reader(
      Foo.oir
    ) age: Option[Int])

final case class NotAnnotatedPerson(
    name: String,
    age: Option[Int])

object Foo {
  val annotatedPersonWriter: BSONDocumentWriter[AnnotatedPerson] = Macros.writer

  def oiw: BSONWriter[Option[Int]] = BSONWriter[Option[Int]] {
    case Some(i) => BSONString(i.toString)
    case _       => BSONNull
  }

  val notAnnotatedPersonWriter1: BSONDocumentWriter[NotAnnotatedPerson] =
    Macros.writer

  val notAnnotatedPersonWriter2: BSONDocumentWriter[NotAnnotatedPerson] = {
    implicit def iw: BSONWriter[Option[Int]] = oiw
    Macros.writer
  }

  def oir: BSONReader[Option[Int]] = BSONReader[Option[Int]] {
    case BSONString(repr) => Some(repr.toInt)
    case _                => None
  }

  val annotatedPersonReader: BSONDocumentReader[AnnotatedPerson] = Macros.reader

  val notAnnotatedPersonReader1: BSONDocumentReader[NotAnnotatedPerson] =
    Macros.reader

  val notAnnotatedPersonReader2: BSONDocumentReader[NotAnnotatedPerson] = {
    implicit def ir: BSONReader[Option[Int]] = oir
    Macros.reader
  }
}

final class FooSpec extends org.specs2.mutable.Specification {
  "Foo".title

  "Writer for optional field" should {
    "be resolved from annotation" in {
      Foo.annotatedPersonWriter.writeTry(
        AnnotatedPerson("foo", Some(1))
      ) must beSuccessfulTry(BSONDocument("name" -> "foo", "age" -> "1"))
    }

    "be resolved using default instance" in {
      Foo.notAnnotatedPersonWriter1.writeTry(
        NotAnnotatedPerson("bar", Some(2))
      ) must beSuccessfulTry(BSONDocument("name" -> "bar", "age" -> 2))
    }

    "be resolved using custom instance" in {
      Foo.notAnnotatedPersonWriter2.writeTry(
        NotAnnotatedPerson("lorem", Some(3))
      ) must beSuccessfulTry(
        BSONDocument("name" -> "lorem", "age" -> "3")
      )
    }
  }

  "Reader for optional field" should {
    "be resolved from annotation" in {
      Foo.annotatedPersonReader.readTry(
        BSONDocument("name" -> "foo", "age" -> "1")
      ) must beSuccessfulTry(AnnotatedPerson("foo", Some(1)))
    }

    "be resolved using default instance" in {
      Foo.notAnnotatedPersonReader1.readTry(
        BSONDocument("name" -> "bar", "age" -> 2)
      ) must beSuccessfulTry(NotAnnotatedPerson("bar", Some(2)))
    }

    "be resolved using custom instance" in {
      Foo.notAnnotatedPersonReader2.readTry(
        BSONDocument("name" -> "lorem", "age" -> "3")
      ) must beSuccessfulTry(NotAnnotatedPerson("lorem", Some(3)))
    }
  }
}
