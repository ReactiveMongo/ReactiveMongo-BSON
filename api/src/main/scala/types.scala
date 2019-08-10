package reactivemongo.api.bson

import java.math.{ BigDecimal => JBigDec }

import java.time.Instant

import java.nio.ByteBuffer

import scala.language.implicitConversions

import scala.util.{ Failure, Success, Try }
import scala.util.control.NonFatal

import scala.collection.mutable.{ HashMap => MMap }
import scala.collection.immutable.{ HashMap, IndexedSeq }

import buffer._

import exceptions.{ BSONValueNotFoundException, TypeDoesNotMatchException }

sealed trait Producer[T] {
  private[bson] def generate(): Iterable[T]
}

sealed trait BSONValue { self =>
  /**
   * The code indicating the BSON type for this value
   */
  val code: Byte

  /** The number of bytes for the serialized representation */
  private[reactivemongo] def byteSize: Int

  /**
   * Tries to parse this value as a `T` one.
   *
   * {{{
   * import scala.util.Try
   * import reactivemongo.api.bson.BSONValue
   *
   * def foo(v: BSONValue): Try[String] = v.asTry[String]
   * }}}
   */
  final def asTry[T](implicit reader: BSONReader[T]): Try[T] =
    reader.readTry(this) // TODO: as[M[_]] ?

  /**
   * Optionally parses this value as a `T` one.
   *
   * @return `Some` successfully parsed value, or `None` if fails
   *
   * {{{
   * import scala.util.Try
   * import reactivemongo.api.bson.BSONValue
   *
   * def foo(v: BSONValue): Option[String] = v.asOpt[String]
   * }}}
   */
  final def asOpt[T](implicit reader: BSONReader[T]): Option[T] = try {
    reader.readOpt(this)
  } catch {
    case NonFatal(_) => Option.empty[T]
  }

  // --- Optimized/builtin conversions

  @inline private[bson] def asBoolean: Try[Boolean] =
    Failure(TypeDoesNotMatchException(
      "BSONBoolean", self.getClass.getSimpleName))

  @inline private[bson] def asDecimal: Try[BigDecimal] =
    Failure(TypeDoesNotMatchException(
      "BSONDecimal", self.getClass.getSimpleName))

  @inline private[bson] def asDateTime: Try[Instant] =
    Failure(TypeDoesNotMatchException(
      "BSONDateTime", self.getClass.getSimpleName))

  @inline private[bson] def asDouble: Try[Double] =
    Failure(TypeDoesNotMatchException(
      "BSONDouble", self.getClass.getSimpleName))

  @inline private[bson] def asLong: Try[Long] =
    Failure(TypeDoesNotMatchException(
      "BSONLong", self.getClass.getSimpleName))

  @inline private[bson] def asInt: Try[Int] =
    Failure(TypeDoesNotMatchException(
      "BSONInteger", self.getClass.getSimpleName))

  @inline private[bson] def asString: Try[String] =
    Failure(TypeDoesNotMatchException(
      "BSONString", self.getClass.getSimpleName))

}

object BSONValue extends BSONValueLowPriority1 {
  /** Returns a String representation for the value. */
  def pretty(value: BSONValue): String = value match {
    case arr: BSONArray => BSONArray.pretty(arr)

    case bin: BSONBinary => BSONBinary.pretty(bin)

    case BSONBoolean(bool) => bool.toString

    case time: BSONDateTime => BSONDateTime.pretty(time)

    case doc: BSONDocument => BSONDocument.pretty(doc)

    case BSONDouble(d) => d.toString

    case BSONInteger(i) => i.toString

    case l: BSONLong => BSONLong.pretty(l)

    case d: BSONDecimal => BSONDecimal.pretty(d)

    case s: BSONString => BSONString.pretty(s)

    case id: BSONObjectID => BSONObjectID.pretty(id)

    case ts: BSONTimestamp => BSONTimestamp.pretty(ts)

    case BSONNull => BSONNull.pretty
    case BSONUndefined => BSONUndefined.pretty
    case BSONMinKey => BSONMinKey.pretty
    case BSONMaxKey => BSONMaxKey.pretty

    case _ => value.toString
  }

  /**
   * An addition operation for [[BSONValue]],
   * so that it forms an additive semigroup with the BSON value kind.
   */
  object Addition extends ((BSONValue, BSONValue) => BSONArray) {
    def apply(x: BSONValue, y: BSONValue): BSONArray = (x, y) match {
      case (a: BSONArray, b: BSONArray) => a ++ b
      case (a: BSONArray, _) => a ++ y
      case (_, b: BSONArray) => x +: b
      case _ => new BSONArray(IndexedSeq(x, y))
    }
  }

  implicit def identityValueProducer[B <: BSONValue](value: B): Producer[BSONValue] = new SomeValueProducer(value)

  // ---

  protected final class SomeValueProducer(
    private val value: BSONValue) extends Producer[BSONValue] {
    private[bson] def generate() = Seq(value)
  }
}

// Conversions (for BSONArray factories)
private[bson] sealed trait BSONValueLowPriority1
  extends BSONValueLowPriority2 { _: BSONValue.type =>

  implicit def optionProducer[T](value: Option[T])(implicit writer: BSONWriter[T]): Producer[BSONValue] = value.flatMap(writer.writeOpt) match {
    case Some(bson) => new SomeValueProducer(bson)
    case _ => NoneValueProducer
  }

  implicit val noneProducer: None.type => Producer[BSONValue] =
    _ => NoneValueProducer

  // ---

  protected object NoneValueProducer extends Producer[BSONValue] {
    private val underlying = Seq.empty[BSONValue]
    @inline private[bson] def generate() = underlying
  }
}

private[bson] sealed trait BSONValueLowPriority2 {
  _: BSONValue.type with BSONValueLowPriority1 =>

  implicit def valueProducer[T](value: T)(implicit writer: BSONWriter[T]): Producer[BSONValue] = writer.writeOpt(value) match {
    case Some(v) => new SomeValueProducer(v)
    case _ => NoneValueProducer
  }
}

/** A BSON Double. */
final class BSONDouble private[bson] (val value: Double) extends BSONValue {
  val code = 0x01: Byte

  override private[reactivemongo] val byteSize = 8

  override private[bson] lazy val asDouble: Try[Double] = Success(value)

  override private[bson] lazy val asLong: Try[Long] =
    if (value.isWhole) Try(value.toLong) else super.asLong

  override private[bson] lazy val asInt: Try[Int] = {
    if (value.isWhole && value >= Int.MinValue && value <= Int.MaxValue) {
      Try(value.toInt)
    } else {
      super.asInt
    }
  }

  override private[bson] lazy val asDecimal: Try[BigDecimal] =
    Try(BigDecimal exact value)

  override def hashCode: Int = value.hashCode

  override def equals(that: Any): Boolean = that match {
    case other: BSONDouble => value.compare(other.value) == 0
    case _ => false
  }

