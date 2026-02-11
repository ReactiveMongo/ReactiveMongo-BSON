package reactivemongo.api.bson.builder

import scala.util.{ Success, Try }

import reactivemongo.api.bson.{ BSONDocument, BSONString, BSONWriter }

import shapeless.{ <:!<, HList, SingletonProductArgs, Witness }
import shapeless.ops.hlist.ToTraversable

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
   * @param v the value to wrap in an expression
   * @param i0 implicit BSONWriter for the value type
   * @param i1 evidence that V is not already an Expr
   * @return an Expr containing the serialized value
   */
  def from[V](
      v: V
    )(implicit
      i0: BSONWriter[V],
      i1: V <:!< Expr[_, _]
    ): Expr[T, V] = new Expr[T, V](() => i0.writeTry(v))

  /**
   * Creates a field selector for type-safe field references in expressions.
   *
   * This method provides compile-time validation that the field exists in the
   * document type and has the expected type. It uses Shapeless to navigate
   * the type structure and verify field paths.
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
   * val nameExpr = builder.select(Symbol("name"))
   *
   * // Select nested fields
   * val cityExpr = builder.select(Symbol("address"), Symbol("city")
   * )
   * }}}
   *
   * @tparam V the field type
   * @return a Selector that can be used to select fields
   */
  object select extends SingletonProductArgs {

    def applyProduct[P <: HList, V](
        path: P
      )(implicit
        /* @unused */ i0: BsonPath.Lookup[T, P, V],
        i1: ToTraversable.Aux[P, List, Symbol]
      ): Expr[T, i0.Inner] = {
      val bsonPath =
        (self.prefix ++ path.toList[Symbol].map(_.name)).mkString(".")

      new Expr[T, i0.Inner](() => Success(BSONString("$" + bsonPath)))
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
   * val address = builder.select(Symbol("address"))
   *
   * val city = builder.getField(address, Symbol("city"))
   * // Result: { "$$getField": { "field": "city", "input": "$$address" } }
   * }}}
   */
  def getField[V, U](
      input: Expr[T, V],
      field: Witness.Lt[Symbol]
    )(implicit
      /* @unused */ i0: BsonPath.Exists[V, field.T, U]
    ): Expr[T, V] =
    new Expr[T, V](() =>
      Try(
        BSONDocument(
          f"$$getField" -> BSONDocument(
            "field" -> field.value.name,
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
     * import reactivemongo.api.bson.builder.Expr.implicits._
     *
     * case class Product(name: String, price: Double, tax: Double)
     *
     * val exprBuilder = ExprBuilder.empty[Product]
     * val updateBuilder = UpdateBuilder.empty[Product]
     *
     * val price = exprBuilder.select(Symbol("price"))
     * val tax = exprBuilder.select(Symbol("tax"))
     *
     * // OK: Using expressions in aggregation pipeline update (MongoDB 4.2+)
     * val totalPrice = exprBuilder.add(price, tax)
     * val update = updateBuilder.set(Symbol("price"), totalPrice)
     *
     * // NOT OK: Don't import this implicit for simple updates
     * // val simpleUpdate = updateBuilder.set(Symbol("price"), 42.0)
     * }}}
     *
     * @tparam T the document type
     * @tparam K the field key type
     * @tparam V the field value type
     * @param i0 implicit MongoComparable instance for the underlying type
     * @param i1 evidence that V is not already an Expr
     * @return a MongoComparable instance for Expr-represented values
     */
    implicit def mongoComparable[T, K, V](
        implicit
        /* @unused */ i0: MongoComparable[T, K, V],
        /* @unused */ i1: V <:!< Expr[_, _]
      ): MongoComparable[T, K, Expr[T, V]] = MongoComparable[T, K, Expr[T, V]]
  }
}
