package reactivemongo.api.bson

import scala.util.{ Failure, Success, Try }

private[bson] object BSONIterator {
  // TODO: Remove
  def pretty(i: Int, it: Iterator[Try[BSONElement]], f: String => String = { name => s""""${name}": """ }): String = {
    val indent = (0 to i).map { i => "  " }.mkString("")

    it.map {
      case Success(BSONElement(name, value)) =>
        pretty(i, indent, name, value, f)

      case Failure(e) => s"${indent}ERROR[${e.getMessage()}]"
    }.mkString(",\n")
  }

  def _pretty(i: Int, it: TraversableOnce[BSONElement], f: String => String = { name => s""""${name}": """ }): String = {
    val indent = (0 to i).map { i => "  " }.mkString("")

    it.map {
      case BSONElement(name, value) =>
        pretty(i, indent, name, value, f)

    }.mkString(",\n")
  }

  /** Makes a pretty String representation of the given iterator of BSON elements. */
  def pretty(it: Iterator[Try[BSONElement]]): String = "{\n" + pretty(0, it) + "\n}" // TODO: Remove

  /** Makes a pretty String representation of the given iterator of BSON elements. */
  def _pretty(it: TraversableOnce[BSONElement]): String =
    "{\n" + _pretty(0, it) + "\n}"

  private def pretty(
    i: Int,
    indent: String,
    name: String,
    value: BSONValue,
    f: String => String): String = {

    val prefix = s"${indent}${f(name)}"

    value match {
      case array: BSONArray => s"${prefix}[\n" + pretty(i + 1, array.elements.map(Success(_)).iterator, _ => "") + s"\n${indent}]"

      case BSONBoolean(b) =>
        s"${prefix}$b"

      case BSONDocument(elements) =>
        s"${prefix}{\n" + _pretty(i + 1, elements.iterator) + s"\n$indent}"

      case BSONDouble(d) =>
        s"""${prefix}$d"""

      case BSONInteger(i) =>
        s"${prefix}$i"

      case BSONLong(l) =>
        s"${prefix}NumberLong($l)"

      case d @ BSONDecimal(_, _) =>
        s"${prefix}NumberDecimal($d)"

      case BSONString(s) =>
        prefix + '"' + s.replaceAll("\"", "\\\"") + '"'

      case oid @ BSONObjectID(_) =>
        s"${prefix}Object(${oid.stringify})"

      case ts @ BSONTimestamp(_) =>
        s"${prefix}Timestamp(${ts.time}, ${ts.ordinal})"

      case BSONNull => s"${prefix}null"
      case BSONUndefined => s"${prefix}undefined"
      case BSONMinKey => s"${prefix}MinKey"
      case BSONMaxKey => s"${prefix}MaxKey"

      case _ =>
        s"${prefix}$value"
    }
  }
}
