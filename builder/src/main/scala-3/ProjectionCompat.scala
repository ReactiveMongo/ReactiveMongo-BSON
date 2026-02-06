package reactivemongo.api.bson.builder

import scala.annotation.unused

import reactivemongo.api.bson.{ BSONInteger, BSONValue }

private[builder] trait ProjectionCompat[T] { self: ProjectionBuilder[T] =>

  /**
   * Returns a fully qualified field path for the given field name.
   *
   * This method constructs the complete dot-notation path for a field by combining
   * any existing prefix (for nested document projections) with the field name.
   *
   * @param field The field symbol
   * @return A `String` containing the fully qualified field path
   *
   * '''Examples:'''
   * - Without prefix: `"field_name"`
   * - With prefix `["parent"]`: `"parent.field_name"`
   * - With nested prefix `["root", "child"]`: `"root.child.field_name"`
   */
  private def fieldPath(field: String & Singleton): String = {
    val fieldName = field

    if (prefix.isEmpty) {
      fieldName
    } else {
      s"${prefix.mkString(".")}.$fieldName"
    }
  }

  /**
   * Projects a field with the specified inclusion/exclusion flag.
   *
   * This is a helper method that combines field path generation with clause addition.
   * It's used internally by both `includes()` and `excludes()` methods.
   *
   * @param field The field symbol
   * @param flag `true` to include the field (value 1), `false` to exclude (value 0)
   * @return A reference to this builder for method chaining
   */
  private def projectField[V](
      field: String & Singleton,
      flag: Boolean
    )(implicit
      @unused i0: BsonPath.Exists[T, field.type, V]
    ): ProjectionBuilder[T] = {
    val path = fieldPath(field)
    val value: BSONValue = BSONInteger(if (flag) 1 else 0)

    clauses += path -> value

    this
  }

  /**
   * Includes a field in the projection result.
   *
   * This method adds a field to the projection with a value of 1, indicating that
   * the field should be included in the query results. This is equivalent to
   * MongoDB's `{ field: 1 }` projection syntax.
   *
   * @tparam V The type of the field
   * @param field The field symbol to include
   * @return A reference to this builder for method chaining
   *
   * '''Example:'''
   * {{{
   * import reactivemongo.api.bson.builder.ProjectionBuilder
   *
   * case class User(name: String, email: String)
   *
   * val projection = ProjectionBuilder.empty[User]
   *   .includes("name")
   *   .includes("email")
   *   .result()
   * // Results in: { "name": 1, "email": 1 }
   * }}}
   */
  def includes[V](
      field: String & Singleton
    )(implicit
      i0: BsonPath.Exists[T, field.type, V]
    ): ProjectionBuilder[T] = projectField[V](field, true)

  /**
   * Excludes a field from the projection result.
   *
   * This method adds a field to the projection with a value of 0, indicating that
   * the field should be excluded from the query results. This is equivalent to
   * MongoDB's `{ field: 0 }` projection syntax.
   *
   * @tparam V The type of the field
   * @param field The field symbol to exclude
   * @return A reference to this builder for method chaining
   *
   * '''Example:'''
   * {{{
   * import reactivemongo.api.bson.builder.ProjectionBuilder
   *
   * case class User(name: String, email: String, password: String)
   *
   * val projection = ProjectionBuilder.empty[User]
   *   .excludes("password")
   *   .result()
   * // Results in: { "password": 0 }
   * }}}
   */
  def excludes[V](
      field: String & Singleton
    )(implicit
      i0: BsonPath.Exists[T, field.type, V]
    ): ProjectionBuilder[T] = projectField[V](field, false)

  /**
   * Projects a field using a custom MongoDB expression.
   *
   * This method allows you to specify complex MongoDB projection expressions
   * for a field, such as computed fields, conditional projections, or array
   * manipulations. The field path is specified as a string, so be careful
   * with spelling and ensure the field exists in your MongoDB documents.
   *
   * @param path The field path as a string (e.g., "name", "address.city")
   * @param expr A BSON expression defining how to project this field
   * @return A reference to this builder for method chaining
   */
  def project(path: String, expr: BSONValue): ProjectionBuilder[T] = {
    clauses += path -> expr
    this
  }

  /**
   * Projects elements from an array field based on their position.
   *
   * This method adds a projection clause that includes the element at the
   * specified position from an array field. It uses MongoDB's `$` positional
   * operator to achieve this.
   *
   * @tparam V The type of the array elements
   * @param field The field symbol representing the array
   * @return A reference to this builder for method chaining
   *
   * '''Example:'''
   * {{{
   * import reactivemongo.api.bson.builder.ProjectionBuilder
   *
   * case class User(name: String, tags: List[String])
   *
   * val projection = ProjectionBuilder.empty[User]
   *   .positional("tags")
   *   .result()
   * // Results in: { "tags.$": 1 }
   * }}}
   */
  def positional(
      field: String & Singleton
    )(implicit
      i0: BsonPath.Exists[T, field.type, _ <: Iterable[_]]
    ): ProjectionBuilder[T] = {
    clauses += (fieldPath(field) + f".$$") -> BSONInteger(1)
    this
  }

  /**
   * Performs projection on a single nested field.
   *
   * This method is a convenience wrapper for accessing a nested field
   * and applying projections within that field's context.
   *
   * @tparam V The type of the nested field
   * @tparam U The inner type (in case of Option)
   * @param field The field symbol
   * @param f A function that configures the projection on the nested structure
   * @return A reference to this builder for method chaining
   *
   * '''Example:'''
   * {{{
   * import reactivemongo.api.bson.builder.ProjectionBuilder
   *
   * case class Address(city: String, country: String)
   * case class User(name: String, address: Address)
   *
   * val projection = ProjectionBuilder.empty[User]
   *   .nestedField[Address, Address]("address").at { addressBuilder =>
   *     addressBuilder.includes("city")
   *   }
   *   .result()
   * // Results in: { "address.city": 1 }
   * }}}
   */
  def nestedField[V, U](
      field: String & Singleton
    )(implicit
      @unused i0: BsonPath.Lookup[T, field.type *: EmptyTuple, V],
      @unused i1: ProjectionBuilder.NestedType.Aux[V, U]
    ): ProjectionBuilder.NestedSingle[T, U] = {
    def in = new ProjectionBuilder[U](
      self.prefix :+ field,
      clauses
    )

    new ProjectionBuilder.NestedSingle[T, U](in, self)
  }

  /**
   * Performs nested field projection using a lookup function.
   *
   * This method enables projection on nested object fields by providing a way to
   * navigate into nested structures while maintaining compile-time field verification.
   * It's particularly useful for projecting fields within embedded documents or
   * complex nested structures.
   *
   * @tparam P The path type (Tuple of String & Singleton)
   * @param path The path to the nested field as varargs
   * @return A nested projection builder
   *
   * '''Example:'''
   * {{{
   * import reactivemongo.api.bson.builder.ProjectionBuilder
   *
   * case class Address(street: String, city: String)
   * case class User(name: String, address: Address)
   *
   * val projection = ProjectionBuilder.empty[User]
   *   .nested("address").at { nested =>
   *     nested.includes("city")
   *   }
   *   .result()
   * // Results in: { "address.city": 1 }
   * }}}
   */
  object nested {

    private def pathToSeq(path: Tuple): Seq[String] =
      path.productIterator.map(_.asInstanceOf[String]).toSeq

    def apply(
      )(using
        i0: BsonPath.Lookup[T, EmptyTuple, ?]
      ): ProjectionBuilder.Nested[T, i0.Inner] = {
      def in = new ProjectionBuilder[i0.Inner](self.prefix, clauses)
      new ProjectionBuilder.Nested[T, i0.Inner](in, self)
    }

    def apply[K1 <: String & Singleton](
        k1: K1
      )(using
        i0: BsonPath.Lookup[T, K1 *: EmptyTuple, ?]
      ): ProjectionBuilder.Nested[T, i0.Inner] = {
      val pathSeq = pathToSeq(k1 *: EmptyTuple)
      def in = new ProjectionBuilder[i0.Inner](self.prefix ++ pathSeq, clauses)
      new ProjectionBuilder.Nested[T, i0.Inner](in, self)
    }

    def apply[K1 <: String & Singleton, K2 <: String & Singleton](
        k1: K1,
        k2: K2
      )(using
        i0: BsonPath.Lookup[T, (K1, K2), ?]
      ): ProjectionBuilder.Nested[T, i0.Inner] = {
      val pathSeq = pathToSeq((k1, k2))
      def in = new ProjectionBuilder[i0.Inner](self.prefix ++ pathSeq, clauses)
      new ProjectionBuilder.Nested[T, i0.Inner](in, self)
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
      ): ProjectionBuilder.Nested[T, i0.Inner] = {
      val pathSeq = pathToSeq((k1, k2, k3))
      def in = new ProjectionBuilder[i0.Inner](self.prefix ++ pathSeq, clauses)
      new ProjectionBuilder.Nested[T, i0.Inner](in, self)
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
      ): ProjectionBuilder.Nested[T, i0.Inner] = {
      val pathSeq = pathToSeq((k1, k2, k3, k4))
      def in = new ProjectionBuilder[i0.Inner](self.prefix ++ pathSeq, clauses)
      new ProjectionBuilder.Nested[T, i0.Inner](in, self)
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
      ): ProjectionBuilder.Nested[T, i0.Inner] = {
      val pathSeq = pathToSeq((k1, k2, k3, k4, k5))
      def in = new ProjectionBuilder[i0.Inner](self.prefix ++ pathSeq, clauses)
      new ProjectionBuilder.Nested[T, i0.Inner](in, self)
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
      ): ProjectionBuilder.Nested[T, i0.Inner] = {
      val pathSeq = pathToSeq((k1, k2, k3, k4, k5, k6))
      def in = new ProjectionBuilder[i0.Inner](self.prefix ++ pathSeq, clauses)
      new ProjectionBuilder.Nested[T, i0.Inner](in, self)
    }

    def apply[
        K1 <: String & Singleton,
        K2 <: String & Singleton,
        K3 <: String & Singleton,
        K4 <: String & Singleton,
        K5 <: String & Singleton,
        K6 <: String & Singleton,
        K7 <: String & Singleton
      ](k1: K1,
        k2: K2,
        k3: K3,
        k4: K4,
        k5: K5,
        k6: K6,
        k7: K7
      )(using
        i0: BsonPath.Lookup[T, (K1, K2, K3, K4, K5, K6, K7), ?]
      ): ProjectionBuilder.Nested[T, i0.Inner] = {
      val pathSeq = pathToSeq((k1, k2, k3, k4, k5, k6, k7))
      def in = new ProjectionBuilder[i0.Inner](self.prefix ++ pathSeq, clauses)
      new ProjectionBuilder.Nested[T, i0.Inner](in, self)
    }

    def apply[
        K1 <: String & Singleton,
        K2 <: String & Singleton,
        K3 <: String & Singleton,
        K4 <: String & Singleton,
        K5 <: String & Singleton,
        K6 <: String & Singleton,
        K7 <: String & Singleton,
        K8 <: String & Singleton
      ](k1: K1,
        k2: K2,
        k3: K3,
        k4: K4,
        k5: K5,
        k6: K6,
        k7: K7,
        k8: K8
      )(using
        i0: BsonPath.Lookup[T, (K1, K2, K3, K4, K5, K6, K7, K8), ?]
      ): ProjectionBuilder.Nested[T, i0.Inner] = {
      val pathSeq = pathToSeq((k1, k2, k3, k4, k5, k6, k7, k8))
      def in = new ProjectionBuilder[i0.Inner](self.prefix ++ pathSeq, clauses)
      new ProjectionBuilder.Nested[T, i0.Inner](in, self)
    }

    def apply[
        K1 <: String & Singleton,
        K2 <: String & Singleton,
        K3 <: String & Singleton,
        K4 <: String & Singleton,
        K5 <: String & Singleton,
        K6 <: String & Singleton,
        K7 <: String & Singleton,
        K8 <: String & Singleton,
        K9 <: String & Singleton
      ](k1: K1,
        k2: K2,
        k3: K3,
        k4: K4,
        k5: K5,
        k6: K6,
        k7: K7,
        k8: K8,
        k9: K9
      )(using
        i0: BsonPath.Lookup[T, (K1, K2, K3, K4, K5, K6, K7, K8, K9), ?]
      ): ProjectionBuilder.Nested[T, i0.Inner] = {
      val pathSeq = pathToSeq((k1, k2, k3, k4, k5, k6, k7, k8, k9))
      def in = new ProjectionBuilder[i0.Inner](self.prefix ++ pathSeq, clauses)
      new ProjectionBuilder.Nested[T, i0.Inner](in, self)
    }

    def apply[
        K1 <: String & Singleton,
        K2 <: String & Singleton,
        K3 <: String & Singleton,
        K4 <: String & Singleton,
        K5 <: String & Singleton,
        K6 <: String & Singleton,
        K7 <: String & Singleton,
        K8 <: String & Singleton,
        K9 <: String & Singleton,
        K10 <: String & Singleton
      ](k1: K1,
        k2: K2,
        k3: K3,
        k4: K4,
        k5: K5,
        k6: K6,
        k7: K7,
        k8: K8,
        k9: K9,
        k10: K10
      )(using
        i0: BsonPath.Lookup[T, (K1, K2, K3, K4, K5, K6, K7, K8, K9, K10), ?]
      ): ProjectionBuilder.Nested[T, i0.Inner] = {
      val pathSeq = pathToSeq((k1, k2, k3, k4, k5, k6, k7, k8, k9, k10))
      def in = new ProjectionBuilder[i0.Inner](self.prefix ++ pathSeq, clauses)
      new ProjectionBuilder.Nested[T, i0.Inner](in, self)
    }
  }
}
