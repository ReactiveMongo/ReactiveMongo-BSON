package reactivemongo.api.bson.builder

import scala.util.Success

import reactivemongo.api.bson.{ BSONInteger, BSONValue }

import shapeless.{ ::, HList, HNil, SingletonProductArgs, Witness }
import shapeless.ops.hlist.ToTraversable

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
  private def fieldPath(field: Witness.Lt[Symbol]): String = {
    val fieldName = field.value.name

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
      field: Witness.Lt[Symbol],
      flag: Boolean
    )(implicit
      /* @unused */ i0: BsonPath.Exists[T, field.T, V]
    ): ProjectionBuilder[T] = {
    val path = fieldPath(field)
    val value: BSONValue = BSONInteger(if (flag) 1 else 0)

    clauses += path -> (() => Success(value))

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
   *   .includes(Symbol("name"))
   *   .includes(Symbol("email"))
   *   .result()
   * // Results in: { "name": 1, "email": 1 }
   * }}}
   */
  def includes[V](
      field: Witness.Lt[Symbol]
    )(implicit
      i0: BsonPath.Exists[T, field.T, V]
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
   *   .excludes(Symbol("password"))
   *   .result()
   * // Results in: { "password": 0 }
   * }}}
   */
  def excludes[V](
      field: Witness.Lt[Symbol]
    )(implicit
      i0: BsonPath.Exists[T, field.T, V]
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
  def project(path: String, expr: Expr.Opaque[T]): ProjectionBuilder[T] = {
    clauses += path -> expr.writes
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
   *   .positional(Symbol("tags"))
   *   .result()
   * // Results in: { "tags.$": 1 }
   * }}}
   */
  def positional(
      field: Witness.Lt[Symbol]
    )(implicit
      i0: BsonPath.Exists[T, field.T, _ <: Iterable[_]]
    ): ProjectionBuilder[T] = {
    clauses += (fieldPath(field) + f".$$") -> (() => Success(BSONInteger(1)))
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
   *   .nestedField[Address, Address](Symbol("address")).at { addressBuilder =>
   *     addressBuilder.includes(Symbol("city"))
   *   }
   *   .result()
   * // Results in: { "address.city": 1 }
   * }}}
   */
  def nestedField[V, U](
      field: Witness.Lt[Symbol]
    )(implicit
      /*@unused*/ i0: BsonPath.Lookup[T, field.T :: HNil, V],
      /* @unused */ i1: ProjectionBuilder.NestedType.Aux[V, U]
    ): ProjectionBuilder.NestedSingle[T, U] = {
    def in = new ProjectionBuilder[U](
      self.prefix :+ field.value.name,
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
   * @tparam P The path type (HList of Symbols)
   * @tparam U The type of the nested structure
   * @param path The path to the nested field
   * @param f A function that configures the projection on the nested structure
   * @return A reference to this builder for method chaining
   *
   * '''Example:'''
   * {{{
   * import reactivemongo.api.bson.builder.ProjectionBuilder
   *
   * case class Address(street: String, city: String)
   * case class User(name: String, address: Address)
   *
   * val projection = ProjectionBuilder.empty[User]
   *   .nested(Symbol("address")).at { nested =>
   *     nested.includes(Symbol("city"))
   *   }
   *   .result()
   * // Results in: { "address.city": 1 }
   * }}}
   */
  object nested extends SingletonProductArgs {

    def applyProduct[P <: HList, U](
        path: P
      )(implicit
        /* @unused */ i0: BsonPath.Lookup[T, P, U],
        i2: ToTraversable.Aux[P, List, Symbol]
      ): ProjectionBuilder.Nested[T, i0.Inner] = {
      def in = new ProjectionBuilder[i0.Inner](
        self.prefix ++ path.toList[Symbol].map(_.name),
        clauses
      )

      new ProjectionBuilder.Nested[T, i0.Inner](in, self)
    }
  }
}
