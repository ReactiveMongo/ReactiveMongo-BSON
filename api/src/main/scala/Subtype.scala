package reactivemongo.api.bson

/** Binary Subtype */
sealed trait Subtype {

  /** Subtype code */
  private[reactivemongo] val value: Int

  override lazy val toString = getClass.getName
}

object Subtype {
  sealed class GenericBinarySubtype extends Subtype { val value = 0x00 }
  case object GenericBinarySubtype extends GenericBinarySubtype

  sealed class FunctionSubtype extends Subtype { val value = 0x01 }
  case object FunctionSubtype extends FunctionSubtype

  sealed class OldBinarySubtype extends Subtype { val value = 0x02 }
  case object OldBinarySubtype extends OldBinarySubtype

  sealed class OldUuidSubtype extends Subtype { val value = 0x03 }
  case object OldUuidSubtype extends OldUuidSubtype

  sealed class UuidSubtype extends Subtype { val value = 0x04 }
  case object UuidSubtype extends UuidSubtype

  sealed class Md5Subtype extends Subtype { val value = 0x05 }
  case object Md5Subtype extends Md5Subtype

  sealed class EncryptedSubtype extends Subtype { val value = 0x06 }
  case object EncryptedSubtype extends EncryptedSubtype

  sealed class CompressedSubtype extends Subtype { val value = 0x07 }
  case object CompressedSubtype extends CompressedSubtype

  sealed class UserDefinedSubtype extends Subtype {
    val value = 0x80.toByte.toInt
  }

  case object UserDefinedSubtype extends UserDefinedSubtype

  def apply(code: Byte): Subtype = code match {
    case 0    => GenericBinarySubtype
    case 1    => FunctionSubtype
    case 2    => OldBinarySubtype
    case 3    => OldUuidSubtype
    case 4    => UuidSubtype
    case 5    => Md5Subtype
    case 6    => EncryptedSubtype
    case 7    => CompressedSubtype
    case -128 => UserDefinedSubtype
    case _    => throw new NoSuchElementException(s"binary type = $code")
  }
}