  override def toString: String = s"BSONDouble($value)"
}

object BSONDouble {
  /** Extracts the double value if `that`'s a [[BSONDouble]]. */
  def unapply(that: Any): Option[Double] = that match {
    case bson: BSONDouble => Some(bson.value)
    case _ => None
  }

  /** Returns a [[BSONDouble]] */
  @inline def apply(value: Double): BSONDouble = new BSONDouble(value)
}

final class BSONString private[bson] (val value: String) extends BSONValue {
  val code = 0x02: Byte

  override private[reactivemongo] lazy val byteSize = 5 + value.getBytes.size

  override private[reactivemongo] lazy val asString: Try[String] =
    Success(value)

  override def hashCode: Int = value.hashCode

  override def equals(that: Any): Boolean = that match {
    case other: BSONString => value == other.value
    case _ => false
  }

  override def toString: String = s"BSONString($value)"
}

object BSONString {
  /** Extracts the string value if `that`'s a [[BSONString]]. */
  def unapply(that: Any): Option[String] = that match {
    case bson: BSONString => Some(bson.value)
    case _ => None
  }

  /** Returns a [[BSONString]] */
  @inline def apply(value: String): BSONString = new BSONString(value)

  /** Returns the plain string representation for the given [[BSONString]]. */
  @inline def pretty(str: BSONString): String =
    s"""'${str.value.replaceAll("\'", "\\\\'")}'"""
}

/**
 * A `BSONArray` structure (BSON type `0x04`).
 *
 * A `BSONArray` is a indexed sequence of [[BSONValue]].
 *
 * @define indexParam the index to be found in the array
 */
final class BSONArray private[bson] (
  val values: IndexedSeq[BSONValue]) extends BSONValue {

  val code = 0x04: Byte

  /**
   * Returns the [[BSONValue]] at the given `index`.
   *
   * If there is no such `index`, or if the matching value
   * cannot be deserialized returns `None`.
   *
   * @param index $indexParam
   */
  def get(index: Int): Option[BSONValue] = values.lift(index)

  /** The first/mandatory value, if any */
  def headOption: Option[BSONValue] = values.headOption

  /** Returns a BSON array with the given value prepended. */
  def +:(value: BSONValue): BSONArray = new BSONArray(value +: values)

  /** Returns a BSON array with the values of the given one appended. */
  def ++(arr: BSONArray): BSONArray = new BSONArray(values ++ arr.values)

  /** Returns a BSON array with the given values appended. */
  def ++(values: BSONValue*): BSONArray = {
    val vs = IndexedSeq.newBuilder[BSONValue]

    values.foreach { vs += _ }

    new BSONArray(this.values ++ vs.result())
  }

  /** The number of values */
  @inline def size = values.size

  /** Indicates whether this array is empty */
  @inline def isEmpty: Boolean = values.isEmpty

  /**
   * Returns the [[BSONValue]] at the given `index`,
   * and converts it with the given implicit [[BSONReader]].
   *
   * If there is no matching value, or the value could not be deserialized,
   * or converted, returns a `None`.
   *
   * @param index $indexParam
   */
  def getAsOpt[T](index: Int)(implicit reader: BSONReader[T]): Option[T] =
    get(index).flatMap {
      case BSONNull => Option.empty[T]
      case value => reader.readOpt(value)
    }

  /**
   * Gets the [[BSONValue]] at the given `index`,
   * and converts it with the given implicit [[BSONReader]].
   *
   * If there is no matching value, or the value could not be deserialized,
   * or converted, returns a `Failure`.
   *
   * The `Failure` holds a [[exceptions.BSONValueNotFoundException]]
   * if the index could not be found.
   *
   * @param index $indexParam
   */
  def getAsTry[T](index: Int)(implicit reader: BSONReader[T]): Try[T] =
    get(index) match {
      case None | Some(BSONNull) =>
        Failure(BSONValueNotFoundException(index, this))

      case Some(v) => reader.readTry(v)
    }

  /**
   * Gets the [[BSONValue]] at the given `index`,
   * and converts it with the given implicit [[BSONReader]].
   *
   * If there is no matching value, `Success(None)` is returned.
   * If there is a value, it must be valid or a `Failure` is returned.
   *
   * @param index $indexParam
   */
  def getAsUnflattendTry[T](index: Int)(
    implicit
    reader: BSONReader[T]): Try[Option[T]] = get(index) match {
    case None | Some(BSONNull) =>
      Success(Option.empty[T])

    case Some(v) => reader.readTry(v).map(Some(_))
  }

  private[bson] def copy(values: IndexedSeq[BSONValue] = this.values): BSONArray = new BSONArray(values)

  override def equals(that: Any): Boolean = that match {
    case other: BSONArray => {
      if (values == other.values) true
      else values.sortBy(_.hashCode) == other.values.sortBy(_.hashCode)
    }

    case _ => false
  }

  override def hashCode: Int = values.hashCode

  override def toString: String = s"BSONArray(<${if (isEmpty) "empty" else "non-empty"}>)"

  private[reactivemongo] lazy val byteSize: Int =
    values.zipWithIndex.foldLeft(5) {
      case (sz, (v, i)) =>
        sz + 2 + i.toString.getBytes.size + v.byteSize
    }
}

object BSONArray {
  /** Extracts the values sequence if `that`'s a [[BSONArray]]. */
  @inline def unapply(that: Any): Option[IndexedSeq[BSONValue]] = that match {
    case array: BSONArray => Some(array.values)
    case _ => None
  }

  /** Creates a new [[BSONArray]] containing all the given `values`. */
  def apply(values: Producer[BSONValue]*): BSONArray = {
    val vs = IndexedSeq.newBuilder[BSONValue]

    values.foreach {
      vs ++= _.generate()
    }

    new BSONArray(vs.result())
  }

  /**
   * Creates a new [[BSONArray]] containing all the `values`
   * in the given `Iterable`.
   */
  def apply(values: Iterable[BSONValue]): BSONArray = {
    val vs = IndexedSeq.newBuilder[BSONValue]

    values.foreach { vs += _ }

    new BSONArray(vs.result())
  }

  /** Returns a String representing the given [[BSONArray]]. */
  def pretty(array: BSONArray): String =
    s"[\n${BSONIterator.pretty(0, array.values)}\n]"

  /** An empty BSONArray. */
  val empty: BSONArray = new BSONArray(IndexedSeq.empty[BSONValue])
}

/**
 * A BSON binary value.
 *
 * @param value The binary content.
 * @param subtype The type of the binary content.
 */
