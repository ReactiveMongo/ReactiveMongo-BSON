import reactivemongo.api.bson.Macros, Macros.Annotations.NoneAsNull

object MacroTest {
  case class Person(firstName: String, lastName: String)

  object Union {
    sealed trait UT
  }

  final class FooVal(val v: Int) extends AnyVal

  case class OptionalAsNull(name: String, @NoneAsNull value: Option[String])
  case class OptionalGeneric[T](v: Int, opt: Option[T])
}
