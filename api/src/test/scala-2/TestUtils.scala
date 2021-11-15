package reactivemongo.api.bson

import scala.language.experimental.macros

import org.specs2.execute.{ Typecheck, Typechecked }

object TestUtils {

  def typecheck(code: String): Typechecked =
    macro Typecheck.typecheckImpl
}
