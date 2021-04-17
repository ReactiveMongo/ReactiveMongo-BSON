package reactivemongo.api.bson.buffer

import java.nio.{ ByteBuffer, ByteOrder }

import scala.collection.mutable.ArrayBuffer

import reactivemongo.io.netty.buffer.{ ByteBuf, Unpooled }

/**
 * A readable buffer.
 *
 * @param buffer the underlying NIO buffer (must be little endian)
 */
private[reactivemongo] final class ReadableBuffer private[bson] (
    val buffer: ByteBuffer)
    extends AnyVal {

  /** Returns the buffer size. */
  @inline def size = buffer.capacity

  /** Fills the given array with the bytes read from this buffer. */
  def readBytes(bytes: Array[Byte]): ReadableBuffer = {
    buffer.get(bytes)
    this
  }

  /** Reads a `Byte` from this buffer. */
  def readByte(): Byte = buffer.get()

  /** Reads an `Int` from this buffer. */
  def readInt(): Int = buffer.getInt()

  /** Reads a `Long` from this buffer. */
  def readLong(): Long = buffer.getLong()

  /** Reads a `Double` from this buffer. */
  def readDouble(): Double = buffer.getDouble()

  /**
   * Reads a UTF-8 String.
   * @see WritableBuffer#writeBsonString
   */
  def readBsonString(): String = {
    val bytes = new Array[Byte](this.readInt() - 1)

    this.readBytes(bytes)
    this.readByte() // 0 delimiter byte

    new String(bytes, "UTF-8")
  }

  /**
   * Reads an array of Byte of the given length.
   *
   * @param length the length of the newly created array.
   */
  def readArray(length: Int): Array[Byte] = {
    val bytes = Array.ofDim[Byte](length)
    readBytes(bytes)
    bytes
  }

  /** Reads a UTF-8 C-Style String. */
  def readCString(): String =
    new String(takeUntilZero(new ArrayBuffer[Byte](16)), "UTF-8")

  /** Returns the number of readable remaining bytes of this buffer. */
  def readable(): Int = buffer.remaining()

  /** Returns a writable buffer with the same content as this one. */
  def toWritableBuffer: WritableBuffer =
    new WritableBuffer(Unpooled wrappedBuffer buffer)

  /** Returns a duplicate buffer. */
  def duplicate(): ReadableBuffer = new ReadableBuffer(buffer.duplicate())

  /** Skips the buffer content until `condition` is reached. */
  def skipUntil(condition: Byte => Boolean): ReadableBuffer = {
    @annotation.tailrec
    def go(): Unit = {
      if (buffer.remaining() > 0 && !condition(buffer.get())) go()
      else ()
    }

    go()

    this
  }

  /**
   * Splits this buffer after `n` bytes.
   * Note: The second buffer shares its state with the current one.
   */
  def splitAt(n: Int): (ReadableBuffer, ReadableBuffer) = {
    val r = buffer.remaining()
    val lim = if (r < n) r else n

    ReadableBuffer(readArray(lim)) -> this
  }

  // ---

  @scala.annotation.tailrec
  private def takeUntilZero(array: ArrayBuffer[Byte]): Array[Byte] = {
    val byte = this.readByte()

    if (byte == (0x00: Byte /* C end marker */ )) {
      array.toArray
    } else takeUntilZero(array += byte)
  }
}

private[reactivemongo] object ReadableBuffer {

  /** Returns a [[ReadableBuffer]] which source is the given `array`. */
  def apply(bytes: Array[Byte]): ReadableBuffer = {
    val buf = ByteBuffer.wrap(bytes)
    buf.order(ByteOrder.LITTLE_ENDIAN)

    new ReadableBuffer(buf)
  }

  /**
   * Returns an NIO [[ReadableBuffer]] which the same content
   * as the given Netty buffer.
   */
  def apply(buffer: ByteBuf): ReadableBuffer = {
    val buf = buffer.nioBuffer
    buf.order(ByteOrder.LITTLE_ENDIAN)

    new ReadableBuffer(buf)
  }
}
