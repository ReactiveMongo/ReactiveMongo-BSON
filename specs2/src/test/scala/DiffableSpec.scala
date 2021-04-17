import reactivemongo.api.bson.{ BSONArray, BSONDocument, BSONString }
import reactivemongo.api.bson.specs2._

import org.specs2.matcher.describe.{ PrimitiveDifference, PrimitiveIdentical }

final class DiffableSpec extends org.specs2.mutable.Specification {
  "Diffable" title

  "Array comparision result" should {
    "indicate everything is null" in {
      diffableArray.diff(null, null) must_=== PrimitiveIdentical(null)
    }

    "indicate primitive difference if actual is null" in {
      diffableArray.diff(null, BSONArray.empty) must_=== PrimitiveDifference(
        actual = null,
        expected = BSONArray.empty
      )
    }

    "indicate primitive difference if expected is null (and actual not)" in {
      diffableArray.diff(BSONArray.empty, null) must_=== PrimitiveDifference(
        actual = BSONArray.empty,
        expected = null
      )
    }

    "indicate an unordered difference" in {
      diffableArray
        .diff(
          actual = BSONArray("added", "same"),
          expected = BSONArray("same", "removed")
        )
        .render must_=== """BSONArray('same',
          added: 'added',
          removed: 'removed')"""
    }
  }

  "Document comparision result" should {
    "indicate everything is null" in {
      diffableDocument.diff(null, null) must_=== PrimitiveIdentical(null)
    }

    "indicate primitive difference if actual is null" in {
      diffableDocument.diff(
        null,
        BSONDocument.empty
      ) must_=== PrimitiveDifference(
        actual = null,
        expected = BSONDocument.empty
      )
    }

    "indicate primitive difference if expected is null (and actual not)" in {
      diffableDocument.diff(
        BSONDocument.empty,
        null
      ) must_=== PrimitiveDifference(
        actual = BSONDocument.empty,
        expected = null
      )
    }

    "indicate an unordered difference" in {
      diffableDocument
        .diff(
          actual = BSONDocument(
            "added" -> 1,
            "changedPrimitive" -> "value",
            "changedArr" -> BSONArray("_1"),
            "changedDoc" -> BSONDocument("foo" -> 1)
          ),
          expected = BSONDocument(
            "changedArr" -> BSONArray.empty,
            "changedPrimitive" -> 1.23D,
            "removed" -> 2,
            "changedDoc" -> BSONDocument("bar" -> 2)
          )
        )
        .render must_=== """BSONDocument('changedPrimitive': BSONString(value) != BSONDouble(1.23),
             'changedArr': BSONArray(added: '_1'),
             'changedDoc': BSONDocument(added: 'foo': 1,
                          removed: 'bar': 2),
             added: 'added': 1,
             removed: 'removed': 2)"""

    }
  }

  "Value comparision result" should {
    "indicate everything is null" in {
      diffableValue.diff(null, null) must_=== PrimitiveIdentical(null)
    }

    "indicate primitive difference if actual is null" in {
      diffableValue.diff(null, BSONDocument.empty) must_=== PrimitiveDifference(
        actual = null,
        expected = BSONDocument.empty
      )
    }

    "indicate primitive difference if expected is null (and actual not)" in {
      diffableValue.diff(BSONString("str"), null) must_=== PrimitiveDifference(
        actual = BSONString("str"),
        expected = null
      )
    }
  }
}
