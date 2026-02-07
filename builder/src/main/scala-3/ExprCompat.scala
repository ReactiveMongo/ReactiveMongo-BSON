package reactivemongo.api.bson.builder

import scala.util.{ Success, Try }

import scala.annotation.unused

import reactivemongo.api.bson.{ BSONDocument, BSONString, BSONWriter }

private[builder] trait ExprBuilderCompat[T] { self: ExprBuilder[T] =>

  /**
   * Creates an Expr from a value that can be serialized to BSON.
   *
   * This method allows you to create expressions from constant values or
   * computed values that have a BSONWriter. The value will be serialized
   * to BSON when the expression is evaluated.
   *
   * The type constraint ensures that the value is not already an Expr,
   * preventing double-wrapping.
   *
   * {{{
   * import reactivemongo.api.bson.builder.ExprBuilder
   *
   * case class Foo(name: String, age: Int)
   *
   * val builder = ExprBuilder.empty[Foo]
   *
   * // Create expressions from constant values
   * val nameExpr = builder.from("John")
   * val ageExpr = builder.from(42)
   * val tagsExpr = builder.from(Seq("tag1", "tag2"))
   * }}}
   *
   * @tparam V the value type (must have a BSONWriter)
   * @param value the value to wrap in an expression
   * @param i0 implicit BSONWriter for the value type
   * @param i1 evidence that V is not already an Expr
   * @return an Expr containing the serialized value
   */
  def from[V](
      value: V
    )(using
      i0: BSONWriter[V],
      i1: BsonPath.<:!<[V, Expr[_, _]]
    ): Expr[T, V] = new Expr[T, V](() => i0.writeTry(value))

  /**
   * Creates a field selector for type-safe field references in expressions.
   *
   * This method provides compile-time validation that the field exists in the
   * document type and has the expected type. It uses Scala 3's match types
   * and tuple-based paths to verify field paths.
   *
   * {{{
   * import reactivemongo.api.bson.builder.ExprBuilder
   *
   * case class User(name: String, age: Int, address: Address)
   * case class Address(city: String, country: String)
   *
   * val builder = ExprBuilder.empty[User]
   *
   * // Select a top-level field
   * val nameExpr = builder.select("name")
   *
   * // Select nested fields using tuple notation
   * val cityExpr = builder.select("address", "city")
   * }}}
   *
   * @tparam V the field type
   * @return a Selector that can be used to select fields
   */
  // [V]: Selector[T, V] = new Selector[T, V]()
  object select {

    private def pathToSeq(path: Tuple): Seq[String] =
      path.productIterator.map(_.asInstanceOf[String]).toSeq

    def apply[K1 <: String & Singleton, V](
        k1: K1
      )(using
        i0: BsonPath.Lookup[T, K1 *: EmptyTuple, ?]
      ): Expr[T, i0.Inner] = {
      val pathSeq = self.prefix ++ pathToSeq(k1 *: EmptyTuple)

      new Expr[T, i0.Inner](() =>
        Success(BSONString("$" + pathSeq.mkString(".")))
      )
    }

    def apply[K1 <: String & Singleton, K2 <: String & Singleton](
        k1: K1,
        k2: K2
      )(using
        i0: BsonPath.Lookup[T, (K1, K2), ?]
      ): Expr[T, i0.Inner] = {
      val pathSeq = self.prefix ++ pathToSeq(k1 *: k2 *: EmptyTuple)

      new Expr[T, i0.Inner](() =>
        Success(BSONString("$" + pathSeq.mkString(".")))
      )
    }

    def apply[
        K1 <: String & Singleton,
        K2 <: String & Singleton,
        K3 <: String & Singleton,
        V
      ](k1: K1,
        k2: K2,
        k3: K3
      )(using
        i0: BsonPath.Lookup[T, (K1, K2, K3), ?]
      ): Expr[T, i0.Inner] = {
      val pathSeq = self.prefix ++ pathToSeq(k1 *: k2 *: k3 *: EmptyTuple)

      new Expr[T, i0.Inner](() =>
        Success(BSONString("$" + pathSeq.mkString(".")))
      )
    }

    def apply[
        K1 <: String & Singleton,
        K2 <: String & Singleton,
        K3 <: String & Singleton,
        K4 <: String & Singleton,
        V
      ](k1: K1,
        k2: K2,
        k3: K3,
        k4: K4
      )(using
        i0: BsonPath.Lookup[T, (K1, K2, K3, K4), ?]
      ): Expr[T, i0.Inner] = {
      val pathSeq = self.prefix ++ pathToSeq(k1 *: k2 *: k3 *: k4 *: EmptyTuple)

      new Expr[T, i0.Inner](() =>
        Success(BSONString("$" + pathSeq.mkString(".")))
      )
    }

    def apply[
        K1 <: String & Singleton,
        K2 <: String & Singleton,
        K3 <: String & Singleton,
        K4 <: String & Singleton,
        K5 <: String & Singleton,
        V
      ](k1: K1,
        k2: K2,
        k3: K3,
        k4: K4,
        k5: K5
      )(using
        i0: BsonPath.Lookup[T, (K1, K2, K3, K4, K5), ?]
      ): Expr[T, i0.Inner] = {
      val pathSeq =
        self.prefix ++ pathToSeq(k1 *: k2 *: k3 *: k4 *: k5 *: EmptyTuple)

      new Expr[T, i0.Inner](() =>
        Success(BSONString("$" + pathSeq.mkString(".")))
      )
    }

    def apply[
        K1 <: String & Singleton,
        K2 <: String & Singleton,
        K3 <: String & Singleton,
        K4 <: String & Singleton,
        K5 <: String & Singleton,
        K6 <: String & Singleton,
        V
      ](k1: K1,
        k2: K2,
        k3: K3,
        k4: K4,
        k5: K5,
        k6: K6
      )(using
        i0: BsonPath.Lookup[T, (K1, K2, K3, K4, K5, K6), ?]
      ): Expr[T, i0.Inner] = {
      val pathSeq =
        self.prefix ++ pathToSeq(k1 *: k2 *: k3 *: k4 *: k5 *: k6 *: EmptyTuple)

      new Expr[T, i0.Inner](() =>
        Success(BSONString("$" + pathSeq.mkString(".")))
      )
    }
  }

  /**
   * Returns the value of a field from a document using MongoDB's `$$getField` operator.
   *
   * {{{
   * import reactivemongo.api.bson.builder.ExprBuilder
   *
   * case class Address(city: String, country: String)
   * case class Person(name: String, address: Address)
   *
   * val builder = ExprBuilder.empty[Person]
   * val address = builder.select("address")
   *
   * val city = builder.getField(address, "city")
   * // Result: { "$$getField": { "field": "city", "input": "$$address" } }
   * }}}
   */
  def getField[V, U](
      input: Expr[T, V],
      field: String & Singleton
    )(implicit
      /* @unused */ i0: BsonPath.Exists[V, field.type, U]
    ): Expr[T, V] =
    new Expr[T, V](() =>
      Try(
        BSONDocument(
          f"$$getField" -> BSONDocument(
            "field" -> BSONString(field),
            "input" -> input
          )
        )
      )
    )
}

