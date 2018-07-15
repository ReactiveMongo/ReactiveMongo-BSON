package reactivemongo.api.bson.buffer

import reactivemongo.api.bson._
import scala.util.Try

object DefaultBufferHandler extends BufferHandler {
  sealed trait BufferWriter[B <: BSONValue] {
    def write(value: B, buffer: WritableBuffer): WritableBuffer
  }

  sealed trait BufferReader[B <: BSONValue] {
    def read(buffer: ReadableBuffer): B
  }

  sealed trait BufferRW[B <: BSONValue]
    extends BufferWriter[B] with BufferReader[B]

  val handlersByCode: Map[Byte, BufferRW[_ <: BSONValue]] = Map(
    (0x01: Byte) -> BSONDoubleBufferHandler,
    (0x02: Byte) -> BSONStringBufferHandler,
    (0x03: Byte) -> BSONDocumentBufferHandler,
    (0x04: Byte) -> BSONArrayBufferHandler, // array
    (0x05: Byte) -> BSONBinaryBufferHandler, // binary TODO
    (0x06: Byte) -> BSONUndefinedBufferHandler, // undefined,
    (0x07: Byte) -> BSONObjectIDBufferHandler, // objectid,
    (0x08: Byte) -> BSONBooleanBufferHandler, // boolean
    (0x09: Byte) -> BSONDateTimeBufferHandler, // datetime
    (0x0A: Byte) -> BSONNullBufferHandler, // null
    (0x0B: Byte) -> BSONRegexBufferHandler, // regex
    (0x0D: Byte) -> BSONJavaScriptBufferHandler, // JS
    (0x0E: Byte) -> BSONSymbolBufferHandler, // symbol
    (0x0F: Byte) -> BSONJavaScriptWSBufferHandler, // JS with scope
    (0x10: Byte) -> BSONIntegerBufferHandler,
    (0x11: Byte) -> BSONTimestampBufferHandler, // timestamp,
    (0x12: Byte) -> BSONLongBufferHandler, // long,
    (0x13: Byte) -> BSONDecimalBufferHandler, // decimal
    0xFF.toByte -> BSONMinKeyBufferHandler, // min
    (0x7F: Byte) -> BSONMaxKeyBufferHandler) // max

  object BSONDoubleBufferHandler extends BufferRW[BSONDouble] {
    def write(value: BSONDouble, buffer: WritableBuffer): WritableBuffer =
      buffer.writeDouble(value.value)

    def read(buffer: ReadableBuffer): BSONDouble = BSONDouble(buffer.readDouble)
  }
  object BSONStringBufferHandler extends BufferRW[BSONString] {
    def write(value: BSONString, buffer: WritableBuffer): WritableBuffer = buffer.writeString(value.value)
    def read(buffer: ReadableBuffer): BSONString = BSONString(buffer.readString)
  }
  object BSONDocumentBufferHandler extends BufferRW[BSONDocument] {
    def write(doc: BSONDocument, buffer: WritableBuffer) = {
      val now = buffer.index

      buffer.writeInt(0) // initial document size

      doc.elements.foreach { e =>
        buffer.writeByte(e.value.code.toByte)
        buffer.writeCString(e.name)
        serialize(e.value, buffer)
      }

      buffer.setInt(now, (buffer.index - now + 1)) // reset size
      buffer.writeByte(0)

      buffer
    }

    def read(b: ReadableBuffer) = {
      val length = b.readInt
      val buffer = b.slice(length - 4)
      b.discard(length - 4)
      def makeStream(): Stream[Try[BSONElement]] = {
        if (buffer.readable > 1) { // last is 0
          val code = buffer.readByte
          val name = buffer.readCString
          val value = Try {
            DefaultBufferHandler.handlersByCode.get(code).
              map(_.read(buffer)) match {
                case Some(v) => v
                case _ => throw new NoSuchElementException(
                  "buffer can not be read, end of buffer reached")
              }
          }

          value.map(BSONElement(name, _)) #:: makeStream
        } else Stream.empty
      }
      val stream = makeStream
      stream.force // TODO remove
      new BSONDocument(stream)
    }
  }

