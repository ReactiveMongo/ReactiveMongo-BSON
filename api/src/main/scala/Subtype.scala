package reactivemongo.api.bson

/** Binary Subtype */
sealed trait Subtype {
  /** Subtype code */
  val value: Byte

  override lazy val toString = getClass.getName
}

object Subtype {
  object GenericBinarySubtype extends Subtype { val value = 0x00: Byte }
  object FunctionSubtype extends Subtype { val value = 0x01: Byte }
  object OldBinarySubtype extends Subtype { val value = 0x02: Byte }
  object OldUuidSubtype extends Subtype { val value = 0x03: Byte }
  object UuidSubtype extends Subtype { val value = 0x04: Byte }
  object Md5Subtype extends Subtype { val value = 0x05: Byte }
  object UserDefinedSubtype extends Subtype { val value = 0x80.toByte }

  def apply(code: Byte) = code match {
    case 0 => GenericBinarySubtype
    case 1 => FunctionSubtype
    case 2 => OldBinarySubtype
    case 3 => OldUuidSubtype
    case 4 => UuidSubtype
    case 5 => Md5Subtype
    case -128 => UserDefinedSubtype
    case _ => throw new NoSuchElementException(s"binary type = $code")
  }
}
