package reactivemongo.api

private[api] trait PackageCompat:

  /**
   * Keeps a `A` statement but raise a migration error at compile-time.
   *
   * The compilation error can be disabled by setting the system property
   * `reactivemongo.api.migrationRequired.nonFatal` to `true`.
   */
  inline def migrationRequired[A](inline details: String): A =
    ${ reactivemongo.api.bson.MacroImpl.migrationRequired[A]('details) }

end PackageCompat
