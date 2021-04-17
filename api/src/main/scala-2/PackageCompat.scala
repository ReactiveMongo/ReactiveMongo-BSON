package reactivemongo.api

private[api] trait PackageCompat {
  import language.experimental.macros

  /**
   * Keeps a `A` statement but raise a migration error at compile-time.
   *
   * The compilation error can be disabled by setting the system property
   * `reactivemongo.api.migrationRequired.nonFatal` to `true`.
   */
  @SuppressWarnings(Array("NullParameter", "UnusedMethodParameter"))
  def migrationRequired[A](details: String): A =
    macro reactivemongo.api.bson.MacroImpl.migrationRequired[A]
}
