package reactivemongo.api.bson.buffer

import scala.util.{ Failure, Success, Try }

import scala.collection.mutable.{ Map => MMap }
import scala.collection.immutable.IndexedSeq

import reactivemongo.api.bson._

private[reactivemongo] object DefaultBufferHandler {
  def serialize(bson: BSONValue, buffer: WritableBuffer): WritableBuffer =
    bson match {
      case BSONDouble(v) => buffer writeDouble v
      case BSONString(v) => buffer writeBsonString v
      case doc: BSONDocument => writeDocument(doc, buffer)
      case BSONArray(vs) => writeArray(vs, buffer)
      case v: BSONBinary => writeBinary(v, buffer)
      case oid: BSONObjectID => writeObjectID(oid, buffer)
      case BSONBoolean(v) => buffer writeByte (if (v) 1 else 0)
      case BSONDateTime(time) => buffer writeLong time
      case v: BSONRegex => writeRegex(v, buffer)
      case BSONJavaScript(v) => buffer writeBsonString v
      case BSONSymbol(name) => buffer writeBsonString name

      case BSONJavaScriptWS(v, s) => {
        buffer writeBsonString v
        writeDocument(s, buffer)
      }

      case BSONInteger(v) => buffer writeInt v
      case BSONTimestamp(time) => buffer writeLong time
      case BSONLong(v) => buffer writeLong v
      case dec: BSONDecimal => writeDecimal(dec, buffer)

      case _ =>
        // Constant values: null, minKey, maxKey, undefined
        buffer // unchanged
    }

  def deserialize(buffer: ReadableBuffer): Try[BSONValue] =
    Try(readValue(buffer, code = buffer.readByte()))

  def writeArray(vs: IndexedSeq[BSONValue], buffer: WritableBuffer) = {
    val szBefore = buffer.size

    buffer.writeInt(0) // initial BSON size

    var i = 0
    vs.foreach { v =>
      buffer.writeByte(v.code)
      buffer.writeCString(i.toString)
      serialize(v, buffer)
      i += 1
    }

    buffer.setInt(szBefore, (buffer.size - szBefore + 1)) // reset size

    buffer.writeByte(0) // writeArray#write_1
    buffer
  }

  def writeDocument(
    document: BSONDocument,
    buffer: WritableBuffer): WritableBuffer = {
    val szBefore = buffer.size

    buffer.writeInt(0) // initial (unknown:0) document size

    document.elements.foreach {
      case BSONElement(k, v) =>
        buffer.writeByte(v.code)
        buffer.writeCString(k)
        serialize(v, buffer)
    }

    buffer.setInt(szBefore, (buffer.size - szBefore + 1)) // reset size
    buffer.writeByte(0) // writeDocument#write_1

    buffer
  }

  // ---

  private def readDouble(buffer: ReadableBuffer): BSONDouble =
    BSONDouble(buffer.readDouble())

  private[bson] def readString(buffer: ReadableBuffer): BSONString =
    BSONString(buffer.readBsonString())

  /**
   * Reads a document from the `buffer`.
   *
   * Note that the buffer's readerIndex must be set on the start of a document, or it will fail.
   */
  private[bson] def readDocument(buffer: ReadableBuffer): BSONDocument = {
    val _ = buffer.readInt() // length

    // assert(length == b.size)

    val elms = Seq.newBuilder[BSONElement]
    val fields = MMap.empty[String, BSONValue]

    @scala.annotation.tailrec
    def read(): Unit = {
      lazy val code = buffer.readByte()

      if (buffer.readable() > 1 && code != (0x0: Byte)) {
        // Last is 0 (see readDocument#write_1)

        val name = buffer.readCString()
        val v = readValue(buffer, code)

        elms += BSONElement(name, v)
        fields.put(name, v)

        read()
      }
    }

    read()

    BSONDocument(elms.result(), fields.toMap)
  }

  private[bson] def readArray(buffer: ReadableBuffer): BSONArray = {
    val _ = buffer.readInt() // length
    val builder = IndexedSeq.newBuilder[BSONValue]

    @scala.annotation.tailrec
    def makeSeq(): IndexedSeq[BSONValue] = {
      lazy val code = buffer.readByte()

      if (buffer.readable > 1 && code != (0x0: Byte)) {
        // Last is 0 (see writeArray#write_1)
        buffer.skipUntil(_ == (0x0: Byte)) // C string delimiter

        builder += readValue(buffer, code)

        makeSeq()
      } else builder.result()
    }

    BSONArray(makeSeq())
  }

  @inline def writeBinary(binary: BSONBinary, buffer: WritableBuffer) =
    buffer.writeInt(binary.value.readable).
      writeByte(binary.subtype.value).
      writeBytes(binary.value.duplicate())

  def readBinary(buffer: ReadableBuffer): BSONBinary = {
    val readable = buffer.readInt()
    val subtype = Subtype(buffer.readByte())
    val (data, _) = buffer.splitAt(readable)

    new BSONBinary(data, subtype)
  }

  @inline def writeObjectID(oid: BSONObjectID, buffer: WritableBuffer) =
    buffer writeBytes oid.byteArray

  private[bson] def readObjectID(buffer: ReadableBuffer): BSONObjectID = {
    BSONObjectID.parse(buffer readArray 12) match {
      case Success(oid) => oid
      case Failure(err) => throw err
    }
  }

  private def readBoolean(buffer: ReadableBuffer): BSONBoolean = {
    BSONBoolean(buffer.readByte() == (0x01: Byte))
  }

  private def readDateTime(buffer: ReadableBuffer): BSONDateTime = {
    BSONDateTime(buffer.readLong())
  }

  def writeRegex(regex: BSONRegex, buffer: WritableBuffer) = {
    buffer writeCString regex.value
    buffer writeCString regex.flags
  }

  private def readRegex(buffer: ReadableBuffer): BSONRegex = {
    BSONRegex(buffer.readCString(), buffer.readCString())
  }

  private def readJavaScript(buffer: ReadableBuffer): BSONJavaScript = {
    BSONJavaScript(buffer.readBsonString())
  }

  private def readSymbol(buffer: ReadableBuffer): BSONSymbol = {
    BSONSymbol(buffer.readBsonString())
  }

  private def readJavaScriptWS(buffer: ReadableBuffer): BSONJavaScriptWS = {
    BSONJavaScriptWS(buffer.readBsonString(), readDocument(buffer))
  }

  private def readInteger(buffer: ReadableBuffer): BSONInteger = {
    BSONInteger(buffer.readInt)
  }

  private def readTimestamp(buffer: ReadableBuffer): BSONTimestamp = {
    BSONTimestamp(buffer.readLong)
  }

  private def readLong(buffer: ReadableBuffer): BSONLong = {
    BSONLong(buffer.readLong)
  }

  @inline def writeDecimal(
    decimal: BSONDecimal, buffer: WritableBuffer) =
    buffer.writeLong(decimal.low).writeLong(decimal.high)

  private[bson] def readDecimal(buffer: ReadableBuffer): BSONDecimal = {
    BSONDecimal(low = buffer.readLong(), high = buffer.readLong())
  }

  private[bson] def readValue(buffer: ReadableBuffer, code: Byte): BSONValue = {
    if (buffer.readable > 0) {
      (code: @annotation.switch) match {
        case 0x01 => readDouble(buffer)
        case 0x02 => readString(buffer)
        case 0x03 => readDocument(buffer)
        case 0x04 => readArray(buffer)
        case 0x05 => readBinary(buffer)
        case 0x06 => BSONUndefined
        case 0x07 => readObjectID(buffer)
        case 0x08 => readBoolean(buffer)
        case 0x09 => readDateTime(buffer)
        case 0x10 => readInteger(buffer)
        case 0x11 => readTimestamp(buffer)
        case 0x12 => readLong(buffer)
        case 0x13 => readDecimal(buffer)
        case 0x0A => BSONNull
        case 0x0B => readRegex(buffer)
        case 0x0D => readJavaScript(buffer)
        case 0x0E => readSymbol(buffer)
        case 0x0F => readJavaScriptWS(buffer)
        case 0xFF => BSONMinKey
        case 0x7F => BSONMaxKey

        case _ =>
          throw new IllegalArgumentException(s"invalid type code: $code")
      }
    } else {
      throw new NoSuchElementException(
        "buffer can not be read, end of buffer reached")
    }
  }
}
