package reactivemongo.api.bson

import scala.util.{ Failure, Success }

import scala.deriving.Mirror
import scala.reflect.Enum

/**
 * Utilities to handle [[https://dotty.epfl.ch/docs/reference/enums/enums.html Enumerations]]
 *
 * (Inspired by [[https://github.com/lloydmeta/enumeratum/blob/master/enumeratum-reactivemongo-bson/src/main/scala/enumeratum/EnumHandler.scala enumeratum]])
 */
object EnumHandler {

  /**
   * Returns a strict `BSONReader` for a given enum.
   */
  inline def reader[E <: Enum: Mirror.SumOf]: BSONReader[E] = reader(false)

  /**
   * Returns a `BSONReader` for a given enum.
   *
   * @param insensitive bind in a case-insensitive way, defaults to false
   */
  inline def reader[E <: Enum: Mirror.SumOf](
      insensitive: Boolean = false
    ): BSONReader[E] = {
    if (insensitive) {
      collect[E](EnumHelper.insensitiveValueOf[E])
    } else {
      collect[E](EnumHelper.strictValueOf[E])
    }
  }

  /**
   * Returns a `KeyReader` for a given enum.
   *
   * @param insensitive bind in a case-insensitive way, defaults to false
   */
  inline def keyReader[E <: Enum: Mirror.SumOf](
      insensitive: Boolean = false
    ): KeyReader[E] = {
    if (insensitive) {
      collectKey[E](EnumHelper.insensitiveValueOf[E])
    } else {
      collectKey[E](EnumHelper.strictValueOf[E])
    }
  }

  /**
   * Returns a `BSONReader` for a given enum transformed to lower case.
   */
  inline def readerLowercaseOnly[E <: Enum: Mirror.SumOf]: BSONReader[E] =
    collect[E](EnumHelper.lowerCaseValueOf[E])

  /**
   * Returns a `KeyReader` for a given enum transformed to lower case.
   */
  inline def keyReaderLowercaseOnly[E <: Enum: Mirror.SumOf]: KeyReader[E] =
    collectKey[E](EnumHelper.lowerCaseValueOf[E])

  /**
   * Returns a `BSONReader` for a given enum transformed to upper case.
   */
  inline def readerUppercaseOnly[E <: Enum: Mirror.SumOf]: BSONReader[E] =
    collect[E](EnumHelper.upperCaseValueOf[E])

  /**
   * Returns a `KeyReader` for a given enum transformed to upper case.
   */
  inline def keyReaderUppercaseOnly[E <: Enum: Mirror.SumOf]: KeyReader[E] =
    collectKey[E](EnumHelper.upperCaseValueOf[E])

  private def collect[E](f: String => Option[E]): BSONReader[E] =
    BSONReader.option[E] {
      case BSONString(str) => f(str)
      case _               => None
    }

  private def collectKey[E](f: String => Option[E]): KeyReader[E] =
    KeyReader.from[E] { key =>
      f(key) match {
        case Some(a) => Success(a)
        case _ => Failure(exceptions.TypeDoesNotMatchException(key, "key"))
      }
    }

  /**
   * Returns a `BSONWriter` for a given enum.
   */
  def writer[E <: Enum]: BSONWriter[E] =
    BSONWriter[E] { entry => BSONString(entry.productPrefix) }

  /**
   * Returns a `KeyWriter` for a given enum.
   */
  def keyWriter[E <: Enum]: KeyWriter[E] = KeyWriter[E](_.productPrefix)

  /**
   * Returns a `BSONWriter` for a given enum, the value as lower case.
   */
  def writerLowercase[E <: Enum]: BSONWriter[E] =
    BSONWriter[E] { entry => BSONString(entry.productPrefix.toLowerCase) }

  /**
   * Returns a `KeyWriter` for a given enum, the value as lower case.
   */
  def keyWriterLowercase[E <: Enum]: KeyWriter[E] =
    KeyWriter[E](_.productPrefix.toLowerCase)

  /**
   * Returns a `BSONWriter` for a given enum, the value as upper case.
   */
  def writerUppercase[E <: Enum]: BSONWriter[E] =
    BSONWriter[E] { entry => BSONString(entry.productPrefix.toUpperCase) }

  /**
   * Returns a `KeyWriter` for a given enum, the value as upper case.
   */
  def keyWriterUppercase[E <: Enum]: KeyWriter[E] =
    KeyWriter[E](_.productPrefix.toUpperCase)

  /**
   * Returns a strict `BSONHandler` for a given enum.
   */
  inline def handler[E <: Enum: Mirror.SumOf]: BSONHandler[E] =
    handler[E](false)

  /**
   * Returns a `BSONHandler` for a given enum.
   *
   * @param insensitive bind in a case-insensitive way, defaults to false
   */
  inline def handler[E <: Enum: Mirror.SumOf](
      insensitive: Boolean = false
    ): BSONHandler[E] =
    BSONHandler.provided[E](reader[E](insensitive), writer[E])

  /**
   * Returns a `BSONHandler` for a given enum,
   * handling a lower case transformation.
   */
  inline def handlerLowercaseOnly[E <: Enum: Mirror.SumOf]: BSONHandler[E] =
    BSONHandler.provided[E](readerLowercaseOnly[E], writerLowercase[E])

  /**
   * Returns a `BSONHandler` for a given enum,
   * handling an upper case transformation.
   */
  inline def handlerUppercaseOnly[E <: Enum: Mirror.SumOf]: BSONHandler[E] =
    BSONHandler.provided[E](readerUppercaseOnly[E], writerUppercase[E])
}