private[builder] trait ExprCompat {

  object implicits {

    /**
     * Implicit conversion that enables using `Expr` values in MongoDB comparable operations.
     *
     * '''Important:''' This implicit should '''only''' be imported when expressions are supported by MongoDB.
     * Expressions are supported in:
     * - Aggregation pipelines (including `\$project`, `\$addFields`, `\$set`, etc.)
     * - Updates with aggregation pipeline (available since MongoDB 4.2)
     * - `\$expr` operator in queries
     *
     * Expressions are '''not''' supported in:
     * - Simple update operations (using `\$set`, `\$inc`, etc. without aggregation pipeline)
     * - Traditional query operators (use filter builders instead)
     *
     * @see [[https://www.mongodb.com/docs/manual/reference/method/db.collection.update/#std-label-update-with-aggregation-pipeline MongoDB Update with Aggregation Pipeline]]
     *
     * {{{
     * import reactivemongo.api.bson.builder.{ ExprBuilder, UpdateBuilder }
     * import reactivemongo.api.bson.builder.Expr.implicits.given
     *
     * case class Product(name: String, price: Double, tax: Double)
     *
     * val exprBuilder = ExprBuilder.empty[Product]
     * val updateBuilder = UpdateBuilder.empty[Product]
     *
     * val price = exprBuilder.select("price")
     * val tax = exprBuilder.select("tax")
     *
     * // OK: Using expressions in aggregation pipeline update (MongoDB 4.2+)
     * val totalPrice = exprBuilder.add(price, tax)
     * val update = updateBuilder.set("price", totalPrice)
     *
     * // NOT OK: Don't import this implicit for simple updates
     * // val simpleUpdate = updateBuilder.set("price", 42.0)
     * }}}
     *
     * @tparam T the document type
     * @tparam K the field key type
     * @tparam V the field value type
     * @param i0 implicit MongoComparable instance for the underlying type
     * @param i1 evidence that V is not already an Expr
     * @return a MongoComparable instance for Expr-represented values
     */
    given mongoComparable[T, K, V](
        using
        @unused i0: MongoComparable[T, K, V],
        @unused i1: BsonPath.<:!<[V, Expr[_, _]]
      ): MongoComparable[T, K, Expr[T, V]] = MongoComparable[T, K, Expr[T, V]]
  }
}
