package reactivemongo.api.bson.specs2

import reactivemongo.api.bson.{
  BSONArray,
  BSONDocument,
  BSONElement,
  BSONValue
}

import org.specs2.matcher.describe.{
  ComparisonResult,
  Diffable,
  PrimitiveDifference,
  PrimitiveIdentical,
  UnorderedCollectionDifferent
}

/**
 * {{{
 * import reactivemongo.api.bson.BSONDocument
 * import reactivemongo.api.bson.specs2._
 *
 * final class MySpec extends org.specs2.mutable.Specification {
 *   "Foo" title
 *
 *   "Bar" should {
 *     "lorem" in {
 *       BSONDocument("ipsum" -> 1) must_=== BSONDocument("dolor" -> 2)
 *       // Use provided Diffable to display difference
 *       // between actual and expected documents
 *     }
 *   }
 * }
 * }}}
 */
object `package` extends LowPriorityImplicits {
  implicit val diffableDocument: Diffable[BSONDocument] = DiffableDocument

  implicit val diffableArray: Diffable[BSONArray] = DiffableArray
}

private[specs2] sealed trait LowPriorityImplicits {
  implicit def diffableValue[T <: BSONValue]: Diffable[T] = new DiffableValue[T]
}

private final class DiffableValue[T <: BSONValue] extends Diffable[T] {
  @inline private def nullValue: BSONValue = null

  def diff(actual: T, expected: T): ComparisonResult = {
    if (actual == nullValue && expected == nullValue) {
      PrimitiveIdentical(actual)
    } else if (actual == nullValue || expected == nullValue) {
      PrimitiveDifference(actual, expected)
    } else (actual, expected) match {
      case (actualDoc: BSONDocument, expectedDoc: BSONDocument) =>
        DiffableDocument.diff(actualDoc, expectedDoc)

      case (actualArr: BSONArray, expectedArr: BSONArray) =>
        DiffableArray.diff(actualArr, expectedArr)

      case _ =>
        Diffable.fallbackDiffable.diff(actual, expected)
    }
  }
}

private object DiffableDocument extends Diffable[BSONDocument] {
  def diff(actual: BSONDocument, expected: BSONDocument): ComparisonResult = {
    if (actual == null && expected == null) {
      PrimitiveIdentical(actual)
    } else if (actual == null || expected == null) {
      PrimitiveDifference(actual, expected)
    } else {
      val same = Seq.newBuilder[BSONElement]
      val added = Seq.newBuilder[BSONElement]
      val removed = Seq.newBuilder[BSONElement]
      val changed = Seq.newBuilder[(String, ComparisonResult)]
      val commonFields = Seq.newBuilder[String]

      actual.elements.foreach { elm =>
        expected.get(elm.name) match {
          case Some(v) if v == elm.value => {
            commonFields += elm.name
            same += elm
          }

          case Some(v) => {
            commonFields += elm.name
            changed += elm.name -> diffableValue[BSONValue].diff(elm.value, v)
          }

          case _ =>
            added += elm
        }
      }

      val removedFields = expected.toMap -- commonFields.result()

      removedFields.foreach {
        case (name, value) => removed += BSONElement(name, value)
      }

      new DocumentDifference(
        same = same.result(),
        changed = changed.result(),
        added = added.result(),
        removed = removed.result())
    }
  }
}

private final class DocumentDifference(
  same: Seq[BSONElement],
  changed: Seq[(String, ComparisonResult)],
  added: Seq[BSONElement],
  removed: Seq[BSONElement]) extends UnorderedCollectionDifferent(same, changed, added, removed) {
  val className = "BSONDocument"

  def renderElement(indent: String)(element: BSONElement): String =
    s"'${element.name}': ${BSONValue pretty element.value}"

  def renderChange(indent: String)(change: (String, ComparisonResult)): String =
    s"'${change._1}': ${change._2.render(indent)}"

}

private object DiffableArray extends Diffable[BSONArray] {
  def diff(actual: BSONArray, expected: BSONArray): ComparisonResult = {
    if (actual == null && expected == null) {
      PrimitiveIdentical(actual)
    } else if (actual == null || expected == null) {
      PrimitiveDifference(actual, expected)
    } else {
      val actualValues = actual.values.sortBy(_.hashCode)
      val expectedValues = expected.values.sortBy(_.hashCode)

      val same = Seq.newBuilder[BSONValue]
      val added = Seq.newBuilder[BSONValue]

      actualValues.foreach { v =>
        if (expectedValues contains v) {
          same += v
        } else {
          added += v
        }
      }

      val a = added.result()
      val s = same.result()

      new ArrayDifference(
        same = s,
        added = a,
        removed = expectedValues.diff(a).diff(s))
    }
  }
}

private final class ArrayDifference(
  same: Seq[BSONValue],
  added: Seq[BSONValue],
  removed: Seq[BSONValue]) extends UnorderedCollectionDifferent(
  same, Seq.empty[BSONValue], added, removed) {

  val className = "BSONArray"

  def renderElement(indent: String)(value: BSONValue): String =
    BSONValue.pretty(value)

  def renderChange(indent: String)(value: BSONValue): String =
    BSONValue.pretty(value)
}
