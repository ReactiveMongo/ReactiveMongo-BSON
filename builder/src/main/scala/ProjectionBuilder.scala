package reactivemongo.api.bson.builder

import scala.collection.mutable.{ Map => MMap }

import reactivemongo.api.bson.{ BSONDocument, BSONValue }

/**
 * A builder for creating MongoDB projection documents with compile-time field verification.
 *
 * `ProjectionBuilder` provides a fluent API for constructing MongoDB projection documents
 * while ensuring that only fields that exist on the target struct `T` can be projected.
 * This eliminates runtime errors caused by typos in field names.
 *
 * This builder provides the basic/safe projection features using `includes()` and `excludes()`
 * methods. For additional projection capabilities with custom MongoDB expressions, see the
 * `project()` method which allows custom field path strings with BSON expressions.
 *
 * @tparam T The target struct type that implements the necessary field witness traits
 */
final class ProjectionBuilder[T] private[builder] (
    protected val prefix: Seq[String],
    protected val clauses: MMap[String, BSONValue])
    extends ProjectionCompat[T] { self =>

  /**
   * Builds the final MongoDB projection document.
   *
   * This method produces a `BSONDocument` that can be used directly with MongoDB queries.
   * All accumulated projection clauses are combined into a single document.
   *
   * @return A `BSONDocument` containing all the projection clauses
   *
   * '''Example:'''
   * {{{
   * import reactivemongo.api.bson.builder.ProjectionBuilder
   *
   * case class User(name: String, email: String, age: Int)
   *
   * val projection = ProjectionBuilder.empty[User]
   *   .includes(Symbol("name"))
   *   .excludes(Symbol("email"))
   *   .result()
   *
   * // Use with MongoDB collection
   * // collection.find(query).projection(projection)
   * }}}
   *
   * '''Behavior with Duplicate Fields'''
   *
   * If the same field is projected multiple times with different values,
   * the last value will be used in the final document.
   */
  def result(): BSONDocument = BSONDocument(clauses.toSeq)
}

object ProjectionBuilder {

  /**
   * Creates a new empty `ProjectionBuilder` instance.
   *
   * @tparam T The target struct type that implements the necessary field witness traits
   * @return A new `ProjectionBuilder[T]` instance ready for method chaining
   */
  def empty[T]: ProjectionBuilder[T] =
    new ProjectionBuilder[T](Seq.empty, MMap.empty)

  /**
   * Helper class for nested field projections using the `nested()` method.
   *
   * @tparam T The outer type
   * @tparam U The inner/nested type
   */
  final class Nested[T, U] private[builder] (
      in: => ProjectionBuilder[U],
      out: => ProjectionBuilder[T]) {

    /**
     * Applies a function to configure projections on the nested field.
     *
     * @param f A function that takes a `ProjectionBuilder[U]` and configures projections
     * @return The outer `ProjectionBuilder[T]` for continued chaining
     */
    def at(f: ProjectionBuilder[U] => Any): ProjectionBuilder[T] = {
      f(in)
      out
    }
  }

  /**
   * Helper class for single nested field projections using the `nestedField()` method.
   *
   * @tparam T The outer type
   * @tparam U The inner/nested type
   */
  final class NestedSingle[T, U] private[builder] (
      in: => ProjectionBuilder[U],
      out: => ProjectionBuilder[T]) {

    /**
     * Applies a function to configure projections on the nested field.
     *
     * @param f A function that takes a `ProjectionBuilder[U]` and configures projections
     * @return The outer `ProjectionBuilder[T]` for continued chaining
     */
    def at(f: ProjectionBuilder[U] => Any): ProjectionBuilder[T] = {
      f(in)
      out
    }
  }

  /**
   * Type-level helper for extracting the inner type from nested structures.
   *
   * This trait helps with type inference when working with nested fields,
   * particularly for handling `Option` types.
   */
  sealed trait NestedType[V] {
    type Inner
  }

  object NestedType extends LowPriorityNestedType {
    type Aux[V, U] = NestedType[V] { type Inner = U }

    implicit def optionType[U]: Aux[Option[U], U] =
      new NestedType[Option[U]] {
        type Inner = U
      }
  }

  private[builder] sealed trait LowPriorityNestedType {

    implicit def identityType[V]: NestedType.Aux[V, V] =
      new NestedType[V] {
        type Inner = V
      }
  }
}
