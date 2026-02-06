package reactivemongo.api.bson.builder

import scala.compiletime.testing.{ typeCheckErrors, Error }

import org.specs2.execute.{ TypecheckError, TypecheckSuccess, Typechecked }

object TestUtils {
  def resolver[T] = new Resolver[T]

  final class Resolver[T] {

    def apply[V, K <: String & Singleton](
        k: K,
        v: V
      )(implicit
        ev: MongoComparable[T, k.type, V]
      ) = {
      val _ = v
      ev
    }
  }

  transparent inline def symbol[T <: String & Singleton](inline name: T): T =
    name

  inline def typecheck(inline code: String): Typechecked =
    typeCheckErrors(code).headOption match {
      case Some(Error(msg, _, _, _)) =>
        Typechecked(code, TypecheckError(msg))

      case _ =>
        Typechecked(code, TypecheckSuccess)
    }
}
