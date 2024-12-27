package reactivemongo.api.bson
package msb

import scala.language.implicitConversions

import scala.util.{ Failure, Success, Try }

import scala.reflect.ClassTag

import org.bson._
import org.bson.codecs._
import org.bson.codecs.configuration.CodecRegistry

/**
 * See [[msb$]] and [[HandlerConverters]]
 */
object HandlerConverters extends HandlerConverters {

  private[reactivemongo] def encode[T](
      value: T,
      enc: Encoder[T]
    ): Try[BsonValue] = {
    val container = new BsonDocument
    val writer = new BsonDocumentWriter(container)

    Try {
      val ctx = EncoderContext.builder.build()

      writer.writeStartDocument
      writer.writeName("_conv")

      enc.encode(writer, value, ctx)

      writer.writeEndDocument

      container.get("_conv")
    }
  }

  private[reactivemongo] def decode[T](
      bson: BsonValue,
      dec: Decoder[T]
    ): Try[T] = {
    val container = new BsonDocument("_conv", bson)
    val reader = new BsonDocumentReader(container)

    Try {
      val ctx = DecoderContext.builder.build()

      reader.readStartDocument
      val _: String = reader.readName() // assert("_conv")

      dec.decode(reader, ctx)
    }
  }
}

/**
 * Implicit conversions for handler types
 * between `org.bson` and `reactivemongo.api.bson` .
 *
 * {{{
 * import reactivemongo.api.bson.msb.HandlerConverters._
 *
 * def foo[T](enc: org.bson.codecs.Encoder[T]) = {
 *   val w: reactivemongo.api.bson.BSONWriter[T] = enc
 *   w
 * }
 *
 * def bar[T](lr: reactivemongo.api.bson.BSONReader[T]) = {
 *   val dec: org.bson.codecs.Decoder[T] = lr
 *   dec
 * }
 * }}}
 */
trait HandlerConverters extends LowPriorityHandlerConverters1 {

  implicit object DefaultCodecRegistry extends CodecRegistry {
    val BsonArrayClass = classOf[BsonArray].getName
    val BsonBinaryClass = classOf[BsonBinary].getName
    val BsonBooleanClass = classOf[BsonBoolean].getName
    val BsonDateTimeClass = classOf[BsonDateTime].getName
    val BsonDocumentClass = classOf[BsonDocument].getName
    val BsonDecimal128Class = classOf[BsonDecimal128].getName
    val BsonDoubleClass = classOf[BsonDouble].getName
    val BsonInt32Class = classOf[BsonInt32].getName
    val BsonInt64Class = classOf[BsonInt64].getName
    val BsonJavaScriptClass = classOf[BsonJavaScript].getName
    val BsonJavaScriptWithScopeClass = classOf[BsonJavaScriptWithScope].getName
    val BsonMaxKeyClass = classOf[BsonMaxKey].getName
    val BsonMinKeyClass = classOf[BsonMinKey].getName
    val BsonNullClass = classOf[BsonNull].getName
    val BsonNumberClass = classOf[BsonNumber].getName
    val BsonObjectIdClass = classOf[BsonObjectId].getName
    val BsonRegularExpressionClass = classOf[BsonRegularExpression].getName
    val BsonStringClass = classOf[BsonString].getName
    val BsonSymbolClass = classOf[BsonSymbol].getName
    val BsonTimestampClass = classOf[BsonTimestamp].getName
    val BsonUndefinedClass = classOf[BsonUndefined].getName

    def get[T](clazz: Class[T], r: CodecRegistry): Codec[T] = {
      val _ = r
      get(clazz)
    }

    def get[T](clazz: Class[T]): Codec[T] = {
      val c: Codec[_] = clazz.getName match {
        case BsonArrayClass      => new BsonArrayCodec
        case BsonBinaryClass     => new BsonBinaryCodec
        case BsonBooleanClass    => new BsonBooleanCodec
        case BsonDateTimeClass   => new BsonDateTimeCodec
        case BsonDocumentClass   => new BsonDocumentCodec
        case BsonDecimal128Class => new BsonDecimal128Codec
        case BsonDoubleClass     => new BsonDoubleCodec
        case BsonInt32Class      => new BsonInt32Codec
        case BsonInt64Class      => new BsonInt64Codec
        case BsonJavaScriptClass => new BsonJavaScriptCodec

        case BsonJavaScriptWithScopeClass =>
          new BsonJavaScriptWithScopeCodec(new BsonDocumentCodec)

        case BsonMaxKeyClass   => new BsonMaxKeyCodec
        case BsonMinKeyClass   => new BsonMinKeyCodec
        case BsonNullClass     => new BsonNullCodec
        case BsonObjectIdClass => new BsonObjectIdCodec

        case BsonRegularExpressionClass =>
          new BsonRegularExpressionCodec

        case BsonStringClass    => new BsonStringCodec
        case BsonSymbolClass    => new BsonSymbolCodec
        case BsonTimestampClass => new BsonTimestampCodec

        case _ => new BsonUndefinedCodec
      }

      @SuppressWarnings(Array("AsInstanceOf"))
      def codec = c.asInstanceOf[Codec[T]]

      codec
    }
  }

  implicit final def toHandler[T](h: Codec[T]): BSONHandler[T] =
    BSONHandler.provided[T](toReader(h), toWriter(h))

  implicit final def fromHandler[T](
      h: BSONHandler[T]
    )(implicit
      tt: ClassTag[T],
      cr: CodecRegistry
    ): Codec[T] =
    new DefaultCodec[T](fromReader[T](h)(cr), fromWriter[T](h), cr, tt)