  object BSONArrayBufferHandler extends BufferRW[BSONArray] {
    def write(array: BSONArray, buffer: WritableBuffer) = {
      val now = buffer.index

      buffer.writeInt(0) // initial BSON size

      array.values.zipWithIndex.foreach { e =>
        buffer.writeByte(e._1.code.toByte)
        buffer.writeCString(e._2.toString)
        serialize(e._1, buffer)
      }

      buffer.setInt(now, (buffer.index - now + 1)) // reset size

      buffer.writeByte(0)
      buffer
    }

    def read(b: ReadableBuffer) = {
      val length = b.readInt
      val buffer = b.slice(length - 4)
      b.discard(length - 4)

      def makeStream(): Stream[Try[BSONValue]] = {
        if (buffer.readable > 1) { // last is 0
          val code = buffer.readByte
          buffer.readCString // skip name

          val elem = Try {
            DefaultBufferHandler.handlersByCode.get(code).
              map(_.read(buffer)) match {
                case Some(v) => v
                case _ => throw new NoSuchElementException(
                  "buffer can not be read, end of buffer reached")
              }
          }

          elem #:: makeStream
        } else Stream.empty
      }

      val stream = makeStream
      stream.force // TODO remove
      new BSONArray(stream)
    }
  }

  object BSONBinaryBufferHandler extends BufferRW[BSONBinary] {
    def write(binary: BSONBinary, buffer: WritableBuffer) = {
      buffer.writeInt(binary.value.readable)
      buffer.writeByte(binary.subtype.value.toByte)
      val bin = binary.value.slice(binary.value.readable)
      buffer.writeBytes(bin.readArray(bin.readable)) // TODO
      buffer
    }
    def read(buffer: ReadableBuffer) = {
      val length = buffer.readInt
      val subtype = Subtype.apply(buffer.readByte)
      val bin = buffer.slice(length)
      buffer.discard(length)
      BSONBinary(bin, subtype)
    }
  }

  object BSONUndefinedBufferHandler extends BufferRW[BSONUndefined.type] {
    def write(undefined: BSONUndefined.type, buffer: WritableBuffer) = buffer
    def read(buffer: ReadableBuffer) = BSONUndefined
  }

  object BSONObjectIDBufferHandler extends BufferRW[BSONObjectID] {
    def write(objectId: BSONObjectID, buffer: WritableBuffer) =
      buffer writeBytes objectId.valueAsArray

    def read(buffer: ReadableBuffer) = {
      val array = buffer.readArray(12)

      if (array.length != 12) {
        throw new IllegalArgumentException(s"wrong byte array for an ObjectId (size ${array.length})")
      }

      new BSONObjectID(java.util.Arrays.copyOf(array, 12))
    }
  }

  object BSONBooleanBufferHandler extends BufferRW[BSONBoolean] {
    def write(boolean: BSONBoolean, buffer: WritableBuffer) =
      buffer writeByte (if (boolean.value) 1 else 0)

    def read(buffer: ReadableBuffer) =
      BSONBoolean(buffer.readByte == (0x01: Byte))
  }

  object BSONDateTimeBufferHandler extends BufferRW[BSONDateTime] {
    def write(dateTime: BSONDateTime, buffer: WritableBuffer) =
      buffer writeLong dateTime.value

    def read(buffer: ReadableBuffer) = BSONDateTime(buffer.readLong)
  }

  object BSONNullBufferHandler extends BufferRW[BSONNull.type] {
    def write(`null`: BSONNull.type, buffer: WritableBuffer) = buffer
    def read(buffer: ReadableBuffer) = BSONNull
  }
  object BSONRegexBufferHandler extends BufferRW[BSONRegex] {
    def write(regex: BSONRegex, buffer: WritableBuffer) = { buffer writeCString regex.value; buffer writeCString regex.flags }
    def read(buffer: ReadableBuffer) = BSONRegex(buffer.readCString, buffer.readCString)
  }

