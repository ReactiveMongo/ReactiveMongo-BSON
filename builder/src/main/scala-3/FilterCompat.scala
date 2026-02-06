package reactivemongo.api.bson.builder

import scala.collection.mutable.{ Builder, Map as MMap }

import scala.annotation.unused

import reactivemongo.api.bson.{ BSONArray, BSONDocument, BSONWriter }

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
   * builder.eq("id", "value")
   * }}}
   *
   * It is statically checked that field with such name exists
   * and is comparable with type `A`.
   */
  def eq[A](
      field: String & Singleton,
      value: A
    )(implicit
      @unused i0: MongoComparable[T, field.type, A],
      i1: BSONWriter[A]
    ): FilterBuilder[T] = unsafeFilter[A](field, f"$$eq", value)

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
   * builder.in("name", Seq("value"))
   * }}}
   *
   * It is statically checked that field with such name exists
   * and is comparable with type `A`.
   */
  def in[A](
      field: String & Singleton,
      value: Iterable[A]
    )(implicit
      @unused i0: MongoComparable[T, field.type, A],
      i1: BSONWriter[A]
    ): FilterBuilder[T] =
    unsafeFilter[Iterable[A]](field, f"$$in", value)

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
   * builder.nin("name", Seq("value"))
   * }}}
   *
   * It is statically checked that field with such name exists
   * and is comparable with type `A`.
   */
  def nin[A](
      field: String & Singleton,
      value: Iterable[A]
    )(implicit
      @unused i0: MongoComparable[T, field.type, A],
      i1: BSONWriter[A]
    ): FilterBuilder[T] =
    unsafeFilter[Iterable[A]](field, f"$$nin", value)

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
   * builder.lt("priority", 1)
   * }}}
   *
   * It is statically checked that field with such name exists
   * and is comparable with type `A`.
   */
  def lt[A](
      field: String & Singleton,
      value: A
    )(implicit
      @unused i0: MongoComparable[T, field.type, A],
      i1: BSONWriter[A],
      @unused i2: FilterBuilder.Ordered[A]
    ): FilterBuilder[T] = unsafeFilter[A](field, f"$$lt", value)

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
   * builder.lte("priority", 1)
   * }}}
   *
   * It is statically checked that field with such name exists
   * and is comparable with type `A`.
   */
  def lte[A](
      field: String & Singleton,
      value: A
    )(implicit
      @unused i0: MongoComparable[T, field.type, A],
      i1: BSONWriter[A],
      @unused i2: FilterBuilder.Ordered[A]
    ): FilterBuilder[T] = unsafeFilter[A](field, f"$$lte", value)

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
   * builder.gt("priority", 1)
   * }}}
   *
   * It is statically checked that field with such name exists
   * and is comparable with type `A`.
   */
  def gt[A](
      field: String & Singleton,
      value: A
    )(implicit
      @unused i0: MongoComparable[T, field.type, A],
      i1: BSONWriter[A],
      @unused i2: FilterBuilder.Ordered[A]
    ): FilterBuilder[T] = unsafeFilter[A](field, f"$$gt", value)

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
   * builder.gte("priority", 1)
   * }}}
   *
   * It is statically checked that field with such name exists
   * and is comparable with type `A`.
   */
  def gte[A](
      field: String & Singleton,
      value: A
    )(implicit
      @unused i0: MongoComparable[T, field.type, A],
      i1: BSONWriter[A],
      @unused i2: FilterBuilder.Ordered[A]
    ): FilterBuilder[T] = unsafeFilter[A](field, f"$$gte", value)

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
   * builder.ne("id", "value")
   * }}}
   *
   * It is statically checked that field with such name exists
   * and is comparable with type `A`.
   */
  def ne[A](
      field: String & Singleton,
      value: A
    )(implicit
      @unused i0: MongoComparable[T, field.type, A],
      i1: BSONWriter[A]
    ): FilterBuilder[T] = unsafeFilter[A](field, f"$$ne", value)

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
   * builder.exists("id", true)
   * }}}
   */
  def exists[A](
      field: String & Singleton,
      value: Boolean
    )(implicit
      @unused i0: MongoComparable[T, field.type, A]
    ): FilterBuilder[T] =
    unsafeFilter[Boolean](field, f"$$exists", value)

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
   * builder.size("values", 0)
   * }}}
   */
  def size[A](
      field: String & Singleton,
      value: Int
    )(implicit
      @unused i0: MongoComparable[T, field.type, A]
    ): FilterBuilder[T] = unsafeFilter[Int](field, f"$$size", value)

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
   *   clauseBuilder.eq("name", str)
   * }
   * // => { $or: [ { name: { $eq: "foo" } }, { name: { $eq: "bar" } } ] }
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
   * builder.not("tags").apply { tagsBuilder =>
   *   tagsBuilder.ne("foo")
   * }
   * }}}
   *
   * It is statically checked that a field with specified name exists.
   */
  def not[A](
      field: String & Singleton
    )(implicit
      @unused i0: BsonPath.Exists[T, field.type, _]
    ): Function1[FilterBuilder.OperationBuilder[T, field.type] => FilterBuilder.OperationBuilder[T, field.type], FilterBuilder[T]] = {
    val addClause: BSONDocument => FilterBuilder[T] = { preparedOps =>
      val bsonPath = (self.prefix :+ field).mkString(".")

      self.clauses += bsonPath -> BSONDocument(f"$$not" -> preparedOps)

      self
    }

    def opBuilder = new FilterBuilder.OperationBuilder[T, field.type](addClause)

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
   *   FilterBuilder.empty[Foo].eq("id", "value1"),
   *   FilterBuilder.empty[Foo].gte("counter", 10)
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
   * builder.nestedField[Bar]("bar").at { barBuilder =>
   *   barBuilder.eq("value", "test")
   * }
   * }}}
   *
   * It is statically checked that field with such name exists
   * and is of type `A`.
   */
  def nestedField[A](
      field: String & Singleton
    )(implicit
      @unused i0: BsonPath.Lookup[T, field.type *: EmptyTuple, A]
    ): FilterBuilder.Nested[T, A] = {
    def in = new FilterBuilder[A](
      clauses,
      prefix = Seq(field)
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
   * builder.nested("bar").at { barValueBuilder =>
   *   barValueBuilder.eq("value", "test")
   * }
   * }}}
   *
   * It is statically checked that the path exists
   * and is of type `A`.
   */
  object nested {

    private def pathToSeq(path: Tuple): Seq[String] =
      path.productIterator.map(_.asInstanceOf[String]).toSeq

    def apply(
      )(using
        i0: BsonPath.Lookup[T, EmptyTuple, ?]
      ): FilterBuilder.Nested[T, i0.Inner] = {
      def in = new FilterBuilder[i0.Inner](self.clauses, prefix = self.prefix)
      new FilterBuilder.Nested[T, i0.Inner](in, self)
    }

    def apply[K1 <: String & Singleton](
        k1: K1
      )(using
        i0: BsonPath.Lookup[T, K1 *: EmptyTuple, ?]
      ): FilterBuilder.Nested[T, i0.Inner] = {
      val pathSeq = pathToSeq(k1 *: EmptyTuple)
      def in = new FilterBuilder[i0.Inner](
        self.clauses,
        prefix = self.prefix ++ pathSeq
      )
      new FilterBuilder.Nested[T, i0.Inner](in, self)
    }

    def apply[K1 <: String & Singleton, K2 <: String & Singleton](
        k1: K1,
        k2: K2
      )(using
        i0: BsonPath.Lookup[T, (K1, K2), ?]
      ): FilterBuilder.Nested[T, i0.Inner] = {
      val pathSeq = pathToSeq((k1, k2))
      def in = new FilterBuilder[i0.Inner](
        self.clauses,
        prefix = self.prefix ++ pathSeq
      )
      new FilterBuilder.Nested[T, i0.Inner](in, self)
    }

    def apply[
        K1 <: String & Singleton,
        K2 <: String & Singleton,
        K3 <: String & Singleton
      ](k1: K1,
        k2: K2,
        k3: K3
      )(using
        i0: BsonPath.Lookup[T, (K1, K2, K3), ?]
      ): FilterBuilder.Nested[T, i0.Inner] = {
      val pathSeq = pathToSeq((k1, k2, k3))
      def in = new FilterBuilder[i0.Inner](
        self.clauses,
        prefix = self.prefix ++ pathSeq
      )
      new FilterBuilder.Nested[T, i0.Inner](in, self)
    }

    def apply[
        K1 <: String & Singleton,
        K2 <: String & Singleton,
        K3 <: String & Singleton,
        K4 <: String & Singleton
      ](k1: K1,
        k2: K2,
        k3: K3,
        k4: K4
      )(using
        i0: BsonPath.Lookup[T, (K1, K2, K3, K4), ?]
      ): FilterBuilder.Nested[T, i0.Inner] = {
      val pathSeq = pathToSeq((k1, k2, k3, k4))
      def in = new FilterBuilder[i0.Inner](
        self.clauses,
        prefix = self.prefix ++ pathSeq
      )
      new FilterBuilder.Nested[T, i0.Inner](in, self)
    }

    def apply[
        K1 <: String & Singleton,
        K2 <: String & Singleton,
        K3 <: String & Singleton,
        K4 <: String & Singleton,
        K5 <: String & Singleton
      ](k1: K1,
        k2: K2,
        k3: K3,
        k4: K4,
        k5: K5
      )(using
        i0: BsonPath.Lookup[T, (K1, K2, K3, K4, K5), ?]
      ): FilterBuilder.Nested[T, i0.Inner] = {
      val pathSeq = pathToSeq((k1, k2, k3, k4, k5))
      def in = new FilterBuilder[i0.Inner](
        self.clauses,
        prefix = self.prefix ++ pathSeq
      )
      new FilterBuilder.Nested[T, i0.Inner](in, self)
    }

    def apply[
        K1 <: String & Singleton,
        K2 <: String & Singleton,
        K3 <: String & Singleton,
        K4 <: String & Singleton,
        K5 <: String & Singleton,
        K6 <: String & Singleton
      ](k1: K1,
        k2: K2,
        k3: K3,
        k4: K4,
        k5: K5,
        k6: K6
      )(using
        i0: BsonPath.Lookup[T, (K1, K2, K3, K4, K5, K6), ?]
      ): FilterBuilder.Nested[T, i0.Inner] = {
      val pathSeq = pathToSeq((k1, k2, k3, k4, k5, k6))
      def in = new FilterBuilder[i0.Inner](
        self.clauses,
        prefix = self.prefix ++ pathSeq
      )
      new FilterBuilder.Nested[T, i0.Inner](in, self)
    }
  }
}