final class BSONBinary private[bson] (
  private[bson] val value: ReadableBuffer,
  val subtype: Subtype) extends BSONValue {

  val code = 0x05: Byte

  /** Returns the whole binary content as array. */
  def byteArray: Array[Byte] = value.duplicate().readArray(value.size)

  override private[reactivemongo] lazy val byteSize = {
    5 /* header = 4 (value.readable: Int) + 1 (subtype.value.toByte) */ +
      value.readable
  }

  override lazy val hashCode: Int = {
    import scala.util.hashing.MurmurHash3

    val nh = MurmurHash3.mix(MurmurHash3.productSeed, subtype.##)

    MurmurHash3.mixLast(nh, value.hashCode)
  }

  override def equals(that: Any): Boolean = that match {
    case other: BSONBinary =>
      (subtype == other.subtype &&
        (value == other.value ||
          // compare content ignoring indexes
          value.duplicate().equals(other.value.duplicate())))

    case _ => false
  }

  override lazy val toString: String =
    s"BSONBinary(${subtype}, size = ${value.readable})"
}

object BSONBinary {
  /** Extracts the [[Subtype]] if `that`'s a [[BSONBinary]]. */
  def unapply(that: Any): Option[Subtype] = that match {
    case other: BSONBinary => Some(other.subtype)
    case _ => None
  }

  /** Creates a [[BSONBinary]] with given `value` and [[Subtype]]. */
  def apply(value: Array[Byte], subtype: Subtype): BSONBinary =
    new BSONBinary(ReadableBuffer(value), subtype)

  /**
   * Creates a [[BSONBinary]] from the given UUID
   * (with [[Subtype.UuidSubtype]]).
   */
  def apply(id: java.util.UUID): BSONBinary = {
    val bytes = Array.ofDim[Byte](16)
    val buf = ByteBuffer.wrap(bytes)

    buf putLong id.getMostSignificantBits
    buf putLong id.getLeastSignificantBits

    BSONBinary(bytes, Subtype.UuidSubtype)
  }

  /** Returns a string representation of the given [[BSONBinary]]. */
  def pretty(bin: BSONBinary): String = {
    val b64 = java.util.Base64.getEncoder.encodeToString(bin.byteArray)

    s"BinData(${bin.subtype.value}, '${b64}')"
  }
}

/** BSON Undefined value */
sealed trait BSONUndefined extends BSONValue {
  val code = 0x06: Byte
  override private[reactivemongo] val byteSize = 0

  override def toString: String = "BSONUndefined"
}

object BSONUndefined extends BSONUndefined {
  val pretty = "undefined"
}

/**
 * BSON ObjectId value.
 *
 * +------------------------+------------------------+------------------------+------------------------+
 * + timestamp (in seconds) +   machine identifier   +    thread identifier   +        increment       +
 * +        (4 bytes)       +        (3 bytes)       +        (2 bytes)       +        (3 bytes)       +
 * +------------------------+------------------------+------------------------+------------------------+
 */
sealed abstract class BSONObjectID extends BSONValue {
  import java.util.Arrays

  val code = 0x07: Byte

  protected val raw: Array[Byte]

  /** The time of this BSONObjectId, in seconds */
  def timeSecond: Int

  /** The time of this BSONObjectId, in milliseconds */
  @inline final def time: Long = timeSecond * 1000L

  @inline override private[reactivemongo] val byteSize = 12

  /** Returns the whole binary content as array. */
  final def byteArray: Array[Byte] = Arrays.copyOf(raw, byteSize)

  /** ObjectId hexadecimal String representation */
  lazy val stringify = Digest.hex2Str(raw)

  override def toString = s"BSONObjectID($stringify)"

  override def equals(that: Any): Boolean = that match {
    case BSONObjectID(other) =>
      Arrays.equals(raw, other)

    case _ => false
  }

  override lazy val hashCode: Int = Arrays.hashCode(raw)
}

object BSONObjectID {
  private val maxCounterValue = 16777216
  private val increment = new java.util.concurrent.atomic.AtomicInteger(
    scala.util.Random nextInt maxCounterValue)

  private def counter: Int = (increment.getAndIncrement + maxCounterValue) % maxCounterValue

  /**
   * The following implementation of machineId works around
   * openjdk limitations in versions 6 and 7.
   *
   * Openjdk fails to parse /proc/net/if_inet6 correctly to determine macaddress
   * resulting in SocketException thrown.
   *
   * Please see:
   *
   * - https://github.com/openjdk-mirror/jdk7u-jdk/blob/feeaec0647609a1e6266f902de426f1201f77c55/src/solaris/native/java/net/NetworkInterface.c#L1130
   * - http://lxr.free-electrons.com/source/net/ipv6/addrconf.c?v=3.11#L3442
   * - http://lxr.free-electrons.com/source/include/linux/netdevice.h?v=3.11#L1130
   * - http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=7078386
   *
   * and fix in openjdk8:
   * - http://hg.openjdk.java.net/jdk8/tl/jdk/rev/b1814b3ea6d3
   */
  private val machineId: Array[Byte] = {
    import java.net.{ InetAddress, NetworkInterface, NetPermission }

    def p(n: String) = System.getProperty(n)

    val validPlatform = try {
      val correctVersion = p("java.version").substring(0, 3).toFloat >= 1.8
      val noIpv6 = p("java.net.preferIPv4Stack").toBoolean == true
      val isLinux = p("os.name") == "Linux"

      !isLinux || correctVersion || noIpv6
    } catch {
      case NonFatal(_) => false
    }

    // Check java policies
    val permitted = try {
      System.getSecurityManager().
        checkPermission(new NetPermission("getNetworkInformation"))

      true
    } catch {
      case NonFatal(_) => false
    }

    if (validPlatform && permitted) {
      @annotation.tailrec
      def resolveAddr(ifaces: java.util.Enumeration[NetworkInterface]): Array[Byte] = {
        if (ifaces.hasMoreElements) {
          val hwAddr: Array[Byte] = try {
            ifaces.nextElement().getHardwareAddress()
          } catch {
            case NonFatal(_) => null
          }

          if (hwAddr != null && hwAddr.length == 6) {
            hwAddr
          } else {
            resolveAddr(ifaces)
          }
        } else {
          // Fallback:
          InetAddress.getLocalHost.getHostName.getBytes("UTF-8")
        }
      }

      val ha = resolveAddr(NetworkInterface.getNetworkInterfaces())

      Digest.md5(ha).take(3)
    } else {
      val threadId = Thread.currentThread.getId.toInt

      Array[Byte](
        (threadId & 0xFF).toByte,
        (threadId >> 8 & 0xFF).toByte,
        (threadId >> 16 & 0xFF).toByte)

    }
  }

  /** Extracts the bytes if `that`'s a [[BSONObjectID]]. */
  def unapply(that: Any): Option[Array[Byte]] = that match {
    case id: BSONObjectID => Some(id.byteArray)
    case _ => None
  }

  /** Tries to make a BSON ObjectId from a hexadecimal string representation. */
  def parse(id: String): Try[BSONObjectID] = {
    if (id.length != 24) Failure[BSONObjectID](
      new IllegalArgumentException(s"Wrong ObjectId (length != 24): '$id'"))
    else parse(Digest str2Hex id)
  }

  /** Tries to make a BSON ObjectId from a binary representation. */
  def parse(bytes: Array[Byte]): Try[BSONObjectID] = {
    if (bytes.length != 12) {
      Failure[BSONObjectID](new IllegalArgumentException(
        s"Wrong ObjectId: length(${bytes.length}) != 12"))

    } else Try {
      bytes(0) << 24 | (bytes(1) << 0xFF) << 16 | (
        bytes(2) & 0xFF) << 8 | (bytes(3) & 0xFF)

    }.map { timeSec =>
      new BSONObjectID {
        val timeSecond = timeSec
        val raw = bytes
      }
    }
  }

  /**
   * Generates a new BSON ObjectID using the current time as seed.
   *
   * @see [[fromTime]]
   */
  def generate(): BSONObjectID = fromTime(System.currentTimeMillis, false)

  /**
   * Generates a new BSON ObjectID from the given timestamp in milliseconds.
   *
   * The included timestamp is the number of seconds since epoch,
   * so a [[BSONObjectID]] time part has only a precision up to the second.
   *
   * To get a reasonably unique ID, you _must_ set `onlyTimestamp` to false.
   *
   * Crafting an ID from a timestamp with `fillOnlyTimestamp` set to true
   * is helpful for range queries; e.g if you want of find documents an `_id`
   * field which timestamp part is greater than or lesser
   * than the one of another id.
   *
   * If you do not intend to use the produced [[BSONObjectID]]
   * for range queries, then you'd rather use the `generate` method instead.
   *
   * @param fillOnlyTimestamp if true, the returned BSONObjectID
   * will only have the timestamp bytes set; the other will be set to zero.
   */
  def fromTime(timeMillis: Long, fillOnlyTimestamp: Boolean = false): BSONObjectID = {
    // n of seconds since epoch. Big endian
    val timestamp = (timeMillis / 1000).toInt
    val id = Array.ofDim[Byte](12)

    id(0) = (timestamp >>> 24).toByte
    id(1) = (timestamp >> 16 & 0xFF).toByte
    id(2) = (timestamp >> 8 & 0xFF).toByte
    id(3) = (timestamp & 0xFF).toByte

    if (!fillOnlyTimestamp) {
      // machine id, 3 first bytes of md5(macadress or hostname)
      id(4) = machineId(0)
      id(5) = machineId(1)
      id(6) = machineId(2)

      // 2 bytes of the pid or thread id. Thread id in our case. Low endian
      val threadId = Thread.currentThread.getId.toInt
      id(7) = (threadId & 0xFF).toByte
      id(8) = (threadId >> 8 & 0xFF).toByte

      // 3 bytes of counter sequence, which start is randomized. Big endian
      val c = counter
      id(9) = (c >> 16 & 0xFF).toByte
      id(10) = (c >> 8 & 0xFF).toByte
      id(11) = (c & 0xFF).toByte
    }

    new BSONObjectID {
      val raw = id
      val timeSecond = timestamp
    }
  }

  /** Returns the string representation for the given [[BSONObjectID]]. */
  @inline def pretty(oid: BSONObjectID): String =
    s"ObjectId('${oid.stringify}')"
}

/** BSON boolean value */
final class BSONBoolean private[bson] (val value: Boolean) extends BSONValue {
  val code = 0x08: Byte
  override private[reactivemongo] val byteSize = 1

  override lazy val asBoolean: Try[Boolean] = Success(value)

  override def hashCode: Int = value.hashCode

  override def equals(that: Any): Boolean = that match {
    case other: BSONBoolean => value == other.value
    case _ => false
  }

  override def toString: String = s"BSONBoolean($value)"
}

object BSONBoolean {
  /** Extracts the boolean value if `that`'s a [[BSONBoolean]]. */
  def unapply(that: Any): Option[Boolean] = that match {
    case bson: BSONBoolean => Some(bson.value)
    case _ => None
  }

  /** Returns a [[BSONBoolean]] */
  @inline def apply(value: Boolean): BSONBoolean = new BSONBoolean(value)
}

/** BSON date time value */
final class BSONDateTime private[bson] (val value: Long) extends BSONValue {
  val code = 0x09: Byte

  override private[bson] lazy val asDecimal: Try[BigDecimal] =
    Success(BigDecimal(value))

  override private[bson] lazy val asLong: Try[Long] = Success(value)

  override private[bson] lazy val asDateTime: Try[Instant] =
    Success(Instant ofEpochMilli value)

  override private[reactivemongo] val byteSize = 8

  override def hashCode: Int = value.hashCode

  override def equals(that: Any): Boolean = that match {
    case other: BSONDateTime => value == other.value
    case _ => false
  }

  override def toString: String = s"BSONDateTime($value)"
}

object BSONDateTime {
  /** Extracts the dateTime value if `that`'s a [[BSONDateTime]]. */
  def unapply(that: Any): Option[Long] = that match {
    case bson: BSONDateTime => Some(bson.value)
    case _ => None
  }

  /**
   * Returns a [[BSONDateTime]].
   *
   * @param value the date/time value
   */
  @inline def apply(value: Long): BSONDateTime = new BSONDateTime(value)

  /** Returns a string representation for the given [[BSONDateTime]]. */
  @inline def pretty(time: BSONDateTime): String =
    s"ISODate('${java.time.Instant ofEpochMilli time.value}')"
}

/** BSON null value */
sealed trait BSONNull extends BSONValue {
  val code = 0x0A: Byte
  override private[reactivemongo] val byteSize = 0

  override def toString: String = "BSONNull"
}

object BSONNull extends BSONNull {
  val pretty = "null"
}

/**
 * BSON Regex value.
 *
 * @param value the regex value (expression)
 * @param flags the regex flags
 */
final class BSONRegex private[bson] (
  val value: String, val flags: String)
  extends BSONValue {
  val code = 0x0B: Byte

  override private[reactivemongo] lazy val byteSize =
    2 + value.getBytes.size + flags.getBytes.size

  override lazy val hashCode: Int = {
    import scala.util.hashing.MurmurHash3

    val nh = MurmurHash3.mix(MurmurHash3.productSeed, value.##)

    MurmurHash3.mixLast(nh, flags.hashCode)
  }

  override def equals(that: Any): Boolean = that match {
    case other: BSONRegex =>
      (value == other.value && flags == other.flags)

    case _ => false
  }

  override def toString: String = s"BSONRegex($value, $flags)"
}

object BSONRegex {
  /** Extracts the regex value and flags if `that`'s a [[BSONRegex]]. */
  def unapply(that: Any): Option[(String, String)] = that match {
    case regex: BSONRegex => Some(regex.value -> regex.flags)
    case _ => None
  }

  /** Returns a [[BSONRegex]] */
  @inline def apply(value: String, flags: String): BSONRegex =
    new BSONRegex(value, flags)
}

/**
 * BSON JavaScript value.
 *
 * @param value The JavaScript source code.
 */
final class BSONJavaScript private[bson] (val value: String) extends BSONValue {
  val code = 0x0D: Byte

  override private[reactivemongo] lazy val byteSize = 5 + value.getBytes.size

  override private[reactivemongo] lazy val asString: Try[String] =
    Success(value)

  override def hashCode: Int = value.hashCode

  override def equals(that: Any): Boolean = that match {
    case other: BSONJavaScript => value == other.value
    case _ => false
  }

  override def toString: String = s"BSONJavaScript($value)"
}

object BSONJavaScript {
  /** Extracts the javaScript value if `that`'s a [[BSONJavaScript]]. */
  def unapply(that: Any): Option[String] = that match {
    case bson: BSONJavaScript => Some(bson.value)
    case _ => None
  }

  /** Returns a [[BSONJavaScript]] */
  @inline def apply(value: String): BSONJavaScript = new BSONJavaScript(value)
}

/**
 * BSON Symbol value.
 *
 * @param value the symbol value (name)
 */
final class BSONSymbol private[bson] (val value: String) extends BSONValue {
  val code = 0x0E: Byte

  override private[reactivemongo] lazy val byteSize = 5 + value.getBytes.size

  override private[reactivemongo] lazy val asString: Try[String] =
    Success(value)

  override def hashCode: Int = value.hashCode

  override def equals(that: Any): Boolean = that match {
    case other: BSONSymbol => value == other.value
    case _ => false
  }

  override def toString: String = s"BSONSymbol($value)"
}

object BSONSymbol {
  /** Extracts the symbol value if `that`'s a [[BSONSymbol]]. */
  def unapply(that: Any): Option[String] = that match {
    case sym: BSONSymbol => Some(sym.value)
    case _ => None
  }

  /** Returns a [[BSONSymbol]] */
  @inline def apply(value: String): BSONSymbol = new BSONSymbol(value)
}

/**
 * BSON JavaScript value with scope (WS).
 *
 * @param value The JavaScript source code.
 */
final class BSONJavaScriptWS private[bson] (
  val value: String,
  val scope: BSONDocument) extends BSONValue {
  val code = 0x0F: Byte

  override private[reactivemongo] lazy val byteSize =
    5 + value.getBytes.size + scope.byteSize

  override private[reactivemongo] lazy val asString: Try[String] =
    Success(value)

  override def hashCode: Int = {
    import scala.util.hashing.MurmurHash3

    val nh = MurmurHash3.mix(MurmurHash3.productSeed, value.hashCode)

    MurmurHash3.mixLast(nh, scope.hashCode)
  }

  override def equals(that: Any): Boolean = that match {
    case other: BSONJavaScriptWS =>
      (value == other.value) && (scope == other.scope)

    case _ => false
  }

  override def toString: String = s"BSONJavaScriptWS($value)"
}

object BSONJavaScriptWS {
  /** Extracts the javaScriptWS value if `that`'s a [[BSONJavaScriptWS]]. */
  def unapply(that: Any): Option[(String, BSONDocument)] = that match {
    case js: BSONJavaScriptWS => Some(js.value -> js.scope)
    case _ => None
  }

  /** Returns a [[BSONJavaScriptWS]] with given `scope` */
  @inline def apply(value: String, scope: BSONDocument): BSONJavaScriptWS =
    new BSONJavaScriptWS(value, scope)
}

/** BSON Integer value */
final class BSONInteger private[bson] (val value: Int) extends BSONValue {
  val code = 0x10: Byte
  override private[reactivemongo] val byteSize = 4

  override private[bson] lazy val asDouble: Try[Double] = Success(value.toDouble)

  override private[bson] lazy val asInt: Try[Int] = Success(value)

  override private[bson] lazy val asLong: Try[Long] = Success(value.toLong)

  override private[bson] lazy val asDecimal: Try[BigDecimal] = Success(BigDecimal(value))

  override def hashCode: Int = value.hashCode

  override def equals(that: Any): Boolean = that match {
    case other: BSONInteger => value == other.value
    case _ => false
  }

  override def toString: String = s"BSONInteger($value)"
}

object BSONInteger {
  /** Extracts the integer value if `that`'s a [[BSONInteger]]. */
  def unapply(that: Any): Option[Int] = that match {
    case i: BSONInteger => Some(i.value)
    case _ => None
  }

  /** Returns a [[BSONInteger]] */
  @inline def apply(value: Int): BSONInteger = new BSONInteger(value)
}

/** BSON Timestamp value */
final class BSONTimestamp private[bson] (val value: Long) extends BSONValue {
  val code = 0x11: Byte

  /** Seconds since the Unix epoch */
  val time = value >>> 32

  /** Ordinal (with the second) */
  val ordinal = value.toInt

  override private[bson] lazy val asLong: Try[Long] = Success(value)

  override private[bson] lazy val asDateTime: Try[Instant] =
    Success(Instant ofEpochMilli value)

  override private[reactivemongo] val byteSize = 8

  override def hashCode: Int = value.hashCode

  override def equals(that: Any): Boolean = that match {
    case other: BSONTimestamp => value == other.value
    case _ => false
  }

  override def toString: String = s"BSONTimestamp($value)"
}

/** Timestamp companion */
object BSONTimestamp {
  /** Extracts the timestamp value if `that`'s a [[BSONTimestamp]]. */
  def unapply(that: Any): Option[Long] = that match {
    case ts: BSONTimestamp => Some(ts.value)
    case _ => None
  }

  /** Returns a [[BSONTimestamp]] */
  @inline def apply(value: Long): BSONTimestamp = new BSONTimestamp(value)

  /**
   * Returns the timestamp corresponding to the given `time` and `ordinal`.
   *
   * @param time the 32bits time value (seconds since the Unix epoch)
   * @param ordinal an incrementing ordinal for operations within a same second
   */
  def apply(time: Long, ordinal: Int): BSONTimestamp =
    BSONTimestamp((time << 32) ^ ordinal)

  /** Returns the string representation for the given [[BSONTimestamp]]. */
  @inline def pretty(ts: BSONTimestamp): String =
    s"Timestamp(${ts.time}, ${ts.ordinal})"
}

/** BSON Long value */
final class BSONLong private[bson] (val value: Long) extends BSONValue {
  val code = 0x12: Byte

  override private[bson] lazy val asDouble: Try[Double] = {
    if (value >= Double.MinValue && value <= Double.MaxValue) {
      Try(value.toDouble)
    } else super.asDouble
  }

  override private[bson] lazy val asInt: Try[Int] = {
    if (value >= Int.MinValue && value <= Int.MaxValue) Try(value.toInt)
    else super.asInt
  }

  override private[bson] lazy val asLong: Try[Long] = Success(value)

  override private[bson] lazy val asDecimal: Try[BigDecimal] =
    Success(BigDecimal(value))

  override private[reactivemongo] val byteSize = 8

  override def hashCode: Int = value.hashCode

  override def equals(that: Any): Boolean = that match {
    case other: BSONLong => value == other.value
    case _ => false
  }

  override def toString: String = s"BSONLong($value)"
}

object BSONLong {
  /** Extracts the value if `that`'s a [[BSONLong]]. */
  def unapply(that: Any): Option[Long] = that match {
    case bson: BSONLong =>
      Some(bson.value)

    case _ => None
  }

  /** Returns a [[BSONLong]] */
  @inline def apply(value: Long): BSONLong = new BSONLong(value)

  /** Returns the string representation for the given [[BSONLong]]. */
  @inline def pretty(long: BSONLong): String = s"NumberLong(${long.value})"
}

/**
 * Value wrapper for a [[https://github.com/mongodb/specifications/blob/master/source/bson-decimal128/decimal128.rst BSON 128-bit decimal]].
 *
 * @param high the high-order 64 bits
 * @param low the low-order 64 bits
 */
final class BSONDecimal private[bson] (
  val high: Long,
  val low: Long) extends BSONValue {

  val code = 0x13: Byte

  override private[bson] lazy val asDecimal: Try[BigDecimal] =
    BSONDecimal.toBigDecimal(this)

  override private[bson] lazy val asDouble: Try[Double] =
    asDecimal.filter(_.isDecimalDouble).map(_.toDouble)

  override private[bson] lazy val asInt: Try[Int] =
    asDecimal.filter(_.isValidInt).map(_.toInt)

  override private[bson] lazy val asLong: Try[Long] =
    asDecimal.filter(_.isValidLong).map(_.toLong)

  /** Returns true if is negative. */
  lazy val isNegative: Boolean =
    (high & Decimal128.SignBitMask) == Decimal128.SignBitMask

  /** Returns true if is infinite. */
  lazy val isInfinite: Boolean =
    (high & Decimal128.InfMask) == Decimal128.InfMask

  /** Returns true if is Not-A-Number (NaN). */
  lazy val isNaN: Boolean =
    (high & Decimal128.NaNMask) == Decimal128.NaNMask

  override private[reactivemongo] lazy val byteSize = 16

  // ---

  /**
   * Returns the [[https://github.com/mongodb/specifications/blob/master/source/bson-decimal128/decimal128.rst#to-string-representation string representation]].
   */
  override def toString: String = Decimal128.toString(this)

  override def equals(that: Any): Boolean = that match {
    case BSONDecimal(h, l) => (high == h) && (low == l)
    case _ => false
  }

  override lazy val hashCode: Int = {
    val result = (low ^ (low >>> 32)).toInt

    31 * result + (high ^ (high >>> 32)).toInt
  }
}

object BSONDecimal {
  import java.math.MathContext

  /**
   * Factory alias.
   *
   * @param high the high-order 64 bits
   * @param low the low-order 64 bits
   */
  @inline def apply(high: Long, low: Long): BSONDecimal =
    new BSONDecimal(high, low)

  /**
   * Returns a BSON decimal (Decimal128) corresponding to the given BigDecimal.
   *
   * @param value the BigDecimal representation
   */
  @inline def fromBigDecimal(value: JBigDec): Try[BSONDecimal] =
    Decimal128.fromBigDecimal(value, value.signum == -1)

  /**
   * Returns a BSON decimal (Decimal128) corresponding to the given BigDecimal.
   *
   * @param value the BigDecimal representation
   */
  @inline def fromBigDecimal(value: BigDecimal): Try[BSONDecimal] =
    Decimal128.fromBigDecimal(value.bigDecimal, value.signum == -1)

  /**
   * Returns a Decimal128 value represented the given high 64bits value,
   * using a default for the low one.
   *
   * @param high the high-order 64 bits
   */
  @inline def fromLong(high: Long): Try[BSONDecimal] =
    fromBigDecimal(new JBigDec(high, MathContext.DECIMAL128))

  /**
   * Returns the Decimal128 corresponding to the given string representation.
   *
   * @param repr the Decimal128 value represented as string
   * @see [[https://github.com/mongodb/specifications/blob/master/source/bson-decimal128/decimal128.rst#from-string-representation Decimal128 string representation]]
   */
  def parse(repr: String): Try[BSONDecimal] = Decimal128.parse(repr)

  /** Returns the corresponding BigDecimal. */
  def toBigDecimal(decimal: BSONDecimal): Try[BigDecimal] =
    Decimal128.toBigDecimal(decimal).map(BigDecimal(_))

  /** Extracts the (high, low) representation if `that`'s [[BSONDecimal]]. */
  def unapply(that: Any): Option[(Long, Long)] = that match {
    case decimal: BSONDecimal => Some(decimal.high -> decimal.low)
    case _ => None
  }

  /** Returns the string representation for the given [[BSONDecimal]]. */
  def pretty(dec: BSONDecimal): String = dec.asDecimal match {
    case Success(v) => s"NumberDecimal($v)"
    case _ => "NumberDecimal(NaN)"
  }

  // ---

  /**
   * Decimal128 representation of the positive infinity
   */
  val PositiveInf: BSONDecimal = BSONDecimal(Decimal128.InfMask, 0)

  /**
   * Decimal128 representation of the negative infinity
   */
  val NegativeInf: BSONDecimal =
    BSONDecimal(Decimal128.InfMask | Decimal128.SignBitMask, 0)

  /**
   * Decimal128 representation of a negative Not-a-Number (-NaN) value
   */
  val NegativeNaN: BSONDecimal =
    BSONDecimal(Decimal128.NaNMask | Decimal128.SignBitMask, 0)

  /**
   * Decimal128 representation of a Not-a-Number (NaN) value
   */
  val NaN: BSONDecimal = BSONDecimal(Decimal128.NaNMask, 0)

  /**
   * Decimal128 representation of a postive zero value
   */
  val PositiveZero: BSONDecimal =
    BSONDecimal(0x3040000000000000L, 0x0000000000000000L)

  /**
   * Decimal128 representation of a negative zero value
   */
  val NegativeZero: BSONDecimal =
    BSONDecimal(0xb040000000000000L, 0x0000000000000000L)
}

/** BSON Min key value */
sealed trait BSONMinKey extends BSONValue {
  val code = 0xFF.toByte
  override private[reactivemongo] val byteSize = 0
  override val toString = "BSONMinKey"
}

object BSONMinKey extends BSONMinKey {
  val pretty = "MinKey"
}

/** BSON Max key value */
sealed trait BSONMaxKey extends BSONValue {
  val code = 0x7F: Byte
  override private[reactivemongo] val byteSize = 0
  override val toString = "BSONMaxKey"
}

object BSONMaxKey extends BSONMaxKey {
  val pretty = "MaxKey"
}

/**
 * A `BSONDocument` structure (BSON type `0x03`).
 *
 * A `BSONDocument` is basically a set of fields `(String, BSONValue)`.
 *
 *
 * '''Note:''' The insertion/initial order of the fields may not
 * be maintained through the operations.
 *
 * @define keyParam the key to be found in the document
 */
sealed abstract class BSONDocument
  extends BSONValue with ElementProducer with BSONDocumentLowPriority { self =>

  final val code = 0x03.toByte

  /** The document fields */
  private[bson] def fields: Map[String, BSONValue]

  /**
   * The document fields as a sequence of [[BSONElement]]s.
   */
  def elements: Seq[BSONElement]

  /**
   * Returns the map representation for this document.
   */
  @inline final def toMap: Map[String, BSONValue] = fields

  /**
   * Checks whether the given key is found in this element set.
   *
   * @param key $keyParam
   * @return true if the key is found
   */
  def contains(key: String): Boolean = fields.contains(key)

  /**
   * Returns the [[BSONValue]] associated with the given `key`.
   * If the key cannot be found, returns `None`.
   *
   * @param key $keyParam
   */
  final def get(key: String): Option[BSONValue] = fields.get(key)

  /** The first/mandatory element, if any */
  def headOption: Option[BSONElement]

  /** Returns the values of the document fields. */
  final def values: Iterable[BSONValue] = fields.values

  /**
   * Returns the [[BSONDocument]] containing all the elements
   * of this one and the elements of the given document.
   */
  final def ++(doc: BSONDocument): BSONDocument = new BSONDocument {
    lazy val fields: Map[String, BSONValue] = self.fields ++ doc.fields

    val elements =
      (toLazy(self.elements) ++ toLazy(doc.elements)).distinct

    @inline def headOption = self.headOption
    val isEmpty = self.isEmpty && doc.isEmpty
  }

  /**
   * Creates a new [[BSONDocument]] containing all the elements
   * of this one and the specified element sequence.
   */
  final def ++(seq: BSONElement*): BSONDocument = new BSONDocument {
    val elements = (toLazy(self.elements) ++ toLazy(seq)).distinct

    lazy val fields = {
      val m = MMap.empty[String, BSONValue]

      m ++= self.fields

      seq.foreach {
        case BSONElement(name, value) => m.put(name, value)
      }

      m.toMap
    }

    @inline def headOption = self.headOption
    val isEmpty = self.isEmpty && seq.isEmpty
  }

  /**
   * Returns a set without the values corresponding to the specified keys.
   */
  final def --(keys: String*): BSONDocument = new BSONDocument {
    val fields = self.fields -- keys
    lazy val elements = self.elements.filterNot { e => keys.contains(e.name) }
    @inline def headOption = elements.headOption
    val isEmpty = fields.isEmpty
    @inline override def contains(key: String): Boolean = false
  }

  /** The number of fields */
  @inline def size: Int = fields.size

  /** Indicates whether this document is empty */
  def isEmpty: Boolean

  /**
   * Returns the [[BSONValue]] associated with the given `key`,
   * and converts it with the given implicit [[BSONReader]].
   *
   * If there is no matching value, or the value could not be deserialized,
   * or converted, returns a `None`.
   *
   * @param key $keyParam
   *
   * @note When implementing a [[http://reactivemongo.org/releases/latest/documentation/bson/typeclasses.html custom reader]], [[getAsTry]] must be preferred.
   */
  final def getAsOpt[T](key: String)(implicit reader: BSONReader[T]): Option[T] = get(key).flatMap {
    case BSONNull => Option.empty[T]
    case v => reader.readOpt(v)
  }

  /**
   * Gets the [[BSONValue]] associated with the given `key`,
   * and converts it with the given implicit [[BSONReader]].
   *
   * If there is no matching value, or the value could not be deserialized,
   * or converted, returns a `Failure`.
   *
   * The `Failure` may hold a [[exceptions.BSONValueNotFoundException]],
   * if the key could not be found.
   *
   * @param key $keyParam
   */
  final def getAsTry[T](key: String)(implicit reader: BSONReader[T]): Try[T] =
    get(key) match {
      case None | Some(BSONNull) =>
        Failure(exceptions.BSONValueNotFoundException(key, this))

      case Some(v) => reader.readTry(v)
    }

  /**
   * Gets the [[BSONValue]] at the given `key`,
   * and converts it with the given implicit [[BSONReader]].
   *
   * If there is no matching value, `Success(None)` is returned.
   * If there is a value, it must be valid or a `Failure` is returned.
   *
   * @param key $keyParam
   */
  final def getAsUnflattenedTry[T](key: String)(
    implicit
    reader: BSONReader[T]): Try[Option[T]] = get(key) match {
    case None | Some(BSONNull) =>
      Success(Option.empty[T])

    case Some(v) => reader.readTry(v).map(Some(_))
  }

  private[bson] final def copy(newFields: Map[String, BSONValue] = self.fields): BSONDocument = new BSONDocument {
    val fields = newFields

    val elements = toLazy(newFields).map {
      case (k, v) => BSONElement(k, v)
    }

    @inline def isEmpty = newFields.isEmpty
    @inline def headOption = elements.headOption
  }

  override def equals(that: Any): Boolean = that match {
    case other: BSONDocument => {
      if (fields == other.fields) true
      else fields.toSeq.sortBy(_._1) == other.fields.toSeq.sortBy(_._1)
    }

    case _ => false
  }

  override def hashCode: Int = fields.toSeq.sortBy(_._1).hashCode

  override def toString: String = "BSONDocument(<" + (if (isEmpty) "empty" else "non-empty") + ">)"

  @inline private[bson] final def generate() = elements

  private[reactivemongo] lazy val byteSize: Int = fields.foldLeft(5) {
    case (sz, (n, v)) => sz + 2 + n.getBytes.size + v.byteSize
  }
}

private[bson] sealed trait BSONDocumentLowPriority { _: BSONDocument =>
  /**
   * Creates a new [[BSONDocument]] containing all the elements
   * of this one and the specified element producers.
   */
  def ++(producers: ElementProducer*): BSONDocument = new BSONDocument {
    val elements = toLazy(producers).flatMap(_.generate()).distinct
    lazy val fields = BSONDocument.toMap(producers)
    val isEmpty = producers.isEmpty
    @inline def headOption = elements.headOption
  }
}

object BSONDocument {
  /**
   * Extracts the elements if `that`'s a [[BSONDocument]].
   */
  def unapply(that: Any): Option[Seq[BSONElement]] = that match {
    case doc: BSONDocument => Some(doc.elements)
    case _ => None
  }

  /** Creates a new [[BSONDocument]] containing all the given `seq`. */
  def apply(seq: ElementProducer*): BSONDocument = new BSONDocument {
    val elements = toLazy(seq).distinct.flatMap(_.generate())
    lazy val fields = BSONDocument.toMap(seq)
    val isEmpty = seq.isEmpty
    @inline def headOption = elements.headOption
  }

  /**
   * Creates a new [[BSONDocument]] containing all the elements
   * in the given sequence.
   */
  def apply(seq: Iterable[(String, BSONValue)]): BSONDocument =
    new BSONDocument {
      val pairs = toLazy(seq).distinct
      val elements = pairs.map {
        case (k, v) => BSONElement(k, v)
      }

      lazy val fields = {
        val m = MMap.empty[String, BSONValue]

        pairs.foreach {
          case (k, v) => m.put(k, v)
        }

        m.toMap
      }

      val isEmpty = seq.isEmpty

      @inline def headOption = elements.headOption
    }

  /** Returns a String representing the given [[BSONDocument]]. */
  def pretty(doc: BSONDocument): String = BSONIterator.pretty(doc.elements)

  private[bson] def toMap(seq: Iterable[ElementProducer]): Map[String, BSONValue] = {
    val m = MMap.empty[String, BSONValue]

    seq.foreach {
      case BSONElement(k, v) => m.put(k, v)
      case _: ElementProducer.Empty.type => ()

      case p => p.generate().foreach {
        case BSONElement(k, v) => m.put(k, v)
      }
    }

    m.toMap
  }

  /** An empty BSONDocument. */
  val empty: BSONDocument = new BSONDocument {
    val fields = HashMap.empty[String, BSONValue]
    val elements = Seq.empty[BSONElement]
    val isEmpty = true
    override val size = 0
    val headOption = Option.empty[BSONElement]
  }
}

sealed abstract class BSONElement extends ElementProducer {
  /** Element (field) name */
  def name: String

  /** Element (BSON) value */
  def value: BSONValue

  final def generate() = Option(this)

  override final lazy val hashCode: Int = {
    import scala.util.hashing.MurmurHash3

    val nh = MurmurHash3.mix(MurmurHash3.productSeed, name.##)

    MurmurHash3.mixLast(nh, value.hashCode)
  }

  override final def equals(that: Any): Boolean = that match {
    case other: BSONElement =>
      (name == other.name && value == other.value)

    case _ => false
  }

  override final def toString = s"BSONElement($name -> $value)"
}

object BSONElement extends BSONElementLowPriority {
  /** Extracts the name and [[BSONValue]] if `that`'s a [[BSONElement]]. */
  def unapply(that: Any): Option[(String, BSONValue)] = that match {
    case elmt: BSONElement => Some(elmt.name -> elmt.value)
    case _ => None
  }

  /** Create a new [[BSONElement]]. */
  def apply[T <: BSONValue](name: String, value: T): BSONElement =
    new DefaultElement(name, value)

  /** Returns an empty [[ElementProducer]] */
  def apply: (String, None.type) => ElementProducer =
    { (_, _) => ElementProducer.Empty }

  private final class DefaultElement[T <: BSONValue](
    val name: String,
    val value: T) extends BSONElement {
    type ValueType = T
  }
}

private[bson] sealed trait BSONElementLowPriority { _: BSONElement.type =>
  /**
   * Returns a [[ElementProducer]] for the given name and value.
   */
  def apply[T](name: String, value: T)(implicit ev: T => Producer[BSONValue]): ElementProducer = {
    val produced = ev(value).generate()

    produced.headOption match {
      case Some(v) if produced.tail.isEmpty =>
        BSONElement(name, v)

      case Some(_) =>
        BSONDocument(produced.map { name -> _ })

      case _ =>
        ElementProducer.Empty
    }
  }

  // Conversions
  /**
   * {{{
   * import reactivemongo.api.bson.BSONDocument
   *
   * BSONDocument(
   *   "foo" -> BSONInteger(1) // tuple as BSONElement("foo", BSONInteger(1))
   * )
   * }}}
   */
  implicit def bsonTuple2BSONElement(pair: (String, BSONValue)): BSONElement =
    BSONElement(pair._1, pair._2)

}

sealed trait ElementProducer extends Producer[BSONElement]

object ElementProducer extends ElementProducerLowPriority {
  /**
   * An empty instance for the [[ElementProducer]] kind.
   * Can be used as `id` with the element [[Composition]] to form
   * an additive monoid.
   */
  private[bson] object Empty extends ElementProducer {
    private val underlying = Seq.empty[BSONElement]
    def generate() = underlying
  }

  /**
   * A composition operation for [[ElementProducer]],
   * so that it forms an additive monoid with the `Empty` instance as `id`.
   */
  object Composition
    extends ((ElementProducer, ElementProducer) => ElementProducer) {

    def apply(x: ElementProducer, y: ElementProducer): ElementProducer =
      (x, y) match {
        case (Empty, Empty) => Empty
        case (Empty, _) => y
        case (_, Empty) => x

        case (a: BSONDocument, b: BSONDocument) => a ++ b

        case (doc: BSONDocument, e: BSONElement) =>
          new BSONDocument {
            val fields = doc.fields.updated(e.name, e.value)
            val elements = (toLazy(doc.elements) :+ e).distinct
            val isEmpty = false // at least `e`
            @inline def headOption = doc.headOption
          }

        case (e: BSONElement, doc: BSONDocument) =>
          new BSONDocument {
            lazy val fields = {
              val m = MMap.empty[String, BSONValue]

              (e +: doc.elements).foreach { el =>
                m.put(el.name, el.value)
              }

              m.toMap
            }

            val elements = (e +: doc.elements).distinct
            val isEmpty = false // at least `e`
            @inline def headOption = Some(e)
          }

        case _ => BSONDocument(x, y)
      }
  }

  // Conversions

  /**
   * {{{
   * import reactivemongo.api.bson.BSONDocument
   *
   * BSONDocument(
   *   "foo" -> None // tuple as empty ElementProducer (no field)
   * )
   * }}}
   */
  implicit val noneValue2ElementProducer: ((String, None.type)) => ElementProducer = _ => ElementProducer.Empty

  /**
   * {{{
   * import reactivemongo.api.bson.BSONDocument
   *
   * BSONDocument(
   *   "foo" -> Some(1), // tuple as BSONElement("foo", BSONInteger(1))
   *   "bar" -> Option.empty[Int] // tuple as empty ElementProducer (no field)
   * )
   * }}}
   */
  implicit def nameOptionValue2ElementProducer[T](element: (String, Option[T]))(implicit writer: BSONWriter[T]): ElementProducer = (for {
    v <- element._2
    b <- writer.writeOpt(v)
  } yield BSONElement(element._1, b)).getOrElse(ElementProducer.Empty)
}

private[bson] sealed trait ElementProducerLowPriority {
  _: ElementProducer.type =>

  /**
   * {{{
   * import reactivemongo.api.bson.BSONDocument
   *
   * BSONDocument(
   *   "foo" -> 1 // tuple as BSONElement("foo", BSONInteger(1))
   * )
   * }}}
   */
  implicit def tuple2ElementProducer[T](pair: (String, T))(implicit writer: BSONWriter[T]): ElementProducer = writer.writeOpt(pair._2) match {
    case Some(v) => BSONElement(pair._1, v)
    case _ => ElementProducer.Empty
  }
}