  object BSONJavaScriptBufferHandler extends BufferRW[BSONJavaScript] {
    def write(js: BSONJavaScript, buffer: WritableBuffer) = buffer writeString js.value
    def read(buffer: ReadableBuffer) = BSONJavaScript(buffer.readString)
  }
  object BSONSymbolBufferHandler extends BufferRW[BSONSymbol] {
    def write(symbol: BSONSymbol, buffer: WritableBuffer) = buffer writeString symbol.value
    def read(buffer: ReadableBuffer) = BSONSymbol(buffer.readString)
  }
  object BSONJavaScriptWSBufferHandler extends BufferRW[BSONJavaScriptWS] {
    def write(jsws: BSONJavaScriptWS, buffer: WritableBuffer) = buffer writeString jsws.value
    def read(buffer: ReadableBuffer) = BSONJavaScriptWS(buffer.readString)
  }
  object BSONIntegerBufferHandler extends BufferRW[BSONInteger] {
    def write(value: BSONInteger, buffer: WritableBuffer) = buffer writeInt value.value
    def read(buffer: ReadableBuffer): BSONInteger = BSONInteger(buffer.readInt)
  }
  object BSONTimestampBufferHandler extends BufferRW[BSONTimestamp] {
    def write(ts: BSONTimestamp, buffer: WritableBuffer) = buffer writeLong ts.value
    def read(buffer: ReadableBuffer) = BSONTimestamp(buffer.readLong)
  }
  object BSONLongBufferHandler extends BufferRW[BSONLong] {
    def write(long: BSONLong, buffer: WritableBuffer) =
      buffer writeLong long.value

    def read(buffer: ReadableBuffer) = BSONLong(buffer.readLong)
  }

  object BSONDecimalBufferHandler extends BufferRW[BSONDecimal] {
    def write(decimal: BSONDecimal, buffer: WritableBuffer) =
      buffer.writeLong(decimal.low).writeLong(decimal.high)

    def read(buffer: ReadableBuffer): BSONDecimal =
      BSONDecimal(low = buffer.readLong(), high = buffer.readLong())
  }

  object BSONMinKeyBufferHandler extends BufferRW[BSONMinKey.type] {
    def write(b: BSONMinKey.type, buffer: WritableBuffer) = buffer
    def read(buffer: ReadableBuffer) = BSONMinKey
  }
  object BSONMaxKeyBufferHandler extends BufferRW[BSONMaxKey.type] {
    def write(b: BSONMaxKey.type, buffer: WritableBuffer) = buffer
    def read(buffer: ReadableBuffer) = BSONMaxKey
  }

  // TODO: Review
  @SuppressWarnings(Array("OptionGet", "AsInstanceOf"))
  def serialize(bson: BSONValue, buffer: WritableBuffer): WritableBuffer =
    handlersByCode.get(bson.code).get.
      asInstanceOf[BufferRW[BSONValue]].write(bson, buffer)

  def deserialize(buffer: ReadableBuffer): Try[(String, BSONValue)] = Try {
    def readError = new NoSuchElementException(
      "buffer can not be read, end of buffer reached")

    if (buffer.readable > 0) {
      val code = buffer.readByte

      handlersByCode.get(code).map(_.read(buffer)) match {
        case Some(v) => buffer.readString -> v
        case _       => throw readError
      }
    } else throw readError
  }

  def readDocument(buffer: ReadableBuffer): Try[BSONDocument] =
    Try(BSONDocumentBufferHandler read buffer)

  def writeDocument(
    document: BSONDocument,
    buffer: WritableBuffer): WritableBuffer = serialize(document, buffer)
}
