package reactivemongo.api.bson.builder

import reactivemongo.api.bson.{
  BSONArray,
  BSONDocument,
  BSONInteger,
  BSONString,
  BSONValue,
  BSONWriter
}

import shapeless.{ ::, HList, HNil, SingletonProductArgs, Witness }
import shapeless.ops.hlist.ToTraversable

private[builder] trait UpdateCompat[T] { self: UpdateBuilder[T] =>

  /**
   * Sets the value of a field.
   *
   * Corresponds to MongoDB's `$$set` operator.
   *
   * {{{
   * import reactivemongo.api.bson.builder.UpdateBuilder
   *
   * case class Foo(name: String, age: Int)
   *
   * UpdateBuilder.empty[Foo]
   *   .set(Symbol("name"), "Alice")
   *   .set(Symbol("age"), 30)
   * }}}
   */
  def set[A](
      field: Witness.Lt[Symbol],
      value: A
    )(implicit
      /*@unused */ i0: MongoComparable[T, field.T, A],
      i1: BSONWriter[A]
    ): UpdateBuilder[T] = {
    val path = fieldPath(field.value.name)
    val doc = operations.getOrElse(f"$$set", BSONDocument.empty)

    operations += f"$$set" -> (doc ++ (path -> value))

    this
  }

  /**
   * Removes a field from the document.
   *
   * Corresponds to MongoDB's `$$unset` operator.
   *
   * {{{
   * import reactivemongo.api.bson.builder.UpdateBuilder
   *
   * case class Foo(tempField: Option[String])
   *
   * UpdateBuilder.empty[Foo]
   *   .unset(Symbol("tempField"))
   * }}}
   */
  def unset(
      field: Witness.Lt[Symbol]
    )(implicit
      /*@unused */ i0: BsonPath.Exists[T, field.T, _ <: Option[_]]
    ): UpdateBuilder[T] =
    addClause(f"$$unset", field.value.name, BSONInteger(1))

  /**
   * Increments a numeric field by the specified amount.
   *
   * Corresponds to MongoDB's `$$inc` operator.
   *
   * {{{
   * import reactivemongo.api.bson.builder.UpdateBuilder
   *
   * case class Stats(count: Int, views: Long)
   *
   * UpdateBuilder.empty[Stats]
   *   .inc(Symbol("count"), 1)
   *   .inc(Symbol("views"), 100L)
   * }}}
   */
  def inc[A](
      field: Witness.Lt[Symbol],
      value: A
    )(implicit
      /*@unused */ i0: MongoComparable[T, field.T, A],
      /*@unused */ i1: IsNumeric[A],
      i2: BSONWriter[A]
    ): UpdateBuilder[T] = {
    val path = fieldPath(field.value.name)
    val doc = operations.getOrElse(f"$$inc", BSONDocument.empty)

    operations += f"$$inc" -> (doc ++ (path -> value))

    this
  }

  /**
   * Multiplies a numeric field by the specified amount.
   *
   * Corresponds to MongoDB's `$$mul` operator.
   *
   * {{{
   * import reactivemongo.api.bson.builder.UpdateBuilder
   *
   * case class Product(price: Double, quantity: Int)
   *
   * UpdateBuilder.empty[Product]
   *   .mul(Symbol("price"), 1.1)
   *   .mul(Symbol("quantity"), 2)
   * }}}
   */
  def mul[A](
      field: Witness.Lt[Symbol],
      value: A
    )(implicit
      /*@unused */ i0: MongoComparable[T, field.T, A],
      /*@unused */ i1: IsNumeric[A],
      i2: BSONWriter[A]
    ): UpdateBuilder[T] = {
    val path = fieldPath(field.value.name)
    val doc = operations.getOrElse(f"$$mul", BSONDocument.empty)

    operations += f"$$mul" -> (doc ++ (path -> value))

    this
  }

  /**
   * Updates a field only if the specified value is greater than the existing value.
   *
   * Corresponds to MongoDB's `$$max` operator.
   *
   * {{{
   * import reactivemongo.api.bson.builder.UpdateBuilder
   *
   * case class HighScore(bestScore: Int)
   *
   * UpdateBuilder.empty[HighScore]
   *   .max(Symbol("bestScore"), 1500)
   * }}}
   */
  def max[A](
      field: Witness.Lt[Symbol],
      value: A
    )(implicit
      /*@unused */ i0: MongoComparable[T, field.T, A],
      i1: BSONWriter[A]
    ): UpdateBuilder[T] = {
    val path = fieldPath(field.value.name)
    val doc = operations.getOrElse(f"$$max", BSONDocument.empty)

    operations += f"$$max" -> (doc ++ (path -> value))

    this
  }

  /**
   * Updates a field only if the specified value is less than the existing value.
   *
   * Corresponds to MongoDB's `$$min` operator.
   *
   * {{{
   * import reactivemongo.api.bson.builder.UpdateBuilder
   *
   * case class Pricing(lowestPrice: Double)
   *
   * UpdateBuilder.empty[Pricing]
   *   .min(Symbol("lowestPrice"), 29.99)
   * }}}
   */
  def min[A](
      field: Witness.Lt[Symbol],
      value: A
    )(implicit
      /*@unused */ i0: MongoComparable[T, field.T, A],
      i1: BSONWriter[A]
    ): UpdateBuilder[T] = {
    val path = fieldPath(field.value.name)
    val doc = operations.getOrElse(f"$$min", BSONDocument.empty)

    operations += f"$$min" -> (doc ++ (path -> value))

    this
  }

  /**
   * Renames a field.
   *
   * Corresponds to MongoDB's `$$rename` operator.
   *
   * {{{
   * import reactivemongo.api.bson.builder.UpdateBuilder
   *
   * case class Document(oldName: String)
   *
   * UpdateBuilder.empty[Document]
   *   .rename(Symbol("oldName"), "newName")
   * }}}
   */
  def rename[A](
      field: Witness.Lt[Symbol],
      newName: String
    )(implicit
      /*@unused */ i0: BsonPath.Exists[T, field.T, A]
    ): UpdateBuilder[T] =
    addClause(f"$$rename", field.value.name, BSONString(newName))

  /**
   * Sets a field to the current date.
   *
   * Corresponds to MongoDB's `$$currentDate` operator.
   *
   * {{{
   * import reactivemongo.api.bson.builder.UpdateBuilder
   *
   * case class Timestamps(lastModified: java.time.Instant)
   *
   * UpdateBuilder.empty[Timestamps]
   *   .currentDate(Symbol("lastModified"))
   * }}}
   */
  def currentDate[A](
      field: Witness.Lt[Symbol],
      dateType: UpdateBuilder.CurrentDateType =
        UpdateBuilder.CurrentDateType.Date
    )(implicit
      /*@unused */ i0: BsonPath.Exists[
        T,
        field.T,
        A
      ],
      /* @unused */ i1: Temporal[A]
    ): UpdateBuilder[T] = {
    val value = dateType match {
      case UpdateBuilder.CurrentDateType.Date =>
        BSONDocument(f"$$type" -> "date")

      case UpdateBuilder.CurrentDateType.Timestamp =>
        BSONDocument(f"$$type" -> "timestamp")
    }

    addClause(f"$$currentDate", field.value.name, value)
  }

  /**
   * Adds a value to an array only if it doesn't already exist.
   *
   * Corresponds to MongoDB's `$$addToSet` operator.
   *
   * {{{
   * import reactivemongo.api.bson.builder.UpdateBuilder
   *
   * case class Article(tags: Seq[String])
   *
   * UpdateBuilder.empty[Article]
   *   .addToSet(Symbol("tags"), "scala")
   * }}}
   */
  def addToSet[A](
      field: Witness.Lt[Symbol],
      value: A
    )(implicit
      /*@unused */ i0: MongoComparable[T, field.T, A],
      i1: BSONWriter[A]
    ): UpdateBuilder[T] = {
    val path = fieldPath(field.value.name)
    val doc = operations.getOrElse(f"$$addToSet", BSONDocument.empty)

    operations += f"$$addToSet" -> (doc ++ (path -> value))

    this
  }

  /**
   * Adds multiple values to an array only if they don't already exist.
   *
   * Corresponds to MongoDB's `$$addToSet` with `$$each` modifier.
   *
   * {{{
   * import reactivemongo.api.bson.builder.UpdateBuilder
   *
   * case class Article(tags: Seq[String])
   *
   * UpdateBuilder.empty[Article]
   *   .addToSetEach(Symbol("tags"), Seq("scala", "mongodb"))
   * }}}
   */
  def addToSetEach[A](
      field: Witness.Lt[Symbol],
      values: Iterable[A]
    )(implicit
      /*@unused */ i0: MongoComparable[T, field.T, A],
      i1: BSONWriter[A]
    ): UpdateBuilder[T] = {
    val bsonValues = BSONArray(
      values.toSeq.map(v => BSONValue.valueProducer(v)(i1)): _*
    )

    val eachDoc = BSONDocument(f"$$each" -> bsonValues)
    val path = fieldPath(field.value.name)
    val doc = operations.getOrElse(f"$$addToSet", BSONDocument.empty)

    operations += f"$$addToSet" -> (doc ++ BSONDocument(path -> eachDoc))

    this
  }

  /**
   * Removes the first or last element from an array.
   *
   * Corresponds to MongoDB's `$$pop` operator.
   *
   * {{{
   * import reactivemongo.api.bson.builder.UpdateBuilder
   *
   * case class Queue(items: Seq[String])
   *
   * UpdateBuilder.empty[Queue]
   *   .pop(Symbol("items"), UpdateBuilder.PopStrategy.First)
   * }}}
   */
  def pop(
      field: Witness.Lt[Symbol],
      strategy: UpdateBuilder.PopStrategy = UpdateBuilder.PopStrategy.Last
    )(implicit
      /*@unused */ i0: BsonPath.Exists[T, field.T, _ <: Iterable[_]]
    ): UpdateBuilder[T] = {
    val value = strategy match {
      case UpdateBuilder.PopStrategy.First => BSONInteger(-1)
      case UpdateBuilder.PopStrategy.Last  => BSONInteger(1)
    }

    addClause(f"$$pop", field.value.name, value)
  }

  /**
   * Removes all array elements that match a condition.
   *
   * Corresponds to MongoDB's `$$pull` operator.
   *
   * {{{
   * import reactivemongo.api.bson.builder.UpdateBuilder
   *
   * case class Content(scores: Seq[Int])
   *
   * UpdateBuilder.empty[Content]
   *   .pull(Symbol("scores"), 0)
   * }}}
   */
  def pull[A](
      field: Witness.Lt[Symbol],
      value: A
    )(implicit
      /*@unused */ i0: MongoComparable[T, field.T, A],
      i1: BSONWriter[A]
    ): UpdateBuilder[T] = {
    val path = fieldPath(field.value.name)
    val doc = operations.getOrElse(f"$$pull", BSONDocument.empty)
    operations += f"$$pull" -> (doc ++ (path -> value))
    this
  }

  /**
   * Removes all array elements that match a BSON condition.
   *
   * Corresponds to MongoDB's `$$pull` operator with query conditions.
   *
   * {{{
   * import reactivemongo.api.bson.BSONDocument
   * import reactivemongo.api.bson.builder.UpdateBuilder
   *
   * case class Inventory(items: Seq[BSONDocument])
   *
   * UpdateBuilder.empty[Inventory]
   *   .pullExpr(Symbol("items"), BSONDocument("quantity" -> BSONDocument(f"$$lt" -> 5)))
   * }}}
   */
  def pullExpr[A](
      field: Witness.Lt[Symbol],
      condition: BSONDocument
    )(implicit
      /*@unused */ i0: MongoComparable[T, field.T, A]
    ): UpdateBuilder[T] =
    addClause(f"$$pull", field.value.name, condition)

  /**
   * Removes all instances of specified values from an array.
   *
   * Corresponds to MongoDB's `$$pullAll` operator.
   *
   * {{{
   * import reactivemongo.api.bson.builder.UpdateBuilder
   *
   * case class Lists(tags: Seq[String])
   *
   * UpdateBuilder.empty[Lists]
   *   .pullAll(Symbol("tags"), Seq("old", "deprecated"))
   * }}}
   */
  def pullAll[A](
      field: Witness.Lt[Symbol],
      values: Iterable[A]
    )(implicit
      /*@unused */ i0: MongoComparable[T, field.T, A],
      i1: BSONWriter[A]
    ): UpdateBuilder[T] = {
    val bsonValues = BSONArray(
      values.toSeq.map(v => BSONValue.valueProducer(v)(i1)): _*
    )
    val path = fieldPath(field.value.name)
    val doc = operations.getOrElse(f"$$pullAll", BSONDocument.empty)

    operations += f"$$pullAll" -> (doc ++ BSONDocument(path -> bsonValues))

    this
  }

  /**
   * Appends a value to an array.
   *
   * Corresponds to MongoDB's `$$push` operator.
   *
   * {{{
   * import reactivemongo.api.bson.builder.UpdateBuilder
   *
   * case class Logging(logs: Seq[String])
   *
   * UpdateBuilder.empty[Logging]
   *   .push(Symbol("logs"), "User logged in")
   * }}}
   */
  def push[A](
      field: Witness.Lt[Symbol],
      value: A
    )(implicit
      /*@unused */ i0: MongoComparable[T, field.T, A],
      i1: BSONWriter[A]
    ): UpdateBuilder[T] = {
    val path = fieldPath(field.value.name)
    val doc = operations.getOrElse(f"$$push", BSONDocument.empty)
    operations += f"$$push" -> (doc ++ (path -> value))
    this
  }

  /**
   * Appends multiple values to an array with optional modifiers.
   *
   * Corresponds to MongoDB's `$$push` with `$$each` modifier.
   *
   * {{{
   * import reactivemongo.api.bson.builder.UpdateBuilder
   *
   * case class GameData(scores: Seq[Int])
   *
   * UpdateBuilder.empty[GameData]
   *   .pushEach(Symbol("scores"), Seq(100, 200, 300))
   * }}}
   */
  def pushEach[A](
      field: Witness.Lt[Symbol],
      values: Iterable[A],
      slice: Option[UpdateBuilder.PushSlice] = None,
      sort: Option[UpdateBuilder.PushSort] = None,
      position: Option[Int] = None
    )(implicit
      /*@unused */ i0: MongoComparable[T, field.T, A],
      i1: BSONWriter[A]
    ): UpdateBuilder[T] = {
    val bsonValues = BSONArray(
      values.toSeq.map(v => BSONValue.valueProducer(v)(i1)): _*
    )

    var doc = BSONDocument(f"$$each" -> bsonValues)

    slice.foreach {
      case UpdateBuilder.PushSlice.Empty =>
        doc = doc ++ (f"$$slice" -> BSONInteger(0))

      case UpdateBuilder.PushSlice.First(n) =>
        doc = doc ++ (f"$$slice" -> BSONInteger(n))

      case UpdateBuilder.PushSlice.Last(n) =>
        doc = doc ++ (f"$$slice" -> BSONInteger(-n))
    }

    sort.foreach {
      case UpdateBuilder.PushSort.Ascending =>
        doc = doc ++ (f"$$sort" -> BSONInteger(1))

      case UpdateBuilder.PushSort.Descending =>
        doc = doc ++ (f"$$sort" -> BSONInteger(-1))

      case UpdateBuilder.PushSort.Document(sortDoc) =>
        doc = doc ++ (f"$$sort" -> sortDoc)
    }

    position.foreach { pos => doc = doc ++ (f"$$position" -> BSONInteger(pos)) }

    addClause(f"$$push", field.value.name, doc)
  }

  /**
   * Conditionally applies an update operation if the value is defined.
   *
   * {{{
   * import reactivemongo.api.bson.builder.UpdateBuilder
   *
   * case class Foo(email: Option[String])
   *
   * val maybeEmail: Option[String] = Some("user@example.com")
   *
   * UpdateBuilder.empty[Foo]
   *   .ifSome(maybeEmail) { (builder, email) =>
   *     builder.set(Symbol("email"), email)
   *   }
   * }}}
   */
  def ifSome[A](
      value: Option[A]
    )(f: (UpdateBuilder[T], A) => UpdateBuilder[T]
    ): UpdateBuilder[T] =
    value.fold(this)(v => f(this, v))

  /**
   * Performs an untyped update operation (escape hatch).
   *
   * Warning: Bypasses type safety. Use with caution.
   *
   * {{{
   * import reactivemongo.api.bson.BSONString
   * import reactivemongo.api.bson.builder.UpdateBuilder
   *
   * case class Foo(name: String)
   *
   * UpdateBuilder.empty[Foo]
   *   .untyped(Symbol("name"), f"$$set", BSONString("value"))
   * }}}
   */
  def untyped[A](
      field: Witness.Lt[Symbol],
      operation: String,
      value: BSONValue
    )(implicit
      /*@unused */ i0: MongoComparable[T, field.T, A]
    ): UpdateBuilder[T] =
    addClause(operation, field.value.name, value)

  /**
   * Performs nested field updates for a single field.
   *
   * {{{
   * import reactivemongo.api.bson.builder.UpdateBuilder
   *
   * case class Bar(city: String, zipCode: String)
   * case class Foo(name: String, bar: Bar)
   *
   * UpdateBuilder.empty[Foo]
   *   .nestedField(Symbol("bar")).at { bar =>
   *     bar.set(Symbol("city"), "Boston")
   *   }
   * }}}
   *
   * It is statically checked that field with such name exists
   * and is of type `A`.
   */
  def nestedField[A](
      field: Witness.Lt[Symbol]
    )(implicit
      /*@unused*/ i0: BsonPath.Lookup[T, field.T :: HNil, A]
    ): UpdateBuilder.Nested[T, A] = {
    def in = new UpdateBuilder[A](
      operations,
      prefix = self.prefix :+ field.value.name
    )

    new UpdateBuilder.Nested[T, A](in, self)
  }

  /**
   * Performs nested field updates for a path.
   *
   * {{{
   * import reactivemongo.api.bson.builder.UpdateBuilder
   *
   * case class Baz(value: Int)
   * case class Bar(city: String, baz: Baz)
   * case class Foo(name: String, bar: Bar)
   *
   * UpdateBuilder.empty[Foo]
   *   .nested(Symbol("bar"), Symbol("baz")).at { baz =>
   *     baz.set(Symbol("value"), 42)
   *   }
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
      ): UpdateBuilder.Nested[T, i0.Inner] = {
      def in = new UpdateBuilder[i0.Inner](
        operations,
        prefix = self.prefix ++ path.toList[Symbol].map(_.name)
      )

      new UpdateBuilder.Nested[T, i0.Inner](in, self)
    }
  }
}
