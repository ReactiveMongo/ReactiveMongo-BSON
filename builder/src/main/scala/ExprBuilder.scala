package reactivemongo.api.bson.builder

import scala.util.{ Success, Try }

import reactivemongo.api.bson.{
  ¬,
  BSONArray,
  BSONDocument,
  BSONString,
  BSONValue,
  BSONWriter,
  DocumentClass
}

/**
 * Represents a MongoDB aggregation expression for use in queries and aggregation pipelines.
 *
 * This class wraps a BSON value that represents a MongoDB expression, providing
 * type safety for the document type and expression value type.
 *
 * {{{
 * import reactivemongo.api.bson.BSONString
 * import reactivemongo.api.bson.builder.{ Expr, ExprBuilder }
 *
 * case class Foo(name: String, age: Int, tags: Seq[String])
 *
 * val builder = ExprBuilder.empty[Foo]
 *
 * // Create an expression from a constant value
 * val constExpr = builder.from("John")
 *
 * // Create an expression from a raw BSON value
 * val rawExpr = Expr.unsafe[Foo, String](BSONString("$$name"))
 * }}}
 *
 * @tparam T the case class/product type representing the document
 * @tparam V the expression type (e.g., String, Int, etc.)
 */
sealed class Expr[T, V](
    private[builder] val writes: () => Try[BSONValue]) {
  private[builder] type Inner = V

  @SuppressWarnings(Array("AsInstanceOf"))
  def widen[U >: V]: Expr[T, U] = this.asInstanceOf[Expr[T, U]]
}

object Expr extends ExprCompat with ExprLowPriority1 {

  /**
   * Creates an Expr from a raw BSON value without compile-time type checking.
   *
   * This method bypasses the usual type safety guarantees and should be used
   * with caution. It's useful when you need to create expressions from
   * dynamically generated BSON values or when working with MongoDB operators
   * that are not yet supported by the type-safe API.
   *
   * {{{
   * import reactivemongo.api.bson.{ BSONString, BSONDocument }
   * import reactivemongo.api.bson.builder.Expr
   *
   * case class Foo(name: String)
   *
   * // Create an expression from a raw BSON value
   * val expr = Expr.unsafe[Foo, String](BSONString("$$$$name"))
   *
   * // Create a MongoDB operator expression
   * val upperExpr = Expr.unsafe[Foo, String](
   *   BSONDocument("$$$$toUpper" -> "$$$$name")
   * )
   * }}}
   *
   * @tparam T the document type
   * @tparam V the expression value type
   * @param repr the BSON value representing the expression
   * @return an Expr wrapping the provided BSON value
   */
  def unsafe[T, V](repr: BSONValue): Expr[T, V] = new Expr(() => Success(repr))

  /**
   * BSON writer for Expr, allowing expressions to be serialized to BSON.
   *
   * This enables Expr instances to be used anywhere a BSONWriter is required,
   * such as in filter conditions, projections, or update operations.
   *
   * {{{
   * import reactivemongo.api.bson.{ BSONDocument, BSONString }
   * import reactivemongo.api.bson.builder.Expr
   *
   * case class Foo(name: String)
   *
   * val expr = Expr.unsafe[Foo, String](BSONString("$$$$name"))
   *
   * // The writer enables direct use in BSON documents
   * val doc = BSONDocument("field" -> expr)
   * }}}
   *
   * @tparam T the document type
   * @tparam V the expression value type
   * @return a BSONWriter that serializes the expression
   */
  implicit def writer[T, V]: BSONWriter[Expr[T, V]] =
    BSONWriter.from(_.writes())

  type Number[T] = NumberExpr[T, _]

  type Document[T] = DocumentExpr[T, _]

  private[builder] type Opaque[T] = Expr[T, _]
}

private[builder] sealed trait ExprLowPriority1 {

  implicit def opaqueWriter[T]: BSONWriter[Expr[T, _]] =
    BSONWriter.from(_.writes())
}

final class NumberExpr[T, V] private[builder] (writes: () => Try[BSONValue])
    extends Expr[T, V](writes)

object NumberExpr {
  import scala.language.implicitConversions

  implicit def fromNumeric[T, V](
      e: Expr[T, V]
    )(implicit
      /* @unused */ i0: Numeric[V]
    ): NumberExpr[T, V] = new NumberExpr[T, V](e.writes)
}

final class DocumentExpr[T, V] private[builder] (writes: () => Try[BSONValue])
    extends Expr[T, V](writes)

object DocumentExpr {
  import scala.language.implicitConversions

  implicit def fromProduct[T, V](
      e: Expr[T, V]
    )(implicit
      /* @unused */ i0: DocumentClass[V]
    ): DocumentExpr[T, V] = new DocumentExpr[T, V](e.writes)
}

