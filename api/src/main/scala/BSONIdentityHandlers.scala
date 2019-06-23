package reactivemongo.api.bson

import scala.util.{ Failure, Success, Try }

private[bson] trait BSONIdentityHandlers
  extends BSONIdentityLowPriorityHandlers { _: DefaultBSONHandlers =>

  import exceptions.TypeDoesNotMatchException

  private[bson] sealed trait IdentityBSONHandler[B <: BSONValue]
    extends BSONReader[B] with BSONWriter[B] {

    protected def valueType: String

    protected def unapply(bson: BSONValue): Option[B]

    @inline final def writeTry(bson: B): Try[B] = Success(bson)

    final def readTry(bson: BSONValue): Try[B] = unapply(bson) match {
      case Some(v) => Success(v)

      case _ =>
        Failure(TypeDoesNotMatchException(
          valueType, bson.getClass.getSimpleName))
    }

    final override def readOpt(bson: BSONValue): Option[B] = unapply(bson)
  }

  implicit object BSONStringIdentity extends IdentityBSONHandler[BSONString] {
    protected val valueType = "BSONString"

    protected def unapply(bson: BSONValue): Option[BSONString] = bson match {
      case s: BSONString => Some(s)
      case _ => None
    }
  }

  implicit object BSONIntegerIdentity extends IdentityBSONHandler[BSONInteger] {
    protected val valueType = "BSONInteger"

    protected def unapply(bson: BSONValue): Option[BSONInteger] = bson match {
      case i: BSONInteger => Some(i)
      case _ => None
    }
  }

  implicit object BSONDecimalIdentity extends IdentityBSONHandler[BSONDecimal] {
    protected val valueType = "BSONDecimal"

    protected def unapply(bson: BSONValue): Option[BSONDecimal] = bson match {
      case d: BSONDecimal => Some(d)
      case _ => None
    }
  }

  implicit object BSONArrayIdentity extends IdentityBSONHandler[BSONArray] {
    protected val valueType = "BSONArray"

    protected def unapply(bson: BSONValue): Option[BSONArray] = bson match {
      case a: BSONArray => Some(a)
      case _ => None
    }
  }

  implicit object BSONDocumentIdentity extends BSONDocumentReader[BSONDocument]
    with BSONDocumentWriter[BSONDocument] {

    @inline def writeTry(doc: BSONDocument): Try[BSONDocument] =
      Success(doc)

    def readDocument(doc: BSONDocument): Try[BSONDocument] =
      Success(doc)
  }

  implicit object BSONBooleanIdentity extends IdentityBSONHandler[BSONBoolean] {
    protected val valueType = "BSONBoolean"

    protected def unapply(bson: BSONValue): Option[BSONBoolean] = bson match {
      case b: BSONBoolean => Some(b)
      case _ => None
    }
  }

  implicit object BSONLongIdentity extends IdentityBSONHandler[BSONLong] {
    protected val valueType = "BSONLong"

    protected def unapply(bson: BSONValue): Option[BSONLong] = bson match {
      case l: BSONLong => Some(l)
      case _ => None
    }
  }

  implicit object BSONDoubleIdentity extends IdentityBSONHandler[BSONDouble] {
    protected val valueType = "BSONDouble"

    protected def unapply(bson: BSONValue): Option[BSONDouble] = bson match {
      case d: BSONDouble => Some(d)
      case _ => None
    }
  }

  implicit object BSONObjectIDIdentity
    extends IdentityBSONHandler[BSONObjectID] {

    protected val valueType = "BSONObjectID"

    protected def unapply(bson: BSONValue): Option[BSONObjectID] = bson match {
      case i: BSONObjectID => Some(i)
      case _ => None
    }
  }

  implicit object BSONBinaryIdentity extends IdentityBSONHandler[BSONBinary] {
    protected val valueType = "BSONBinary"

    protected def unapply(bson: BSONValue): Option[BSONBinary] = bson match {
      case b: BSONBinary => Some(b)
      case _ => None
    }
  }

  implicit object BSONDateTimeIdentity
    extends IdentityBSONHandler[BSONDateTime] {

    protected val valueType = "BSONDateTime"

    protected def unapply(bson: BSONValue): Option[BSONDateTime] = bson match {
      case d: BSONDateTime => Some(d)
      case _ => None
    }
  }

  implicit object BSONNullIdentity extends IdentityBSONHandler[BSONNull] {
    protected val valueType = "BSONNull"

    protected def unapply(bson: BSONValue): Option[BSONNull] = bson match {
      case _: BSONNull => Some(BSONNull)
      case _ => None
    }
  }

  implicit object BSONUndefinedIdentity
    extends IdentityBSONHandler[BSONUndefined] {

    protected val valueType = "BSONUndefined"

    protected def unapply(bson: BSONValue): Option[BSONUndefined] =
      bson match {
        case _: BSONUndefined => Some(BSONUndefined)
        case _ => None
      }
  }

  implicit object BSONRegexIdentity extends IdentityBSONHandler[BSONRegex] {
    protected val valueType = "BSONRegex"

    protected def unapply(bson: BSONValue): Option[BSONRegex] = bson match {
      case r: BSONRegex => Some(r)
      case _ => None
    }
  }

  implicit object BSONJavaScriptIdentity
    extends IdentityBSONHandler[BSONJavaScript] {

    protected val valueType = "BSONJavaScript"

    protected def unapply(bson: BSONValue): Option[BSONJavaScript] =
      bson match {
        case js: BSONJavaScript => Some(js)
        case _ => None
      }
  }

  implicit object BSONJavaScriptWSIdentity
    extends IdentityBSONHandler[BSONJavaScriptWS] {

    protected val valueType = "BSONJavaScriptWS"

    protected def unapply(bson: BSONValue): Option[BSONJavaScriptWS] =
      bson match {
        case js: BSONJavaScriptWS => Some(js)
        case _ => None
      }
  }
}

private[bson] trait BSONIdentityLowPriorityHandlers { _: BSONIdentityHandlers =>
  implicit object BSONValueIdentity
    extends BSONReader[BSONValue] with BSONWriter[BSONValue] {

    @inline def writeTry(bson: BSONValue): Try[BSONValue] = Success(bson)
    @inline def readTry(bson: BSONValue): Try[BSONValue] = Success(bson)
  }
}
