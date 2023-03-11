package reactivemongo.api.bson

import scala.util.{ Failure, Success, Try }

private[bson] trait BSONIdentityHandlers
    extends BSONIdentityLowPriorityHandlers { self: DefaultBSONHandlers =>

  import exceptions.TypeDoesNotMatchException

  private object BSONStringIdentity extends IdentityBSONHandler[BSONString] {
    protected val valueType = "BSONString"

    protected def unapply(bson: BSONValue): Option[BSONString] = bson match {
      case s: BSONString => Some(s)
      case _             => None
    }
  }

  // Separately expose identity handler as reader & writer,
  // to allow to override implicit (workaround)

  @inline implicit def bsonStringReader: BSONReader[BSONString] =
    BSONStringIdentity

  @inline implicit def bsonStringWriter: BSONWriter[BSONString] =
    BSONStringIdentity

  private object BSONSymbolIdentity extends IdentityBSONHandler[BSONSymbol] {
    protected val valueType = "BSONSymbol"

    protected def unapply(bson: BSONValue): Option[BSONSymbol] = bson match {
      case s: BSONSymbol => Some(s)
      case _             => None
    }
  }

  @inline implicit def bsonSymbolReader: BSONReader[BSONSymbol] =
    BSONSymbolIdentity

  @inline implicit def bsonSymbolWriter: BSONWriter[BSONSymbol] =
    BSONSymbolIdentity

  private object BSONIntegerIdentity extends IdentityBSONHandler[BSONInteger] {
    protected val valueType = "BSONInteger"

    protected def unapply(bson: BSONValue): Option[BSONInteger] = bson match {
      case i: BSONInteger => Some(i)
      case _              => None
    }
  }

  @inline implicit def bsonIntegerReader: BSONReader[BSONInteger] =
    BSONIntegerIdentity

  @inline implicit def bsonIntegerWriter: BSONWriter[BSONInteger] =
    BSONIntegerIdentity

  private object BSONDecimalIdentity extends IdentityBSONHandler[BSONDecimal] {
    protected val valueType = "BSONDecimal"

    protected def unapply(bson: BSONValue): Option[BSONDecimal] = bson match {
      case d: BSONDecimal => Some(d)
      case _              => None
    }
  }

  @inline implicit def bsonDecimalReader: BSONReader[BSONDecimal] =
    BSONDecimalIdentity

  @inline implicit def bsonDecimalWriter: BSONWriter[BSONDecimal] =
    BSONDecimalIdentity

  private object BSONArrayIdentity extends IdentityBSONHandler[BSONArray] {
    protected val valueType = "BSONArray"

    protected def unapply(bson: BSONValue): Option[BSONArray] = bson match {
      case a: BSONArray => Some(a)
      case _            => None
    }
  }

  @inline implicit def bsonArrayReader: BSONReader[BSONArray] =
    BSONArrayIdentity

  @inline implicit def bsonArrayWriter: BSONWriter[BSONArray] =
    BSONArrayIdentity

  private object BSONDocumentIdentity
      extends BSONDocumentReader[BSONDocument]
      with BSONDocumentWriter[BSONDocument] {

    @inline def writeTry(doc: BSONDocument): Try[BSONDocument] =
      Success(doc)

    def readDocument(doc: BSONDocument): Try[BSONDocument] =
      Success(doc)
  }

  @inline implicit def bsonDocumentReader: BSONDocumentReader[BSONDocument] =
    BSONDocumentIdentity

  @inline implicit def bsonDocumentWriter: BSONDocumentWriter[BSONDocument] =
    BSONDocumentIdentity

  private object BSONBooleanIdentity extends IdentityBSONHandler[BSONBoolean] {
    protected val valueType = "BSONBoolean"

    protected def unapply(bson: BSONValue): Option[BSONBoolean] = bson match {
      case b: BSONBoolean => Some(b)
      case _              => None
    }
  }

  @inline implicit def bsonBooleanReader: BSONReader[BSONBoolean] =
    BSONBooleanIdentity

  @inline implicit def bsonBooleanWriter: BSONWriter[BSONBoolean] =
    BSONBooleanIdentity

  private object BSONLongIdentity extends IdentityBSONHandler[BSONLong] {
    protected val valueType = "BSONLong"

    protected def unapply(bson: BSONValue): Option[BSONLong] = bson match {
      case l: BSONLong => Some(l)
      case _           => None
    }
  }

  @inline implicit def bsonLongReader: BSONReader[BSONLong] =
    BSONLongIdentity

  @inline implicit def bsonLongWriter: BSONWriter[BSONLong] =
    BSONLongIdentity

  private object BSONDoubleIdentity extends IdentityBSONHandler[BSONDouble] {
    protected val valueType = "BSONDouble"

    protected def unapply(bson: BSONValue): Option[BSONDouble] = bson match {
      case d: BSONDouble => Some(d)
      case _             => None
    }
  }

  @inline implicit def bsonDoubleReader: BSONReader[BSONDouble] =
    BSONDoubleIdentity

  @inline implicit def bsonDoubleWriter: BSONWriter[BSONDouble] =
    BSONDoubleIdentity

  private object BSONObjectIDIdentity
      extends IdentityBSONHandler[BSONObjectID] {

    protected val valueType = "BSONObjectID"

    protected def unapply(bson: BSONValue): Option[BSONObjectID] = bson match {
      case i: BSONObjectID => Some(i)
      case _               => None
    }
  }

  @inline implicit def bsonObjectIDReader: BSONReader[BSONObjectID] =
    BSONObjectIDIdentity

  @inline implicit def bsonObjectIDWriter: BSONWriter[BSONObjectID] =
    BSONObjectIDIdentity

  private object BSONBinaryIdentity extends IdentityBSONHandler[BSONBinary] {
    protected val valueType = "BSONBinary"

    protected def unapply(bson: BSONValue): Option[BSONBinary] = bson match {
      case b: BSONBinary => Some(b)
      case _             => None
    }
  }

  @inline implicit def bsonBinaryReader: BSONReader[BSONBinary] =
    BSONBinaryIdentity

  @inline implicit def bsonBinaryWriter: BSONWriter[BSONBinary] =
    BSONBinaryIdentity

  private object BSONDateTimeIdentity
      extends IdentityBSONHandler[BSONDateTime] {

    protected val valueType = "BSONDateTime"

    protected def unapply(bson: BSONValue): Option[BSONDateTime] = bson match {
      case d: BSONDateTime => Some(d)
      case _               => None
    }
  }

  @inline implicit def bsonDateTimeReader: BSONReader[BSONDateTime] =
    BSONDateTimeIdentity

  @inline implicit def bsonDateTimeWriter: BSONWriter[BSONDateTime] =
    BSONDateTimeIdentity

  private object BSONTimestampIdentity
      extends IdentityBSONHandler[BSONTimestamp] {

    protected val valueType = "BSONTimestamp"

    protected def unapply(bson: BSONValue): Option[BSONTimestamp] = bson match {
      case d: BSONTimestamp => Some(d)
      case _                => None
    }
  }

  @inline implicit def bsonTimestampReader: BSONReader[BSONTimestamp] =
    BSONTimestampIdentity

  @inline implicit def bsonTimestampWriter: BSONWriter[BSONTimestamp] =
    BSONTimestampIdentity

  private object BSONMaxKeyIdentity extends IdentityBSONHandler[BSONMaxKey] {
    protected val valueType = "BSONMaxKey"

    protected def unapply(bson: BSONValue): Option[BSONMaxKey] =
      bson match {
        case _: BSONMaxKey => Some(BSONMaxKey)
        case _             => None
      }
  }

  @inline implicit def bsonMaxKeyReader: BSONReader[BSONMaxKey] =
    BSONMaxKeyIdentity

  @inline implicit def bsonMaxKeyWriter: BSONWriter[BSONMaxKey] =
    BSONMaxKeyIdentity

  private object BSONMinKeyIdentity extends IdentityBSONHandler[BSONMinKey] {
    protected val valueType = "BSONMinKey"

    protected def unapply(bson: BSONValue): Option[BSONMinKey] =
      bson match {
        case _: BSONMinKey => Some(BSONMinKey)
        case _             => None
      }
  }

  @inline implicit def bsonMinKeyReader: BSONReader[BSONMinKey] =
    BSONMinKeyIdentity

  @inline implicit def bsonMinKeyWriter: BSONWriter[BSONMinKey] =
    BSONMinKeyIdentity

  private object BSONNullIdentity extends IdentityBSONHandler[BSONNull] {
    protected val valueType = "BSONNull"

    protected def unapply(bson: BSONValue): Option[BSONNull] = bson match {
      case _: BSONNull => Some(BSONNull)
      case _           => None
    }
  }

  @inline implicit def bsonNullReader: BSONReader[BSONNull] =
    BSONNullIdentity

  @inline implicit def bsonNullWriter: BSONWriter[BSONNull] =
    BSONNullIdentity

  private object BSONUndefinedIdentity
      extends IdentityBSONHandler[BSONUndefined] {

    protected val valueType = "BSONUndefined"

    protected def unapply(bson: BSONValue): Option[BSONUndefined] =
      bson match {
        case _: BSONUndefined => Some(BSONUndefined)
        case _                => None
      }
  }

  @inline implicit def bsonUndefinedReader: BSONReader[BSONUndefined] =
    BSONUndefinedIdentity

  @inline implicit def bsonUndefinedWriter: BSONWriter[BSONUndefined] =
    BSONUndefinedIdentity

  private object BSONRegexIdentity extends IdentityBSONHandler[BSONRegex] {
    protected val valueType = "BSONRegex"

    protected def unapply(bson: BSONValue): Option[BSONRegex] = bson match {
      case r: BSONRegex => Some(r)
      case _            => None
    }
  }

  @inline implicit def bsonRegexReader: BSONReader[BSONRegex] =
    BSONRegexIdentity

  @inline implicit def bsonRegexWriter: BSONWriter[BSONRegex] =
    BSONRegexIdentity

  private object BSONJavaScriptIdentity
      extends IdentityBSONHandler[BSONJavaScript] {

    protected val valueType = "BSONJavaScript"

    protected def unapply(bson: BSONValue): Option[BSONJavaScript] =
      bson match {
        case js: BSONJavaScript => Some(js)
        case _                  => None
      }
  }

  @inline implicit def bsonJavaScriptReader: BSONReader[BSONJavaScript] =
    BSONJavaScriptIdentity

  @inline implicit def bsonJavaScriptWriter: BSONWriter[BSONJavaScript] =
    BSONJavaScriptIdentity

  private object BSONJavaScriptWSIdentity
      extends IdentityBSONHandler[BSONJavaScriptWS] {

    protected val valueType = "BSONJavaScriptWS"

    protected def unapply(bson: BSONValue): Option[BSONJavaScriptWS] =
      bson match {
        case js: BSONJavaScriptWS => Some(js)
        case _                    => None
      }
  }

  @inline implicit def bsonJavaScriptWSReader: BSONReader[BSONJavaScriptWS] =
    BSONJavaScriptWSIdentity

  @inline implicit def bsonJavaScriptWSWriter: BSONWriter[BSONJavaScriptWS] =
    BSONJavaScriptWSIdentity

  // ---

  private[bson] sealed trait IdentityBSONHandler[B <: BSONValue]
      extends BSONReader[B]
      with BSONWriter[B]
      with SafeBSONWriter[B] {

    protected def valueType: String

    protected def unapply(bson: BSONValue): Option[B]

    @inline final def safeWrite(bson: B) = bson

    final def readTry(bson: BSONValue): Try[B] = unapply(bson) match {
      case Some(v) => Success(v)

      case _ =>
        Failure(TypeDoesNotMatchException(valueType, bson))
    }

    final override def readOpt(bson: BSONValue): Option[B] = unapply(bson)
  }
}

private[bson] trait BSONIdentityLowPriorityHandlers {
  self: DefaultBSONHandlers =>

  implicit object BSONValueIdentity extends IdentityBSONHandler[BSONValue] {
    protected val valueType = "BSONValue"

    protected def unapply(bson: BSONValue) = Some(bson)
  }
}
