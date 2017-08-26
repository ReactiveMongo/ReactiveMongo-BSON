package reactivemongo.api.bson.exceptions

import scala.util.control.NoStackTrace

case class DocumentKeyNotFoundException(name: String) extends Exception {
  override def getMessage = s"The key '$name' could not be found in this document or array"
}

case class TypeDoesNotMatchException(message: String)
  extends Exception with NoStackTrace {
  override val getMessage = message
}
