package reactivemongo.api.bson.buffer

import scala.util.control.NonFatal

import reactivemongo.io.netty.buffer.{ ByteBuf, Unpooled }

/**
 * A writable buffer.
 *
 * The implementation '''MUST''' ensure it stores data in little endian
 * when needed.
 */
private[bson] final class WritableBuffer private[bson] (
  private[api] val buffer: ByteBuf) extends AnyVal {

  /** Returns the current size of this buffer. */
  @inline def size(): Int = buffer.readableBytes()

  /** Replaces 4 bytes at the given `index` by the given `value` */
  def setInt(index: Int, value: Int): WritableBuffer = {
    buffer.setIntLE(index, value)
    this
  }

  /** Writes the `bytes` into this buffer. */
  def writeBytes(bytes: Array[Byte]): WritableBuffer = {
    buffer.writeBytes(bytes)
    this
  }

  /** Writes the bytes stored in the given `buffer` into this buffer. */
  def writeBytes(buf: ReadableBuffer): WritableBuffer = {
    buffer.writeBytes(buf.buffer)
    this
  }

  /** Writes the given `byte` into this buffer. */
  def writeByte(byte: Byte): WritableBuffer = {
    buffer.writeByte(byte.toInt)
    this
  }

  /** Writes the given `int` into this buffer. */
  def writeInt(int: Int): WritableBuffer = {
    buffer.writeIntLE(int)
    this
  }

  /** Writes the given `long` into this buffer. */
  def writeLong(long: Long): WritableBuffer = {
    buffer.writeLongLE(long)
    this
  }

  /** Writes the given `double` into this buffer. */
  def writeDouble(double: Double): WritableBuffer = {
    buffer.writeDoubleLE(double)
    this
  }

  /** Returns the written content of this buffer as a readable one. */
  def toReadableBuffer(): ReadableBuffer = {
    val buf = buffer.slice(0, size).nioBuffer
    buf.order(java.nio.ByteOrder.LITTLE_ENDIAN)

    new ReadableBuffer(buf)
  }

  /** Write a UTF-8 encoded C-Style string. */
  def writeCString(s: String): WritableBuffer = {
    val bytes = s.getBytes("utf-8")
    writeBytes(bytes).writeByte(0)
  }

  /** Write a UTF-8 encoded string. */
  def writeBsonString(s: String): WritableBuffer = {
    val bytes = s.getBytes("utf-8")
    writeInt(bytes.size + 1).writeBytes(bytes).writeByte(0)
  }

  /** Returns an array containing all the data that were put in this buffer. */
  def array(): Array[Byte] = {
    val bytes = Array.ofDim[Byte](size)
    buffer.getBytes(0, bytes, 0, size)
    bytes
  }
}

private[reactivemongo] object WritableBuffer {
  lazy val initialBufferSize: Int = {
    val dsz = 96 // default

    sys.props.get("reactivemongo.api.bson.bufferSizeBytes").fold(dsz) { sz =>
      try {
        sz.toInt
      } catch {
        case NonFatal(_) => dsz
      }
    }
  }

  /**
   * Returns a fresh and empty buffer.
   *
   * Note: The initial capacity for the empty buffer can be customized,
   * using the system property `reactivemongo.api.bson.bufferSizeBytes`
   * (integer number of bytes). The default size is 64 bytes.
   */
  def empty: WritableBuffer =
    new WritableBuffer(Unpooled buffer initialBufferSize)

  def apply(bytes: Array[Byte]): WritableBuffer =
    new WritableBuffer(Unpooled copiedBuffer bytes)
}
