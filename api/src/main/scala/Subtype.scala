package reactivemongo.api.bson

/** Binary Subtype */
sealed trait Subtype {
  /** Subtype code */
  private[reactivemongo] val value: Int

  override lazy val toString = getClass.getName
}

object Subtype {
  sealed trait GenericBinarySubtype extends Subtype { val value = 0x00 }
  object GenericBinarySubtype extends GenericBinarySubtype

  sealed trait FunctionSubtype extends Subtype { val value = 0x01 }
  object FunctionSubtype extends FunctionSubtype

  sealed trait OldBinarySubtype extends Subtype { val value = 0x02 }
  object OldBinarySubtype extends OldBinarySubtype

  sealed trait OldUuidSubtype extends Subtype { val value = 0x03 }
  object OldUuidSubtype extends OldUuidSubtype

  sealed trait UuidSubtype extends Subtype { val value = 0x04 }
  object UuidSubtype extends UuidSubtype

  sealed trait Md5Subtype extends Subtype { val value = 0x05 }
  object Md5Subtype extends Md5Subtype

  sealed trait UserDefinedSubtype extends Subtype {
    val value = 0x80.toByte.toInt
  }

  object UserDefinedSubtype extends UserDefinedSubtype

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
