package reactivemongo.api.bson.collection

import scala.util.{ Failure, Success, Try }

//import reactivemongo.api.ReadConcern
import reactivemongo.api.commands.{ CommandError, UnitBox }

import reactivemongo.api.bson.{
  BSONDocument,
  BSONDocumentReader,
  BSONNumberLike
}

object CommonImplicits {
  implicit object UnitBoxReader
    extends DealingWithGenericCommandErrorsReader[UnitBox.type] {
    def readResult(doc: BSONDocument): Try[UnitBox.type] = Success(UnitBox)
  }

  /*
  implicit val readConcernWriter: BSONDocumentWriter[ReadConcern] =
    BSONDocumentWriter[ReadConcern] { concern =>
      BSONDocument("level" -> concern.level)
    }
   */
}

private[bson] trait DealingWithGenericCommandErrorsReader[A]
  extends BSONDocumentReader[A] {

  def readResult(doc: BSONDocument): Try[A]

  final def readDocument(doc: BSONDocument): Try[A] =
    doc.getAsTry[BSONNumberLike]("ok").flatMap(_.toInt).flatMap { ok =>
      if (ok < 1) {
        val cause = new CommandError.DefaultCommandError(
          code = doc.getAsOpt[Int]("code"),
          errmsg = doc.getAsOpt[String]("errmsg"),
          showDocument = () => BSONDocument.pretty(doc))

        Failure(cause)
      } else readDocument(doc)
    }
}
