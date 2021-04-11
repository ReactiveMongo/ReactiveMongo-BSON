package reactivemongo.api.bson

import scala.compiletime.testing.{ Error, typeCheckErrors }

import org.specs2.execute.{ Typechecked, TypecheckError, TypecheckSuccess }

object TestUtils:
  inline def typecheck(inline code: String): Typechecked =
    typeCheckErrors(code).headOption match {
      case Some(Error(msg, _, _, _)) =>
        Typechecked(code, TypecheckError(msg))

      case _ =>
        Typechecked(code, TypecheckSuccess)
    }
