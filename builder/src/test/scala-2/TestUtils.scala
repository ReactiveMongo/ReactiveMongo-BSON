package reactivemongo.api.bson.builder

import org.specs2.execute.{ Typecheck, Typechecked }

import shapeless.Witness

object TestUtils {
  import scala.language.experimental.macros

  def symbol(name: String): Witness.Lt[Symbol] = macro Compat.symbolImpl

  def resolve[T, V](
      k: Witness.Lt[Symbol],
      v: V
    )(implicit
      ev: MongoComparable[T, k.T, V]
    ) = {
    val _ = v
    ev
  }

  def resolver[T] = new Resolver[T]

  final class Resolver[T] {

    def apply[V](
        k: Witness.Lt[Symbol],
        v: V
      )(implicit
        ev: MongoComparable[T, k.T, V]
      ) = {
      val _ = v
      ev
    }
  }

  def typecheck(code: String): Typechecked =
    macro Typecheck.typecheckImpl
}
