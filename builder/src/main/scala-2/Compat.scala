package reactivemongo.api.bson.builder

import scala.reflect.macros.whitebox

import shapeless.Witness

private[builder] object Compat {
  type Sym = Witness.Lt[Symbol]

  def symbolImpl(
      c: whitebox.Context
    )(name: c.Expr[String]
    ): c.Expr[Witness.Lt[scala.Symbol]] = {
    import c.universe._

    name.tree match {
      case Literal(Constant(s: String)) => {
        c.Expr[Witness.Lt[scala.Symbol]](
          c typecheck q"_root_.shapeless.Witness.apply(${q"_root_.scala.Symbol(${s})"})"
        )
      }

      case _ =>
        c.abort(c.enclosingPosition, "symbol() requires a string literal")
    }
  }
}
