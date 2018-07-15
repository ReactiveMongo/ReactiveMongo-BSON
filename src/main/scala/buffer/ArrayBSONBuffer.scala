package reactivemongo.api.bson.buffer

import scala.collection.mutable.ArrayBuffer

import java.nio.ByteBuffer

/** An array-backed writable buffer. */
class ArrayBSONBuffer protected[buffer] (
    protected val buffer: ArrayBuffer[Byte]) extends WritableBuffer {
  def index = buffer.length // useless

  def bytebuffer(size: Int): ByteBuffer = {
    val b = ByteBuffer.allocate(size)
    b.order(java.nio.ByteOrder.LITTLE_ENDIAN)
    b
  }

  // TODO: apply in companion
  def this() = this(new ArrayBuffer[Byte]())

  /** Returns an array containing all the data that were put in this buffer. */
  def array = buffer.toArray

  def setInt(index: Int, value: Int): this.type = {
    val array = bytebuffer(4).putInt(value).array
    buffer.update(index, array(0))
    buffer.update(index + 1, array(1))
    buffer.update(index + 2, array(2))
    buffer.update(index + 3, array(3))
    this
  }

  def toReadableBuffer = ArrayReadableBuffer(array)

  def writeBytes(array: Array[Byte]): this.type = {
    buffer ++= array
    //index += array.length
    this
  }

  def writeByte(byte: Byte): WritableBuffer = {
    buffer += byte
    //index += 1
    this
  }

  def writeInt(int: Int): WritableBuffer = {
    val array = bytebuffer(4).putInt(int).array
    buffer ++= array
    //index += 4
    this
  }

  def writeLong(long: Long): WritableBuffer = {
    buffer ++= bytebuffer(8).putLong(long).array
    //index += 8
    this
  }

  def writeDouble(double: Double): WritableBuffer = {
    buffer ++= bytebuffer(8).putDouble(double).array
    //index += 8
    this
  }
}