final class ExprBuilder[T] private[builder] (
    protected val prefix: Seq[String])
    extends ExprBuilderCompat[T] {

  // --- Arithmetic Operators ---

  /**
   * Adds numbers or dates using MongoDB's `$$add` operator.
   *
   * Note: The result type is inferred from the first expression.
   * All subsequent expressions must be numeric but can have different numeric types.
   *
   * {{{
   * import reactivemongo.api.bson.builder.ExprBuilder
   *
   * case class Product(price: Double, tax: Double)
   *
   * val builder = ExprBuilder.empty[Product]
   * val price = builder.select(Symbol("price"))
   * val tax = builder.select(Symbol("tax"))
   *
   * val total = builder.add(price, tax)
   * }}}
   */
  def add[N](
      head: Expr[T, N],
      tail: Expr.Number[T]*
    )(implicit
      /* @unused */ i0: Numeric[N]
    ): Expr[T, N] = new Expr[T, N](() =>
    Try(BSONDocument(f"$$add" -> (head +: tail).map(v => v: Expr.Opaque[T])))
  )

  /**
   * Subtracts numbers or dates using MongoDB's `$$subtract` operator.
   *
   * {{{
   * import reactivemongo.api.bson.builder.ExprBuilder
   *
   * case class Product(price: Double, discount: Double)
   *
   * val builder = ExprBuilder.empty[Product]
   * val price = builder.select(Symbol("price"))
   * val discount = builder.select(Symbol("discount"))
   *
   * val finalPrice = builder.subtract(price, discount)
   * }}}
   */
  def subtract[N](
      minuend: Expr[T, N],
      subtrahend: Expr.Number[T]
    )(implicit
      /* @unused */ i0: Numeric[N]
    ): Expr[T, N] =
    new Expr[T, N](() =>
      Try(
        BSONDocument(
          f"$$subtract" -> Seq[Expr.Opaque[T]](minuend, subtrahend)
        )
      )
    )

  /**
   * Multiplies numbers using MongoDB's `$$multiply` operator.
   *
   * Note: The result type is inferred from the first expression.
   * All subsequent expressions must be numeric but can have different numeric types.
   *
   * {{{
   * import reactivemongo.api.bson.builder.ExprBuilder
   *
   * case class OrderLine(price: Double, quantity: Int)
   *
   * val builder = ExprBuilder.empty[OrderLine]
   * val price = builder.select(Symbol("price"))
   * val qty = builder.select(Symbol("quantity"))
   *
   * val lineTotal = builder.multiply(price, qty)
   * // Result: { "$$multiply": ["$$price", "$$quantity"] }
   * }}}
   */
  def multiply[N](
      head: Expr[T, N],
      tail: Expr.Number[T]*
    )(implicit
      /* @unused */ i0: Numeric[N]
    ): Expr[T, N] = new Expr[T, N](() =>
    Try(
      BSONDocument(f"$$multiply" -> (head +: tail).map(v => v: Expr.Opaque[T]))
    )
  )

  /**
   * Divides numbers using MongoDB's `$$divide` operator.
   *
   * Note: The result type is inferred from the dividend (first expression).
   *
   * {{{
   * import reactivemongo.api.bson.builder.ExprBuilder
   *
   * case class Stats(total: Double, count: Int)
   *
   * val builder = ExprBuilder.empty[Stats]
   * val total = builder.select(Symbol("total"))
   * val count = builder.select(Symbol("count"))
   *
   * val average = builder.divide(total, count)
   * // Result: { "$$divide": ["$$total", "$$count"] }
   * }}}
   */
  def divide[N](
      dividend: Expr[T, N],
      divisor: Expr.Number[T]
    )(implicit
      /* @unused */ i0: Numeric[N]
    ): Expr[T, N] =
    new Expr[T, N](() =>
      Try(BSONDocument(f"$$divide" -> Seq[Expr.Opaque[T]](dividend, divisor)))
    )

  /**
   * Returns the modulo using MongoDB's `$$mod` operator.
   *
   * Note: The result type is inferred from the dividend (first expression).
   *
   * {{{
   * import reactivemongo.api.bson.builder.ExprBuilder
   *
   * case class Item(quantity: Int)
   *
   * val builder = ExprBuilder.empty[Item]
   * val qty = builder.select(Symbol("quantity"))
   * val two = builder.from(2)
   *
   * val remainder = builder.mod(qty, two)
   * // Result: { "$$mod": ["$$quantity", 2] }
   * }}}
   */
  def mod[N](
      dividend: Expr[T, N],
      divisor: Expr.Number[T]
    )(implicit
      /* @unused */ i0: Numeric[N]
    ): Expr[T, N] =
    new Expr[T, N](() =>
      Try(BSONDocument(f"$$mod" -> Seq[Expr.Opaque[T]](dividend, divisor)))
    )

  /**
   * Returns the absolute value using MongoDB's `$$abs` operator.
   *
   * Note: The result type is the same as the input expression type.
   *
   * {{{
   * import reactivemongo.api.bson.builder.ExprBuilder
   *
   * case class Measurement(value: Double)
   *
   * val builder = ExprBuilder.empty[Measurement]
   * val value = builder.select(Symbol("value"))
   *
   * val absVal = builder.abs(value)
   * // Result: { "$$abs": "$$value" }
   * }}}
   */
  def abs[N](
      expr: Expr[T, N]
    )(implicit
      /* @unused */ i0: Numeric[N]
    ): Expr[T, N] =
    new Expr[T, N](() => Try(BSONDocument(f"$$abs" -> expr)))

  /**
   * Returns the smallest integer greater than or equal to the specified number.
   *
   * {{{
   * import reactivemongo.api.bson.builder.ExprBuilder
   *
   * case class Measurement(value: Double)
   *
   * val builder = ExprBuilder.empty[Measurement]
   * val value = builder.select(Symbol("value"))
   *
   * val ceilVal = builder.ceil(value)
   * // Result: { "$$ceil": "$$value" }
   * }}}
   */
  def ceil[N](
      expr: Expr[T, N]
    )(implicit
      /* @unused */ i0: Numeric[N]
    ): Expr[T, N] =
    new Expr[T, N](() => Try(BSONDocument(f"$$ceil" -> expr)))

  /**
   * Returns the largest integer less than or equal to the specified number.
   *
   * {{{
   * import reactivemongo.api.bson.builder.ExprBuilder
   *
   * case class Measurement(value: Double)
   *
   * val builder = ExprBuilder.empty[Measurement]
   * val value = builder.select(Symbol("value"))
   *
   * val floorVal = builder.floor(value)
   * // Result: { "$$floor": "$$value" }
   * }}}
   */
  def floor[N](
      expr: Expr[T, N]
    )(implicit
      /* @unused */ i0: Numeric[N]
    ): Expr[T, N] =
    new Expr[T, N](() => Try(BSONDocument(f"$$floor" -> expr)))

  /**
   * Rounds a number to a specified decimal place using MongoDB's `$$round` operator.
   *
   * {{{
   * import reactivemongo.api.bson.builder.ExprBuilder
   *
   * case class Measurement(value: Double)
   *
   * val builder = ExprBuilder.empty[Measurement]
   * val value = builder.select(Symbol("value"))
   *
   * val rounded = builder.round(value, 2)
   * // Result: { "$$round": ["$$value", 2] }
   * }}}
   */
  def round[N](
      expr: Expr[T, N],
      place: Int = 0
    )(implicit
      /* @unused */ i0: Numeric[N]
    ): Expr[T, N] =
    new Expr[T, N](() =>
      Try(BSONDocument(f"$$round" -> BSONArray(expr, place)))
    )

  /**
   * Returns the square root of a number using MongoDB's `$$sqrt` operator.
   */
  def sqrt[N](
      expr: Expr[T, N]
    )(implicit
      /* @unused */ i0: Numeric[N]
    ): Expr[T, Double] =
    new Expr[T, Double](() => Try(BSONDocument(f"$$sqrt" -> expr)))

  /**
   * Raises a number to the specified exponent using MongoDB's `$$pow` operator.
   *
   * Note: Returns `Double` regardless of input types.
   *
   * {{{
   * import reactivemongo.api.bson.builder.ExprBuilder
   *
   * case class Geometry(side: Double)
   *
   * val builder = ExprBuilder.empty[Geometry]
   * val side = builder.select(Symbol("side"))
   *
   * val squared = builder.pow(side, builder.from(2.0))
   * // Result: { "$$pow": ["$$side", 2.0] }
   * }}}
   */
  def pow[N](
      base: Expr[T, N],
      exponent: Expr.Number[T]
    )(implicit
      /* @unused */ i0: Numeric[N]
    ): Expr[T, Double] =
    new Expr[T, Double](() =>
      Try(BSONDocument(f"$$pow" -> Seq[Expr.Opaque[T]](base, exponent)))
    )

  // --- Comparison Operators ---

  /**
   * Compares two ordered values of potentially different types for equality using MongoDB's `$$eq` operator.
   *
   * This overloaded version allows comparing expressions of different ordered types
   * (e.g., Int and Long, String and other comparable types). Both types must have
   * an implicit `FilterBuilder.Ordered` instance available.
   *
   * Note: This is a lower priority implicit resolution alternative to the standard `eq`
   * method that requires both expressions to have compatible types.
   *
   * {{{
   * import reactivemongo.api.bson.{ BSONInteger, BSONLong }
   * import reactivemongo.api.bson.builder.{ Expr, ExprBuilder }
   *
   * case class Stats(count: Int, total: Long)
   *
   * val builder = ExprBuilder.empty[Stats]
   * val countExpr = Expr.unsafe[Stats, Int](BSONInteger(100))
   * val totalExpr = Expr.unsafe[Stats, Long](BSONLong(100L))
   *
   * // Compare Int and Long expressions
   * val comparison = builder.eq(countExpr, totalExpr)
   * // Result: { "$$eq": [100, 100] }
   * }}}
   *
   * @tparam A the type of the first expression (must be ordered)
   * @tparam B the type of the second expression (must be ordered)
   * @param expr1 the first expression to compare
   * @param expr2 the second expression to compare
   * @return a Boolean expression that evaluates to true if the values are equal
   */
  def eq[A, B](
      expr1: Expr[T, A],
      expr2: Expr[T, B]
    )(implicit
      /* @unused */ i0: CanEqual[A, B]
    ): Expr[T, Boolean] =
    new Expr[T, Boolean](() =>
      Try(BSONDocument(f"$$eq" -> Seq[Expr.Opaque[T]](expr1, expr2)))
    )

  /**
   * Compares two values for inequality using MongoDB's `$$ne` operator.
   */
  def ne[V](
      expr1: Expr[T, V],
      expr2: Expr[T, _ <: V]
    ): Expr[T, Boolean] =
    new Expr[T, Boolean](() =>
      Try(BSONDocument(f"$$ne" -> Seq(expr1, expr2.widen[V])))
    )

  /**
   * Returns true if the first value is greater than the second.
   */
  def gt[A, B](
      expr1: Expr[T, A],
      expr2: Expr[T, B]
    )(implicit
      /* @unused */ i0: FilterBuilder.Ordered[A],
      /* @unused */ i1: FilterBuilder.Ordered[B]
    ): Expr[T, Boolean] =
    new Expr[T, Boolean](() =>
      Try(BSONDocument(f"$$gt" -> Seq[Expr.Opaque[T]](expr1, expr2)))
    )

  /**
   * Returns true if the first value is greater than or equal to the second.
   */
  def gte[A, B](
      expr1: Expr[T, A],
      expr2: Expr[T, B]
    )(implicit
      /* @unused */ i0: FilterBuilder.Ordered[A],
      /* @unused */ i1: FilterBuilder.Ordered[B]
    ): Expr[T, Boolean] =
    new Expr[T, Boolean](() =>
      Try(BSONDocument(f"$$gte" -> Seq[Expr.Opaque[T]](expr1, expr2)))
    )

  /**
   * Returns true if the first value is less than the second.
   */
  def lt[A, B](
      expr1: Expr[T, A],
      expr2: Expr[T, B]
    )(implicit
      /* @unused */ i0: FilterBuilder.Ordered[A],
      /* @unused */ i1: FilterBuilder.Ordered[B]
    ): Expr[T, Boolean] =
    new Expr[T, Boolean](() =>
      Try(BSONDocument(f"$$lt" -> Seq[Expr.Opaque[T]](expr1, expr2)))
    )

  /**
   * Returns true if the first value is less than or equal to the second.
   */
  def lte[A, B](
      expr1: Expr[T, A],
      expr2: Expr[T, B]
    )(implicit
      /* @unused */ i0: FilterBuilder.Ordered[A],
      /* @unused */ i1: FilterBuilder.Ordered[B]
    ): Expr[T, Boolean] =
    new Expr[T, Boolean](() =>
      Try(BSONDocument(f"$$lte" -> Seq[Expr.Opaque[T]](expr1, expr2)))
    )

  /**
   * Compares two values and returns -1, 0, or 1 using MongoDB's `$$cmp` operator.
   */
  def cmp[A, B](
      expr1: Expr[T, A],
      expr2: Expr[T, B]
    )(implicit
      /* @unused */ i0: FilterBuilder.Ordered[A],
      /* @unused */ i1: FilterBuilder.Ordered[B]
    ): Expr[T, Int] =
    new Expr[T, Int](() =>
      Try(BSONDocument(f"$$cmp" -> Seq[Expr.Opaque[T]](expr1, expr2)))
    )

  // --- Logical Operators ---

  /**
   * Returns true if all expressions evaluate to true using MongoDB's `$$and` operator.
   */
  def and(head: Expr[T, Boolean], tail: Expr[T, Boolean]*): Expr[T, Boolean] =
    new Expr[T, Boolean](() => Try(BSONDocument(f"$$and" -> (head +: tail))))

  /**
   * Returns true if any expression evaluates to true using MongoDB's `$$or` operator.
   */
  def or(head: Expr[T, Boolean], tail: Expr[T, Boolean]*): Expr[T, Boolean] =
    new Expr[T, Boolean](() => Try(BSONDocument(f"$$or" -> (head +: tail))))

  /**
   * Returns the boolean negation using MongoDB's `$$not` operator.
   */
  def not(expr: Expr[T, Boolean]): Expr[T, Boolean] =
    new Expr[T, Boolean](() => Try(BSONDocument(f"$$not" -> expr)))

  // --- String Operators ---

  /**
   * Concatenates multiple string expressions using MongoDB's `$$concat` operator.
   */
  def concat(head: Expr[T, String], tail: Expr[T, String]*): Expr[T, String] =
    new Expr[T, String](() => Try(BSONDocument(f"$$concat" -> (head +: tail))))

  /**
   * Returns a substring using MongoDB's `$$substr` operator.
   *
   * @param expr the string expression
   * @param start the starting index (0-based)
   * @param length the length of the substring
   */
  def substr(expr: Expr[T, String], start: Int, length: Int): Expr[T, String] =
    new Expr[T, String](() =>
      Try(BSONDocument(f"$$substr" -> BSONArray(expr, start, length)))
    )

  /**
   * Converts a string to lowercase using MongoDB's `$$toLower` operator.
   */
  def toLower(expr: Expr[T, String]): Expr[T, String] =
    new Expr[T, String](() => Try(BSONDocument(f"$$toLower" -> expr)))

  /**
   * Converts a string to uppercase using MongoDB's `$$toUpper` operator.
   */
  def toUpper(expr: Expr[T, String]): Expr[T, String] =
    new Expr[T, String](() => Try(BSONDocument(f"$$toUpper" -> expr)))

  /**
   * Returns the length of a string using MongoDB's `$$strLen` operator.
   */
  def strLen(expr: Expr[T, String]): Expr[T, Int] =
    new Expr[T, Int](() => Try(BSONDocument(f"$$strLen" -> expr)))

  /**
   * Removes whitespace from the beginning and end of a string.
   */
  def trim(expr: Expr[T, String]): Expr[T, String] =
    new Expr[T, String](() =>
      Try(BSONDocument(f"$$trim" -> BSONDocument("input" -> expr)))
    )

  /**
   * Splits a string into an array of substrings using MongoDB's `$$split` operator.
   */
  def split(expr: Expr[T, String], delimiter: String): Expr[T, Seq[String]] =
    new Expr[T, Seq[String]](() =>
      Try(BSONDocument(f"$$split" -> BSONArray(expr, delimiter)))
    )

  // --- Array Operators ---

  /**
   * Returns the element at the specified array index using MongoDB's `$$arrayElemAt` operator.
   *
   * {{{
   * import reactivemongo.api.bson.builder.ExprBuilder
   *
   * case class User(tags: Seq[String])
   *
   * val builder = ExprBuilder.empty[User]
   * val tags = builder.select(Symbol("tags"))
   *
   * val firstTag = builder.arrayElemAt(tags, 0)
   * // Result: { "$$arrayElemAt": ["$$tags", 0] }
   * }}}
   */
  def arrayElemAt[V](
      array: Expr[T, _ <: Iterable[V]],
      index: Int
    )(implicit
      /* @unused */ i0: array.Inner ¬ Option[V]
    ): Expr[T, V] =
    new Expr[T, V](() =>
      Try(BSONDocument(f"$$arrayElemAt" -> BSONArray(array, index)))
    )

  /**
   * Concatenates arrays using MongoDB's `$$concatArrays` operator.
   *
   * {{{
   * import reactivemongo.api.bson.builder.ExprBuilder
   *
   * case class User(tags: Seq[String])
   *
   * val builder = ExprBuilder.empty[User]
   * val tags = builder.select(Symbol("tags"))
   * val more = builder.from(Seq("foo", "bar"))
   *
   * val all = builder.concatArrays(tags, more)
   * // Result: { "$$concatArrays": ["$$tags", ["foo", "bar"]] }
   * }}}
   */
  def concatArrays[V](
      head: Expr[T, _ <: Iterable[V]],
      tail: Expr[T, _ <: Iterable[V]]*
    )(implicit
      /* @unused */ i0: head.Inner ¬ Option[V]
    ): Expr[T, Seq[V]] =
    new Expr[T, Seq[V]](() =>
      Try(
        BSONDocument(
          f"$$concatArrays" -> (head +: tail).map(v => v: Expr.Opaque[T])
        )
      )
    )

  /**
   * Filters array elements using MongoDB's `$$filter` operator.
   *
   * {{{
   * import reactivemongo.api.bson.builder.ExprBuilder
   *
   * case class Doc(scores: Seq[Int])
   *
   * val builder = ExprBuilder.empty[Doc]
   * val scores = builder.select(Symbol("scores"))
   *
   * val passing = builder.filter(scores) { itemExpr =>
   *   builder.gte(itemExpr, builder.from(60))
   * }
   * // Result: { "$$filter": { "input": "$$scores", "cond": { "$$gte": ["$$$$this", 60] }, "as": "this" } }
   * }}}
   *
   * @param input the array expression
   * @param as optional variable name (defaults to "this")
   * @param cond the condition expression
   */
  def filter[V](
      input: Expr[T, _ <: Iterable[V]],
      as: String = "this"
    )(cond: Expr[T, V] => Expr[T, Boolean]
    )(implicit
      /* @unused */ i0: input.Inner ¬ Option[V]
    ): Expr[T, Seq[V]] = new Expr[T, Seq[V]](() =>
    Try(
      BSONDocument(
        f"$$filter" -> BSONDocument(
          "input" -> (input: Expr.Opaque[T]),
          "cond" -> cond(Expr.unsafe[T, V](BSONString("$" + as))),
          "as" -> as
        )
      )
    )
  )

  /**
   * Returns the number of elements in an array using MongoDB's `$$size` operator.
   */
  def size[V](
      array: Expr[T, _ <: Iterable[V]]
    )(implicit
      /* @unused */ i0: array.Inner ¬ Option[V]
    ): Expr[T, Int] =
    new Expr[T, Int](() =>
      Try(BSONDocument(f"$$size" -> (array: Expr.Opaque[T])))
    )

  /**
   * Returns a subset of an array using MongoDB's `$$slice` operator.
   */
  def slice[V](
      array: Expr[T, _ <: Iterable[V]],
      n: Int
    )(implicit
      /* @unused */ i0: array.Inner ¬ Option[V]
    ): Expr[T, Seq[V]] =
    new Expr[T, Seq[V]](() =>
      Try(BSONDocument(f"$$slice" -> BSONArray(array, n)))
    )

  /**
   * Returns a subset of an array with position and length.
   */
  def slice[V](
      array: Expr[T, _ <: Iterable[V]],
      position: Int,
      n: Int
    )(implicit
      /* @unused */ i0: array.Inner ¬ Option[V]
    ): Expr[T, Seq[V]] =
    new Expr[T, Seq[V]](() =>
      Try(BSONDocument(f"$$slice" -> BSONArray(array, position, n)))
    )

  /**
   * Returns true if a value is in an array using MongoDB's `$$in` operator.
   */
  def in[V](
      value: Expr[T, V],
      array: Expr[T, _ <: Iterable[V]]
    )(implicit
      /* @unused */ i0: value.Inner ¬ Option[V]
    ): Expr[T, Boolean] =
    new Expr[T, Boolean](() =>
      Try(BSONDocument(f"$$in" -> BSONArray(value, array)))
    )

  // --- Date Operators ---

  /**
   * Returns the year using MongoDB's `$$year` operator.
   */
  def year[D](
      date: Expr[T, D]
    )(implicit
      /* @unused */ i0: Temporal[D]
    ): Expr[T, Int] =
    new Expr[T, Int](() => Try(BSONDocument(f"$$year" -> date)))

  /**
   * Returns the month using MongoDB's `$$month` operator (1-12).
   */
  def month[D](
      date: Expr[T, D]
    )(implicit
      /* @unused */ i0: Temporal[D]
    ): Expr[T, Int] =
    new Expr[T, Int](() => Try(BSONDocument(f"$$month" -> date)))

  /**
   * Returns the day of month using MongoDB's `$$dayOfMonth` operator (1-31).
   */
  def dayOfMonth[D](
      date: Expr[T, D]
    )(implicit
      /* @unused */ i0: Temporal[D]
    ): Expr[T, Int] =
    new Expr[T, Int](() => Try(BSONDocument(f"$$dayOfMonth" -> date)))

  /**
   * Returns the day of week using MongoDB's `$$dayOfWeek` operator (1-7, Sunday is 1).
   */
  def dayOfWeek[D](
      date: Expr[T, D]
    )(implicit
      /* @unused */ i0: Temporal[D]
    ): Expr[T, Int] =
    new Expr[T, Int](() => Try(BSONDocument(f"$$dayOfWeek" -> date)))

  /**
   * Returns the hour using MongoDB's `$$hour` operator (0-23).
   */
  def hour[D](
      date: Expr[T, D]
    )(implicit
      /* @unused */ i0: Temporal[D]
    ): Expr[T, Int] =
    new Expr[T, Int](() => Try(BSONDocument(f"$$hour" -> date)))

  /**
   * Returns the minute using MongoDB's `$$minute` operator (0-59).
   */
  def minute[D](
      date: Expr[T, D]
    )(implicit
      /* @unused */ i0: Temporal[D]
    ): Expr[T, Int] =
    new Expr[T, Int](() => Try(BSONDocument(f"$$minute" -> date)))

  /**
   * Returns the second using MongoDB's `$$second` operator (0-59).
   */
  def second[D](
      date: Expr[T, D]
    )(implicit
      /* @unused */ i0: Temporal[D]
    ): Expr[T, Int] =
    new Expr[T, Int](() => Try(BSONDocument(f"$$second" -> date)))

  /**
   * Returns the millisecond using MongoDB's `$$millisecond` operator (0-999).
   */
  def millisecond[D](
      date: Expr[T, D]
    )(implicit
      /* @unused */ i0: Temporal[D]
    ): Expr[T, Int] =
    new Expr[T, Int](() => Try(BSONDocument(f"$$millisecond" -> date)))

  /**
   * Converts a date to a string using MongoDB's `$$dateToString` operator.
   *
   * @param date the date expression
   * @param format the format string (e.g., "%Y-%m-%d")
   */
  def dateToString[D](
      date: Expr[T, D],
      format: String
    )(implicit
      /* @unused */ i0: Temporal[D]
    ): Expr[T, String] =
    new Expr[T, String](() =>
      Try(
        BSONDocument(
          f"$$dateToString" -> BSONDocument("date" -> date, "format" -> format)
        )
      )
    )

  // --- Conditional Operators ---

  /**
   * Evaluates a boolean expression and returns one of two values using MongoDB's `$$cond` operator.
   *
   * {{{
   * import reactivemongo.api.bson.builder.{ Expr, ExprBuilder }
   * import reactivemongo.api.bson.BSONInteger
   *
   * case class Order(quantity: Int, price: Double)
   *
   * val builder = ExprBuilder.empty[Order]
   * val quantityExpr = builder.select(Symbol("quantity"))
   * val priceExpr = builder.select(Symbol("price"))
   * val ten = Expr.unsafe[Order, Int](BSONInteger(10))
   *
   * val discounted = builder.cond(
   *   builder.gt(quantityExpr, ten),
   *   priceExpr,
   *   priceExpr
   * )
   * }}}
   */
  def cond[V](
      condition: Expr[T, Boolean],
      ifTrue: Expr[T, V],
      ifFalse: Expr[T, V]
    ): Expr[T, V] =
    new Expr[T, V](() =>
      Try(
        BSONDocument(
          f"$$cond" -> BSONDocument(
            "if" -> condition,
            "then" -> ifTrue,
            "else" -> ifFalse
          )
        )
      )
    )

  /**
   * Returns the first non-null value using MongoDB's `$$ifNull` operator.
   */
  def ifNull[V](
      expr: Expr[T, V],
      replacement: Expr[T, V]
    ): Expr[T, V] =
    new Expr[T, V](() =>
      Try(BSONDocument(f"$$ifNull" -> Seq(expr, replacement)))
    )

  /**
   * Evaluates a series of case expressions using MongoDB's `$$switch` operator.
   *
   * {{{
   * import reactivemongo.api.bson.builder.ExprBuilder
   *
   * case class Student(score: Int)
   *
   * val builder = ExprBuilder.empty[Student]
   * val score = builder.select(Symbol("score"))
   *
   * builder.switch(
   *   branches = Seq(
   *     (builder.gte(score, builder.from(90)), builder.from("A")),
   *     (builder.gte(score, builder.from(80)), builder.from("B")),
   *     (builder.gte(score, builder.from(70)), builder.from("C"))
   *   ),
   *   default = builder.from("F")
   * )
   * }}}
   *
   * @param branches sequence of (condition, value) pairs
   * @param default the default value if no condition matches
   */
  def switch[V](
      branches: Seq[(Expr[T, Boolean], Expr[T, V])],
      default: Expr[T, V]
    ): Expr[T, V] = {
    val branchDocs = branches.map {
      case (cond, value) =>
        BSONDocument("case" -> cond, "then" -> value)
    }

    new Expr[T, V](() =>
      Try(
        BSONDocument(
          f"$$switch" -> BSONDocument(
            "branches" -> branchDocs,
            "default" -> default
          )
        )
      )
    )
  }

  // --- Type Conversion Operators ---

  /**
   * Returns the BSON type of a field using MongoDB's `$$type` operator.
   */
  def `type`[V](expr: Expr[T, V]): Expr[T, String] =
    new Expr[T, String](() => Try(BSONDocument(f"$$type" -> expr)))

  /**
   * Converts a value to a string using MongoDB's `$$toString` operator.
   */
  def toString[V](expr: Expr[T, V]): Expr[T, String] =
    new Expr[T, String](() => Try(BSONDocument(f"$$toString" -> expr)))

  /**
   * Converts a value to an integer using MongoDB's `$$toInt` operator.
   */
  def toInt[V](expr: Expr[T, V]): Expr[T, Int] =
    new Expr[T, Int](() => Try(BSONDocument(f"$$toInt" -> expr)))

  /**
   * Converts a value to a double using MongoDB's `$$toDouble` operator.
   */
  def toDouble[V](expr: Expr[T, V]): Expr[T, Double] =
    new Expr[T, Double](() => Try(BSONDocument(f"$$toDouble" -> expr)))

  /**
   * Converts a value to a long using MongoDB's `$$toLong` operator.
   */
  def toLong[V](expr: Expr[T, V]): Expr[T, Long] =
    new Expr[T, Long](() => Try(BSONDocument(f"$$toLong" -> expr)))

  /**
   * Converts a value to a boolean using MongoDB's `$$toBool` operator.
   */
  def toBool[V](expr: Expr[T, V]): Expr[T, Boolean] =
    new Expr[T, Boolean](() => Try(BSONDocument(f"$$toBool" -> expr)))

  /**
   * Converts a value using MongoDB's `$$convert` operator with explicit type specification.
   *
   * {{{
   * import reactivemongo.api.bson.builder.ExprBuilder
   *
   * case class Doc(code: String)
   *
   * val builder = ExprBuilder.empty[Doc]
   * val code = builder.select(Symbol("code"))
   *
   * builder.convert[String, Int](
   *   code,
   *   to = "int",
   *   onError = Some(builder.from(0)),
   *   onNull = Some(builder.from(0))
   * )
   * // Result: { "$$convert": { "input": "$$code", "to": "int", "onError": 0, "onNull": 0 } }
   * }}}
   *
   * @param input the expression to convert
   * @param to the target BSON type name (e.g., "string", "int", "double")
   * @param onError optional expression to return on error
   * @param onNull optional expression to return on null input
   */
  def convert[V, R](
      input: Expr[T, V],
      to: String,
      onError: Option[Expr[T, R]] = None,
      onNull: Option[Expr[T, R]] = None
    ): Expr[T, R] = {
    def doc = BSONDocument("input" -> input, "to" -> to)
    def withError = onError.fold(doc)(e => doc ++ BSONDocument("onError" -> e))
    def withNull =
      onNull.fold(withError)(n => withError ++ BSONDocument("onNull" -> n))

    new Expr[T, R](() => Try(BSONDocument(f"$$convert" -> withNull)))
  }

  // --- Miscellaneous Operators ---

  /**
   * Evaluates an expression and returns the value using MongoDB's `$$literal` operator.
   * Useful for values that might be interpreted as expressions.
   *
   * {{{
   * import reactivemongo.api.bson.builder.ExprBuilder
   *
   * case class Doc(code: String)
   *
   * ExprBuilder.empty[Doc].literal("$$notAFieldRef")
   * // Result: { "$$literal": "$$notAFieldRef" }
   * }}}
   */
  def literal[V](
      value: V
    )(implicit
      i0: BSONWriter[V]
    ): Expr[T, V] =
    new Expr[T, V](() => Try(BSONDocument(f"$$literal" -> value)))

  /**
   * Merges multiple documents using MongoDB's `$$mergeObjects` operator.
   *
   * {{{
   * import reactivemongo.api.bson.{ BSONDocument }
   * import reactivemongo.api.bson.builder.ExprBuilder
   *
   * case class Address(city: String, country: String)
   * case class Person(name: String, address: Address)
   *
   * val builder = ExprBuilder.empty[Person]
   * val address = builder.select(Symbol("address"))
   * val overrides = builder.from(BSONDocument("city" -> "Foo"))
   *
   * val merged = builder.mergeObjects(address, overrides)
   * // Result: { "$$mergeObjects": ["$$address", { "city": "Foo" }] }
   * }}}
   */
  def mergeObjects[V](
      head: Expr.Document[T],
      tail: Expr[T, BSONDocument]*
    ): Expr[T, V] =
    new Expr[T, V](() =>
      Try(
        BSONDocument(
          f"$$mergeObjects" -> (head +: tail).map(v => v: Expr.Opaque[T])
        )
      )
    )

  // --- Additional Array Operators ---

  /**
   * Returns true if an array is a subset of another using MongoDB's `$$setIsSubset` operator.
   *
   * {{{
   * import reactivemongo.api.bson.builder.ExprBuilder
   *
   * case class User(tags: Seq[String])
   *
   * val builder = ExprBuilder.empty[User]
   * val setA = builder.select(Symbol("tags"))
   * val setB = builder.from(Seq("foo", "bar"))
   *
   * val isSubset = builder.setIsSubset(setA, setB)
   * // Result: { "$$setIsSubset": ["$$tags", ["foo", "bar"]] }
   * }}}
   */
  def setIsSubset[V](
      subset: Expr[T, Seq[V]],
      superset: Expr[T, Seq[V]]
    ): Expr[T, Boolean] =
    new Expr[T, Boolean](() =>
      Try(BSONDocument(f"$$setIsSubset" -> Seq(subset, superset)))
    )

  /**
   * Returns the intersection of arrays using MongoDB's `$$setIntersection` operator.
   *
   * {{{
   * import reactivemongo.api.bson.builder.ExprBuilder
   *
   * case class User(tags: Seq[String])
   *
   * val builder = ExprBuilder.empty[User]
   * val setA = builder.select(Symbol("tags"))
   * val setB = builder.from(Seq("foo", "bar"))
   *
   * val common = builder.setIntersection(setA, setB)
   * // Result: { "$$setIntersection": ["$$tags", ["foo", "bar"]] }
   * }}}
   */
  def setIntersection[V](
      head: Expr[T, Seq[V]],
      tail: Expr[T, Seq[V]]*
    ): Expr[T, Seq[V]] =
    new Expr[T, Seq[V]](() =>
      Try(BSONDocument(f"$$setIntersection" -> (head +: tail)))
    )

  /**
   * Returns the union of arrays using MongoDB's `$$setUnion` operator.
   */
  def setUnion[V](
      head: Expr[T, Seq[V]],
      tail: Expr[T, Seq[V]]*
    ): Expr[T, Seq[V]] =
    new Expr[T, Seq[V]](() =>
      Try(BSONDocument(f"$$setUnion" -> (head +: tail)))
    )

  /**
   * Returns the difference of arrays using MongoDB's `$$setDifference` operator.
   */
  def setDifference[V](
      expr1: Expr[T, Seq[V]],
      expr2: Expr[T, Seq[V]]
    ): Expr[T, Seq[V]] =
    new Expr[T, Seq[V]](() =>
      Try(BSONDocument(f"$$setDifference" -> Seq(expr1, expr2)))
    )

  /**
   * Returns the first element of an array using MongoDB's `$$first` operator.
   */
  def first[V](
      array: Expr[T, Seq[V]]
    ): Expr[T, V] =
    new Expr[T, V](() => Try(BSONDocument(f"$$first" -> array)))

  /**
   * Returns the last element of an array using MongoDB's `$$last` operator.
   */
  def last[V](
      array: Expr[T, Seq[V]]
    ): Expr[T, V] =
    new Expr[T, V](() => Try(BSONDocument(f"$$last" -> array)))

  /**
   * Reverses an array using MongoDB's `$$reverseArray` operator.
   */
  def reverseArray[V](
      array: Expr[T, Seq[V]]
    ): Expr[T, Seq[V]] =
    new Expr[T, Seq[V]](() => Try(BSONDocument(f"$$reverseArray" -> array)))

  /**
   * Returns a range of numbers using MongoDB's `$$range` operator.
   *
   * @param start the start of the range
   * @param end the end of the range (exclusive)
   * @param step optional step size (default 1)
   */
  def range(start: Int, end: Int, step: Int = 1): Expr[T, Seq[Int]] =
    new Expr[T, Seq[Int]](() =>
      Try(BSONDocument(f"$$range" -> Seq(start, end, step)))
    )

  // --- Additional String Operators ---

  /**
   * Returns the index of a substring using MongoDB's `$$indexOfBytes` operator.
   */
  def indexOfBytes(
      string: Expr[T, String],
      substring: String,
      start: Int = 0,
      end: Option[Int] = None
    ): Expr[T, Int] = {
    val args = end match {
      case Some(e) => BSONArray(string, substring, start, e)
      case None    => BSONArray(string, substring, start)
    }

    new Expr[T, Int](() => Try(BSONDocument(f"$$indexOfBytes" -> args)))
  }

  /**
   * Replaces all occurrences of a substring using MongoDB's `$$replaceAll` operator.
   */
  def replaceAll(
      input: Expr[T, String],
      find: String,
      replacement: String
    ): Expr[T, String] =
    new Expr[T, String](() =>
      Try(
        BSONDocument(
          f"$$replaceAll" -> BSONDocument(
            "input" -> input,
            "find" -> find,
            "replacement" -> replacement
          )
        )
      )
    )

  /**
   * Tests if a string matches a regex pattern using MongoDB's `$$regexMatch` operator.
   */
  def regexMatch(
      input: Expr[T, String],
      regex: String,
      options: String = ""
    ): Expr[T, Boolean] = {
    val doc = if (options.isEmpty) {
      BSONDocument("input" -> input, "regex" -> regex)
    } else {
      BSONDocument("input" -> input, "regex" -> regex, "options" -> options)
    }
    new Expr[T, Boolean](() => Try(BSONDocument(f"$$regexMatch" -> doc)))
  }

  // --- Aggregation Accumulator Operators ---

  /**
   * Returns the sum of numeric values using MongoDB's `$$sum` operator.
   */
  def sum[N](
      expr: Expr[T, N]
    )(implicit
      /* @unused */ i0: Numeric[N]
    ): Expr[T, N] =
    new Expr[T, N](() => Try(BSONDocument(f"$$sum" -> expr)))

  /**
   * Returns the average of numeric values using MongoDB's `$$avg` operator.
   */
  def avg[N](
      expr: Expr[T, N]
    )(implicit
      /* @unused */ i0: Numeric[N]
    ): Expr[T, Double] =
    new Expr[T, Double](() => Try(BSONDocument(f"$$avg" -> expr)))

  /**
   * Returns the maximum value using MongoDB's `$$max` operator.
   */
  def max[V](
      expr: Expr[T, V]
    ): Expr[T, V] =
    new Expr[T, V](() => Try(BSONDocument(f"$$max" -> expr)))

  /**
   * Returns the maximum of multiple values.
   */
  def max[V](
      head: Expr[T, V],
      tail: Expr[T, V]*
    ): Expr[T, V] =
    new Expr[T, V](() => Try(BSONDocument(f"$$max" -> (head +: tail))))

  /**
   * Returns the minimum value using MongoDB's `$$min` operator.
   */
  def min[V](
      expr: Expr[T, V]
    ): Expr[T, V] =
    new Expr[T, V](() => Try(BSONDocument(f"$$min" -> expr)))

  /**
   * Returns the minimum of multiple values.
   */
  def min[V](
      head: Expr[T, V],
      tail: Expr[T, V]*
    ): Expr[T, V] =
    new Expr[T, V](() => Try(BSONDocument(f"$$min" -> (head +: tail))))

  // --- Object/Document Operators ---

  /**
   * Returns an array of field names from a document using MongoDB's `$$objectToArray` operator.
   *
   * {{{
   * import reactivemongo.api.bson.builder.ExprBuilder
   *
   * case class Address(city: String, country: String)
   * case class Person(name: String, address: Address)
   *
   * val builder = ExprBuilder.empty[Person]
   * val address = builder.select(Symbol("address"))
   *
   * val asArray = builder.objectToArray(address)
   * // Result: { "$$objectToArray": "$$address" }
   * }}}
   */
  def objectToArray[V](
      doc: Expr[T, V]
    )(implicit
      /* @unused */ i0: DocumentClass[V]
    ): Expr[T, Seq[BSONDocument]] =
    new Expr[T, Seq[BSONDocument]](() =>
      Try(BSONDocument(f"$$objectToArray" -> doc))
    )

  /**
   * Converts an array of key-value pairs to a document using MongoDB's `$$arrayToObject` operator.
   *
   * {{{
   * import reactivemongo.api.bson.builder.ExprBuilder
   *
   * case class Address(city: String, country: String)
   * case class Person(name: String, address: Address)
   *
   * val builder = ExprBuilder.empty[Person]
   * val address = builder.select(Symbol("address"))
   * val asArray = builder.objectToArray(address)
   *
   * val asObj = builder.arrayToObject(asArray)
   * // Result: { "$$arrayToObject": { "$$objectToArray": "$$address" } }
   * }}}
   */
  def arrayToObject(array: Expr[T, Seq[BSONDocument]]): Expr[T, BSONDocument] =
    new Expr[T, BSONDocument](() =>
      Try(BSONDocument(f"$$arrayToObject" -> array))
    )
}

object ExprBuilder {

  /**
   * Creates an empty ExprBuilder for the specified document type.
   *
   * This is the entry point for building type-safe MongoDB expressions.
   * The empty builder has no prefix, meaning field references will be
   * relative to the root of the document.
   *
   * {{{
   * import reactivemongo.api.bson.builder.ExprBuilder
   *
   * case class Foo(name: String, age: Int)
   *
   * val builder = ExprBuilder.empty[Foo]
   *
   * // Now you can use the builder to create expressions
   * val nameExpr = builder.from("John")
   * }}}
   *
   * @tparam T the document type for type-safe field references
   * @return a new empty ExprBuilder
   */
  def empty[T]: ExprBuilder[T] = new ExprBuilder[T](Seq.empty)
}
