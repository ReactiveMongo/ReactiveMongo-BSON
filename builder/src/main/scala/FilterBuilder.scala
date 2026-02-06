package reactivemongo.api.bson.builder

import java.time.temporal.Temporal

import scala.collection.mutable.{ Builder, Map => MMap }

import scala.annotation.implicitNotFound
import scala.math.Numeric

import reactivemongo.api.bson.{
  BSONArray,
  BSONDocument,
  BSONString,
  BSONValue,
  BSONWriter,
  Producer
}

//import shapeless._

/**
 * Mutable builder (based on `scala.collection.mutable.Builder`)
 * so make sure not to share instances between different contexts.
 *
 * Useful to prepare BSON filters consistent with existing type.
 */
final class FilterBuilder[T] private[builder] (
    private[builder] val clauses: MMap[String, BSONValue],
    protected val prefix: Seq[String])
    extends FilterCompat[T] { self =>

  @inline def untyped: MMap[String, BSONValue] = clauses

  protected def unsafeFilter[A](
      field: String,
      op: String,
      value: A
    )(implicit
      i1: BSONWriter[A]
    ): FilterBuilder[T] = {
    val bsonPath = (prefix :+ field).mkString(".")

    val fieldOps: BSONDocument = clauses
      .get(bsonPath)
      .collect { case doc: BSONDocument => doc }
      .getOrElse(BSONDocument.empty)

    clauses += bsonPath -> (fieldOps ++ (op -> value))

    this
  }

  /** Add a `$$comment` */
  def comment(text: String): FilterBuilder[T] = {
    clauses += f"$$comment" -> BSONString(text)

    this
  }

  /** Returns the filters. */
  def result(): BSONDocument = BSONDocument(clauses.result())

  /**
   * Combine the filters with the `and` semantic.
   * If there is only one single filter, then it is directly returned.
   */
  def and(): BSONDocument = clauses.result().toSeq match {
    case Seq() =>
      BSONDocument.empty

    case Seq(single @ (_, _)) =>
      BSONDocument(single)

    case fs =>
      BSONDocument(f"$$and" -> BSONArray(fs.map {
        case (path, expr) => BSONDocument(path -> expr)
      }))
  }

  /**
   * Combine the filters with the `or` semantic.
   * If there is only one single filter, then it is directly returned.
   */
  def or(): BSONDocument = clauses.result().toSeq match {
    case Seq() =>
      BSONDocument.empty

    case Seq(single @ (_, _)) =>
      BSONDocument(single)

    case fs =>
      BSONDocument(f"$$or" -> BSONArray(fs.map {
        case (path, expr) => BSONDocument(path -> expr)
      }))
  }
}

object FilterBuilder {

  def empty[T]: FilterBuilder[T] =
    new FilterBuilder[T](MMap.empty, Seq.empty)

  // ---

  final class Nested[T, A] private[builder] (
      in: => FilterBuilder[A],
      out: => FilterBuilder[T]) {

    def at(f: FilterBuilder[A] => Any): FilterBuilder[T] = {
      f(in)

      out
    }
  }

  // ---

