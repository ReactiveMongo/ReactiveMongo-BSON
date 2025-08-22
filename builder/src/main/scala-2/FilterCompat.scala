package reactivemongo.api.bson.builder

import scala.collection.mutable.{ Builder, Map => MMap }

import reactivemongo.api.bson.{ BSONArray, BSONDocument, BSONWriter }

import shapeless.{ ::, HList, HNil, SingletonProductArgs, Witness }
import shapeless.ops.hlist.ToTraversable

private[builder] trait FilterCompat[T] { self: FilterBuilder[T] =>

  /**
   * Returns a builder with a typed `eq` clause.
   *
   * {{{
   * import reactivemongo.api.bson.builder.FilterBuilder
   *
   * case class Foo(id: String)
   *
   * val builder = FilterBuilder.empty[Foo]
   * builder.eq(Symbol("id"), "value")
   * }}}
   *
   * It is statically checked that field with such name exists
   * and is comparable with type `A`.
   */
  def eq[A](
      field: Witness.Lt[Symbol],
      value: A
    )(implicit
      /*@unused */ i0: MongoComparable[T, field.T, A],
      i1: BSONWriter[A]
    ): FilterBuilder[T] = unsafeFilter[A](field.value.name, f"$$eq", value)

  /**
   * Returns a builder with a typed `in` clause.
   *
   * {{{
   * import reactivemongo.api.bson.builder.FilterBuilder
   *
   * case class Foo(name: String)
   *
   * val builder = FilterBuilder.empty[Foo]
   *
   * builder.in(Symbol("name"), Seq("value"))
   * }}}
   *
   * It is statically checked that field with such name exists
   * and is comparable with type `A`.
   */
  def in[A](
      field: Witness.Lt[Symbol],
      value: Iterable[A]
    )(implicit
      /*@unused */ i0: MongoComparable[T, field.T, A],
      i1: BSONWriter[A]
    ): FilterBuilder[T] =
    unsafeFilter[Iterable[A]](field.value.name, f"$$in", value)

  /**
   * Returns a builder with a typed `nin` clause.
   *
   * {{{
   * import reactivemongo.api.bson.builder.FilterBuilder
   *
   * case class Foo(name: String)
   *
   * val builder = FilterBuilder.empty[Foo]
   *
   * builder.nin(Symbol("name"), Seq("value"))
   * }}}
   *
   * It is statically checked that field with such name exists
   * and is comparable with type `A`.
   */
  def nin[A](
      field: Witness.Lt[Symbol],
      value: Iterable[A]
    )(implicit
      /*@unused */ i0: MongoComparable[T, field.T, A],
      i1: BSONWriter[A]
    ): FilterBuilder[T] =
    unsafeFilter[Iterable[A]](field.value.name, f"$$nin", value)

  /**
   * Returns a builder with a typed `lt` clause.
   *
   * {{{
   * import reactivemongo.api.bson.builder.FilterBuilder
   *
   * case class Foo(priority: Int)
   *
   * val builder = FilterBuilder.empty[Foo]
   *
   * builder.lt(Symbol("priority"), 1)
   * }}}
   *
   * It is statically checked that field with such name exists
   * and is comparable with type `A`.
   */
  def lt[A](
      field: Witness.Lt[Symbol],
      value: A
    )(implicit
      /*@unused */ i0: MongoComparable[T, field.T, A],
      i1: BSONWriter[A],
      /*@unused*/ i2: FilterBuilder.Ordered[A]
    ): FilterBuilder[T] = unsafeFilter[A](field.value.name, f"$$lt", value)

  /**
   * Returns a builder with a typed `lte` clause.
   *
   * {{{
   * import reactivemongo.api.bson.builder.FilterBuilder
   *
   * case class Foo(priority: Int)
   *
   * val builder = FilterBuilder.empty[Foo]
   *
   * builder.lte(Symbol("priority"), 1)
   * }}}
   *
   * It is statically checked that field with such name exists
   * and is comparable with type `A`.
   */
  def lte[A](
      field: Witness.Lt[Symbol],
      value: A
    )(implicit
      /*@unused */ i0: MongoComparable[T, field.T, A],
      i1: BSONWriter[A],
      /*@unused*/ i2: FilterBuilder.Ordered[A]
    ): FilterBuilder[T] = unsafeFilter[A](field.value.name, f"$$lte", value)

  /**
   * Returns a builder with a typed `gt` clause.
   *
   * {{{
   * import reactivemongo.api.bson.builder.FilterBuilder
   *
   * case class Foo(priority: Int)
   *
   * val builder = FilterBuilder.empty[Foo]
   *
   * builder.gt(Symbol("priority"), 1)
   * }}}
   *
   * It is statically checked that field with such name exists
   * and is comparable with type `A`.
   */
  def gt[A](
      field: Witness.Lt[Symbol],
      value: A
    )(implicit
      /*@unused */ i0: MongoComparable[T, field.T, A],
      i1: BSONWriter[A],
      /*@unused*/ i2: FilterBuilder.Ordered[A]
    ): FilterBuilder[T] = unsafeFilter[A](field.value.name, f"$$gt", value)

  /**
   * Returns a builder with a typed `gte` clause.
   *
   * {{{
   * import reactivemongo.api.bson.builder.FilterBuilder
   *
   * case class Foo(priority: Int)
   *
   * val builder = FilterBuilder.empty[Foo]
   *
   * builder.gte(Symbol("priority"), 1)
   * }}}
   *
   * It is statically checked that field with such name exists
   * and is comparable with type `A`.
   */
  def gte[A](
      field: Witness.Lt[Symbol],
      value: A
    )(implicit
      /*@unused */ i0: MongoComparable[T, field.T, A],
      i1: BSONWriter[A],
      /*@unused*/ i2: FilterBuilder.Ordered[A]
    ): FilterBuilder[T] = unsafeFilter[A](field.value.name, f"$$gte", value)

  /**
   * Returns a builder with a typed `ne` clause.
   *
   * {{{
   * import reactivemongo.api.bson.builder.FilterBuilder
   *
   * case class Foo(id: String)
   *
   * val builder = FilterBuilder.empty[Foo]
   *
   * builder.ne(Symbol("id"), "value")
   * }}}
   *
   * It is statically checked that field with such name exists
   * and is comparable with type `A`.
   */
  def ne[A](
      field: Witness.Lt[Symbol],
      value: A
    )(implicit
      /*@unused */ i0: MongoComparable[T, field.T, A],
      i1: BSONWriter[A]
    ): FilterBuilder[T] = unsafeFilter[A](field.value.name, f"$$ne", value)

  /**
   * Returns a builder with a typed `exists` clause.
   *
   * {{{
   * import reactivemongo.api.bson.builder.FilterBuilder
   *
   * case class Foo(id: String)
   *
   * val builder = FilterBuilder.empty[Foo]
   *
   * builder.exists(Symbol("id"), true)
   * }}}
   */
  def exists[A](
      field: Witness.Lt[Symbol],
      value: Boolean
    )(implicit
      /*@unused */ i0: MongoComparable[T, field.T, A]
    ): FilterBuilder[T] =
    unsafeFilter[Boolean](field.value.name, f"$$exists", value)

  /**
   * Returns a builder with a typed `size` clause.
   *
   * {{{
   * import reactivemongo.api.bson.builder.FilterBuilder
   *
   * case class Foo(values: Seq[String])
   *
   * val builder = FilterBuilder.empty[Foo]
   *
   * builder.size(Symbol("values"), 0)
   * }}}
   */
  def size[A](
      field: Witness.Lt[Symbol],
      value: Int
    )(implicit
      /*@unused */ i0: MongoComparable[T, field.T, A]
    ): FilterBuilder[T] = unsafeFilter[Int](field.value.name, f"$$size", value)

  /**
   * Returns a builder with a typed `or` clause based on multiple input.
   *
   * {{{
   * import reactivemongo.api.bson.builder.FilterBuilder
   *
   * case class Foo(name: String)
   *
   * val builder = FilterBuilder.empty[Foo]
   *
   * builder.or(Seq("foo", "bar")) { (clauseBuilder, str) =>
   *   clauseBuilder.eq(Symbol("name"), str)
   * }
   * // => { $$or: [ { name: { $$eq: "foo" } }, { name: { $$eq: "bar" } } ] }
   * }}}
   *
   * It is statically checked that field with such name exists
   * and is comparable with type `A`.
   */
  def or[U](
      input: Iterable[U]
    )(f: (FilterBuilder[T], U) => FilterBuilder[T]
    ): FilterBuilder[T] = {
    val nested = new FilterBuilder[T](MMap.empty, prefix)
    val orClauses: Builder[BSONDocument, Seq[BSONDocument]] = Seq.newBuilder

    input.foreach { v =>
      val _ = f(nested, v)
      val clause = nested.clauses.result().toSeq

      clause match {
        case Seq(single) =>
          orClauses += BSONDocument(single)

        case _ =>
          orClauses += BSONDocument(f"$$and" -> BSONDocument(clause))
      }

      // !! Re-use private mutable
      nested.clauses.clear()
    }

    clauses += f"$$or" -> BSONArray(orClauses.result())

    this
  }

  /**
   * Returns an negating operation builder.
   *
   * {{{
   * import reactivemongo.api.bson.builder.FilterBuilder
   *
   * case class Foo(tags: Seq[String])
   *
   * val builder = FilterBuilder.empty[Foo]
   * builder.not(Symbol("tags")).apply { tagsBuilder =>
   *   tagsBuilder.ne("foo")
   * }
   * }}}
   *
   * It is statically checked that a field with specified name exists.
   */
  def not[A](
      field: Witness.Lt[Symbol]
    )(implicit
      /*@unused*/ i0: BsonPath.Exists[T, field.T, _]
    ): Function1[FilterBuilder.OperationBuilder[T, field.T] => FilterBuilder.OperationBuilder[T, field.T], FilterBuilder[T]] = {
    val addClause: BSONDocument => FilterBuilder[T] = { preparedOps =>
      val bsonPath = (self.prefix :+ field.value.name).mkString(".")

      self.clauses += bsonPath -> BSONDocument(f"$$not" -> preparedOps)

      self
    }

    def opBuilder = new FilterBuilder.OperationBuilder[T, field.T](addClause)

    _(opBuilder).result()
  }

  /**
   * Returns a builder combining multiple filters with `or` semantic.
   *
   * {{{
   * import reactivemongo.api.bson.builder.FilterBuilder
   *
   * case class Foo(id: String, counter: Int)
   *
   * val builder = FilterBuilder.empty[Foo]
   * builder.or(
   *   FilterBuilder.empty[Foo].eq(Symbol("id"), "value1"),
   *   FilterBuilder.empty[Foo].gte(Symbol("counter"), 10)
   * )
   * }}}
   */
  def or(f: FilterBuilder[T]*): FilterBuilder[T] = {
    val bsonClauses =
      f.flatMap(_.clauses.result()).map { case (k, v) => BSONDocument(k -> v) }

    clauses += f"$$or" -> BSONArray(bsonClauses)

    this
  }

  /**
   * Returns a nested builder for a field.
   *
   * {{{
   * import reactivemongo.api.bson.builder.FilterBuilder
   *
   * case class Bar(value: String)
   * case class Foo(bar: Bar)
   *
   * val builder = FilterBuilder.empty[Foo]
   * builder.nestedField[Bar](Symbol("bar")).at { barBuilder =>
   *   barBuilder.eq(Symbol("value"), "test")
   * }
   * }}}
   *
   * It is statically checked that field with such name exists
   * and is of type `A`.
   */
  def nestedField[A](
      field: Witness.Lt[Symbol]
    )(implicit
      /*@unused*/ i0: BsonPath.Lookup[T, field.T :: HNil, A]
    ): FilterBuilder.Nested[T, A] = {
    def in = new FilterBuilder[A](
      clauses,
      prefix = Seq(field.value.name)
    )

    new FilterBuilder.Nested[T, A](in, self)
  }

  /**
   * Provides a nested builder for a path.
   *
   * {{{
   * import reactivemongo.api.bson.builder.FilterBuilder
   *
   * case class Bar(value: String)
   * case class Foo(bar: Bar)
   *
   * val builder = FilterBuilder.empty[Foo]
   * builder.nested(Symbol("bar")).at { barValueBuilder =>
   *   barValueBuilder.eq(Symbol("value"), "test")
   * }
   * }}}
   *
   * It is statically checked that the path exists
   * and is of type `A`.
   */
  object nested extends SingletonProductArgs {

    def applyProduct[P <: HList, A](
        path: P
      )(implicit
        /* @unused */ i0: BsonPath.Lookup[T, P, A],
        i2: ToTraversable.Aux[P, List, Symbol]
      ): FilterBuilder.Nested[T, i0.Inner] = {
      def in = new FilterBuilder[i0.Inner](
        self.clauses,
        prefix = self.prefix ++ path.toList[Symbol].map(_.name)
      )

      new FilterBuilder.Nested[T, i0.Inner](in, self)
    }
  }
}
