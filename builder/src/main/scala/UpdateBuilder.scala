package reactivemongo.api.bson.builder

import scala.collection.mutable.{ Map => MMap }

import reactivemongo.api.bson.{ BSONDocument, BSONValue }

/**
 * Mutable builder for MongoDB update operations.
 *
 * Provides a type-safe, fluent API for constructing MongoDB update documents
 * with compile-time verification of field names and types.
 *
 * {{{
 * import reactivemongo.api.bson.builder.UpdateBuilder
 *
 * case class Foo(name: String, age: Int, tags: Seq[String])
 *
 * val update = UpdateBuilder.empty[Foo]
 *   .set(Symbol("name"), "Alice")
 *   .inc(Symbol("age"), 1)
 *   .push(Symbol("tags"), "scala")
 *   .result()
 * }}}
 */
final class UpdateBuilder[T] private[builder] (
    private[builder] val operations: MMap[String, BSONDocument],
    protected val prefix: Seq[String])
    extends UpdateCompat[T] { self =>

  /**
   * Returns the raw operations map (mainly for testing/debugging).
   */
  @inline def untyped: MMap[String, BSONDocument] = operations

  /**
   * Constructs the fully qualified field path.
   */
  protected def fieldPath(field: String): String = {
    if (prefix.isEmpty) field
    else (prefix :+ field).mkString(".")
  }

  /**
   * Adds a clause to the specified MongoDB operation.
   */
  protected def addClause(
      operation: String,
      field: String,
      value: BSONValue
    ): UpdateBuilder[T] = {
    val path = fieldPath(field)
    val doc = operations.getOrElse(operation, BSONDocument.empty)

    operations += operation -> (doc ++ BSONDocument(path -> value))

    this
  }

  /**
   * Builds the final MongoDB update document.
   *
   * {{{
   * import reactivemongo.api.bson.builder.UpdateBuilder
   *
   * case class Foo(name: String, age: Int)
   *
   * val update = UpdateBuilder.empty[Foo]
   *   .set(Symbol("name"), "Alice")
   *   .inc(Symbol("age"), 1)
   *   .result()
   * // Returns: { "$$set": { "name": "Alice" }, "$$inc": { "age": 1 } }
   * }}}
   */
  def result(): BSONDocument =
    BSONDocument(operations.toSeq.map { case (op, doc) => op -> doc })

}

object UpdateBuilder {

  /**
   * Creates an empty UpdateBuilder for the specified type.
   *
   * {{{
   * import reactivemongo.api.bson.builder.UpdateBuilder
   *
   * case class Foo(name: String, age: Int)
   *
   * val builder = UpdateBuilder.empty[Foo]
   * }}}
   */
  def empty[T]: UpdateBuilder[T] =
    new UpdateBuilder[T](MMap.empty, Seq.empty)

  // ---

  /**
   * Nested update builder for type-safe nested field updates.
   *
   * @tparam T the root document type
   * @tparam A the nested field type
   */
  final class Nested[T, A] private[builder] (
      in: => UpdateBuilder[A],
      out: => UpdateBuilder[T]) {

    /**
     * Applies updates to the nested field.
     *
     * @param f function that performs updates on the nested builder
     * @return the root UpdateBuilder for method chaining
     */
    def at(f: UpdateBuilder[A] => Any): UpdateBuilder[T] = {
      f(in)

      out
    }
  }

  /**
   * Type of date value for `$$currentDate` operator.
   */
  sealed trait CurrentDateType

  object CurrentDateType {
    case object Date extends CurrentDateType
    case object Timestamp extends CurrentDateType
  }

  /**
   * Strategy for `$$pop` operator.
   */
  sealed trait PopStrategy

  object PopStrategy {
    case object First extends PopStrategy
    case object Last extends PopStrategy
  }

  /**
   * Slice modifier for `$$push` with `$$each`.
   */
  sealed trait PushSlice

  object PushSlice {
    case object Empty extends PushSlice
    case class First(n: Int) extends PushSlice
    case class Last(n: Int) extends PushSlice
  }

  /**
   * Sort modifier for `$$push` with `$$each`.
   */
  sealed trait PushSort

  object PushSort {
    case object Ascending extends PushSort
    case object Descending extends PushSort
    case class Document(doc: BSONDocument) extends PushSort
  }
}