  /**
   * @tparam T the document type
   * @tparam K the field type
   */
  final class OperationBuilder[T, K] private[builder] (
      build: BSONDocument => FilterBuilder[T]) {

    private[builder] val ops: Builder[(String, Producer[BSONValue]), Seq[(String, Producer[BSONValue])]] =
      Seq.newBuilder

    /**
     * Returns a builder with a typed `eq` clause.
     *
     * {{{
     * import reactivemongo.api.bson.builder.FilterBuilder
     *
     * case class Foo(tags: Seq[String])
     *
     * val builder = FilterBuilder.empty[Foo]
     * builder.not(Symbol("tags")).apply {
     *   _.eq("foo")
     * }
     * }}}
     *
     * It is statically checked that field with such name exists
     * and is comparable with type `A`.
     */
    def eq[A](
        value: A
      )(implicit
        /*@unused */ i0: MongoComparable[T, K, A],
        i1: BSONWriter[A]
      ): OperationBuilder[T, K] = unsafe[A]("eq", value)

    /**
     * Returns a builder with a typed `gt` clause.
     *
     * {{{
     * import reactivemongo.api.bson.builder.FilterBuilder
     *
     * case class Foo(scores: Seq[Int])
     *
     * val builder = FilterBuilder.empty[Foo]
     * builder.not(Symbol("scores")).apply {
     *   _.gt(1)
     * }
     * }}}
     *
     * It is statically checked that field with such name exists
     * and is comparable with type `A`.
     */
    def gt[A](
        value: A
      )(implicit
        /*@unused */ i0: MongoComparable[T, K, A],
        i1: BSONWriter[A],
        /*@unused*/ i2: FilterBuilder.Ordered[A]
      ): OperationBuilder[T, K] = unsafe[A]("gt", value)

    /**
     * Returns a builder with a typed `gte` clause.
     *
     * {{{
     * import reactivemongo.api.bson.builder.FilterBuilder
     *
     * case class Foo(scores: Seq[Int])
     *
     * val builder = FilterBuilder.empty[Foo]
     * builder.not(Symbol("scores")).apply {
     *   _.gte(1)
     * }
     * }}}
     *
     * It is statically checked that field with such name exists
     * and is comparable with type `A`.
     */
    def gte[A](
        value: A
      )(implicit
        /*@unused */ i0: MongoComparable[T, K, A],
        i1: BSONWriter[A],
        /*@unused*/ i2: FilterBuilder.Ordered[A]
      ): OperationBuilder[T, K] = unsafe[A]("gte", value)

    /**
     * Returns a builder with a typed `lt` clause.
     *
     * {{{
     * import reactivemongo.api.bson.builder.FilterBuilder
     *
     * case class Foo(scores: Seq[Int])
     *
     * val builder = FilterBuilder.empty[Foo]
     * builder.not(Symbol("scores")).apply {
     *   _.lt(10)
     * }
     * }}}
     *
     * It is statically checked that field with such name exists
     * and is comparable with type `A`.
     */
    def lt[A](
        value: A
      )(implicit
        /*@unused */ i0: MongoComparable[T, K, A],
        i1: BSONWriter[A],
        /*@unused*/ i2: FilterBuilder.Ordered[A]
      ): OperationBuilder[T, K] = unsafe[A]("lt", value)

    /**
     * Returns a builder with a typed `lte` clause.
     *
     * {{{
     * import reactivemongo.api.bson.builder.FilterBuilder
     *
     * case class Foo(scores: Seq[Int])
     *
     * val builder = FilterBuilder.empty[Foo]
     * builder.not(Symbol("scores")).apply {
     *   _.lte(10)
     * }
     * }}}
     *
     * It is statically checked that field with such name exists
     * and is comparable with type `A`.
     */
    def lte[A](
        value: A
      )(implicit
        /*@unused */ i0: MongoComparable[T, K, A],
        i1: BSONWriter[A],
        /*@unused*/ i2: FilterBuilder.Ordered[A]
      ): OperationBuilder[T, K] = unsafe[A]("lte", value)

    /**
     * Returns a builder with a typed `ne` clause.
     *
     * {{{
     * import reactivemongo.api.bson.builder.FilterBuilder
     *
     * case class Foo(tags: Seq[String])
     *
     * val builder = FilterBuilder.empty[Foo]
     * builder.not(Symbol("tags")).apply {
     *   _.ne("foo")
     * }
     * }}}
     *
     * It is statically checked that field with such name exists
     * and is comparable with type `A`.
     */
    def ne[A](
        value: A
      )(implicit
        /*@unused */ i0: MongoComparable[T, K, A],
        i1: BSONWriter[A]
      ): OperationBuilder[T, K] = unsafe[A]("ne", value)

    private def unsafe[A](
        opName: String,
        value: A
      )(implicit
        /*@unused */ i0: MongoComparable[T, K, A],
        /*@unused */ i1: BSONWriter[A]
      ): OperationBuilder[T, K] = {
      ops += f"$$${opName}" -> value

      this
    }

    private[builder] def result(): FilterBuilder[T] = build {
      BSONDocument(
        ops.result().flatMap { case (k, vp) => vp.generate().map(k -> _) }
      )
    }
  }

  // ---

  /**
   * Evidence that type `T` can be used with MongoDB ordering operators
   * such as `$$gt` (`$$lt`, ...).
   */
  @implicitNotFound(msg = "Type ${T} cannot be used with ordering operator")
  trait Ordered[T]

  object Ordered {

    @SuppressWarnings(Array("AsInstanceOf"))
    implicit def numeric[T](
        implicit
        /* @unused */ i0: Numeric[T]
      ): Ordered[T] = unsafe.asInstanceOf[Ordered[T]]

    @SuppressWarnings(Array("AsInstanceOf"))
    implicit def temporal[T <: Temporal]: Ordered[T] =
      unsafe.asInstanceOf[Ordered[T]]

    private val unsafe = new Ordered[Nothing] {}
  }
}
