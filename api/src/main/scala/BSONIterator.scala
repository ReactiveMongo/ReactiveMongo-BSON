package reactivemongo.api.bson

private[bson] object BSONIterator {

  def pretty(
      i: Int,
      it: Iterable[BSONElement],
      f: String => String = { name => s"'${name}': " }
    ): String = {
    val indent = (0 to i).map { _ => "  " }.mkString("")

    it.collect {
      case BSONElement(name, value) =>
        pretty(i, indent, name, value, f)

    }.mkString(",\n")
  }

  def pretty(i: Int, it: Iterable[BSONValue]): String = {
    val indent = (0 to i).map { _ => "  " }.mkString("")

    it.map {
      pretty(i, indent, "", _, identity(_))
    }.mkString(",\n")
  }

  /** Makes a pretty String representation of the given iterator of BSON elements. */
  def pretty(it: Iterable[BSONElement]): String =
    "{\n" + pretty(0, it) + "\n}"

  private def pretty(
      i: Int,
      indent: String,
      name: String,
      value: BSONValue,
      f: String => String
    ): String = {

    val prefix = s"${indent}${f(name)}"

    value match {
      case BSONArray(vs) =>
        s"${prefix}[\n" + pretty(i + 1, vs) + s"\n${indent}]"

      case bin: BSONBinary =>
        s"${prefix}${BSONBinary pretty bin}"

      case BSONBoolean(bool) =>
        s"${prefix}${bool}"

      case time: BSONDateTime =>
        s"${prefix}${BSONDateTime pretty time}"

      case BSONDocument(elements) =>
        s"${prefix}{\n" + pretty(i + 1, elements) + s"\n$indent}"

      case BSONDouble(d) =>
        s"${prefix}$d"

      case BSONInteger(i) =>
        s"${prefix}$i"

      case l: BSONLong =>
        s"${prefix}${BSONLong pretty l}"

      case d: BSONDecimal =>
        s"${prefix}${BSONDecimal pretty d}"

      case s: BSONString =>
        s"${prefix}${BSONString pretty s}"

      case id: BSONObjectID =>
        s"${prefix}${BSONObjectID pretty id}"

      case ts: BSONTimestamp =>
        s"${prefix}${BSONTimestamp pretty ts}"

      case BSONNull      => s"${prefix}${BSONNull.pretty}"
      case BSONUndefined => s"${prefix}${BSONUndefined.pretty}"
      case BSONMinKey    => s"${prefix}${BSONMinKey.pretty}"
      case BSONMaxKey    => s"${prefix}${BSONMaxKey.pretty}"

      case _ =>
        s"${prefix}$value"
    }
  }
}