  private final class DefaultCodec[T](
      val bsonReader: BSONReader[T],
      val bsonWriter: BSONWriter[T],
      val codecReg: CodecRegistry,
      val encoderClassTag: ClassTag[T])
      extends Codec[T]
      with BSONEncoder[T]
      with BSONDecoder[T] {

    @inline override def hashCode: Int =
      (encoderClassTag -> bsonReader).hashCode
  }
}

private[bson] sealed trait LowPriorityHandlerConverters1 {
  _self: HandlerConverters =>

  implicit final def toWriter[T](enc: Encoder[T]): BSONWriter[T] =
    BSONWriter.from[T] { v =>
      HandlerConverters.encode(v, enc).map(ValueConverters.toValue)
    }

  implicit final def toReader[T](dec: Decoder[T]): BSONReader[T] =
    BSONReader.from[T] { bson =>
      val v: BsonValue = ValueConverters.fromValue(bson)

      HandlerConverters.decode(v, dec)
    }

  implicit final def fromWriter[T](
      w: BSONWriter[T]
    )(implicit
      tt: ClassTag[T]
    ): Encoder[T] = new DefaultEncoder(w, tt)

  private final class DefaultEncoder[T](
      val bsonWriter: BSONWriter[T],
      val encoderClassTag: ClassTag[T])
      extends BSONEncoder[T]

  implicit final def fromReader[T](
      r: BSONReader[T]
    )(implicit
      cr: CodecRegistry
    ): Decoder[T] = new DefaultDecoder[T](r, cr)

  private final class DefaultDecoder[T](
      val bsonReader: BSONReader[T],
      val codecReg: CodecRegistry)
      extends BSONDecoder[T]
}

private[msb] sealed trait BSONEncoder[T] extends Encoder[T] with Serializable {
  protected def bsonWriter: BSONWriter[T]
  protected def encoderClassTag: ClassTag[T]

  private val underlying = new org.bson.codecs.BsonValueCodec

  def encode(writer: BsonWriter, value: T, ctx: EncoderContext): Unit = {
    val bsonValue: BsonValue = bsonWriter.writeTry(value) match {
      case Success(bson)  => ValueConverters.fromValue(bson)
      case Failure(cause) => throw cause
    }

    underlying.encode(writer, bsonValue, ctx)
  }

  @SuppressWarnings(Array("AsInstanceOf"))
  @inline def getEncoderClass(): Class[T] =
    encoderClassTag.runtimeClass.asInstanceOf[Class[T]]

  @inline override def hashCode: Int = encoderClassTag.hashCode
}

private[msb] sealed trait BSONDecoder[T] extends Decoder[T] {
  protected def bsonReader: BSONReader[T]
  protected def codecReg: CodecRegistry

  def decode(reader: BsonReader, ctx: DecoderContext): T = {
    val current = reader.getCurrentBsonType

    @SuppressWarnings(Array("NullParameter"))
    def resolved = {
      if (current != null) current
      else reader.readBsonType()
    }

    val v: BSONValue = resolved match {
      case BsonType.ARRAY =>
        ValueConverters.toArray(
          codecReg.get(classOf[BsonArray]).decode(reader, ctx)
        )

      case BsonType.BINARY =>
        ValueConverters.toBinary(reader.readBinaryData())

      case BsonType.BOOLEAN =>
        BSONBoolean(reader.readBoolean())

      case BsonType.DATE_TIME =>
        BSONDateTime(reader.readDateTime())

      case BsonType.DECIMAL128 =>
        ValueConverters.toDecimal(reader.readDecimal128())

      case BsonType.DOCUMENT =>
        ValueConverters.toDocument(
          codecReg.get(classOf[BsonDocument]).decode(reader, ctx)
        )

      case BsonType.DOUBLE =>
        BSONDouble(reader.readDouble())

      case BsonType.INT32 =>
        BSONInteger(reader.readInt32())

      case BsonType.INT64 =>
        BSONLong(reader.readInt64())

      case BsonType.JAVASCRIPT =>
        BSONJavaScript(reader.readJavaScript())

      case BsonType.JAVASCRIPT_WITH_SCOPE =>
        ValueConverters.toJavaScriptWS(
          codecReg.get(classOf[BsonJavaScriptWithScope]).decode(reader, ctx)
        )

      case BsonType.MAX_KEY => {
        reader.readMaxKey()
        BSONMaxKey
      }

      case BsonType.MIN_KEY => {
        reader.readMinKey()
        BSONMinKey
      }

      case BsonType.NULL => {
        reader.readNull()
        BSONNull
      }

      case BsonType.OBJECT_ID =>
        ValueConverters.toObjectID(reader.readObjectId())

      case BsonType.REGULAR_EXPRESSION =>
        ValueConverters.toRegex(reader.readRegularExpression())

      case BsonType.STRING =>
        BSONString(reader.readString())

      case BsonType.SYMBOL =>
        BSONSymbol(reader.readSymbol())

      case BsonType.TIMESTAMP =>
        ValueConverters.toTimestamp(reader.readTimestamp())

      case _ => {
        reader.readUndefined()
        BSONUndefined
      }
    }

    bsonReader.readTry(v) match {
      case Success(result) => result
      case Failure(cause)  => throw cause
    }
  }

  @inline override def hashCode: Int = (bsonReader -> codecReg).hashCode
}
