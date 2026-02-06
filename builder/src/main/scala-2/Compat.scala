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
      case Literal(Constant(s: String)) =>
        val symTree = q"_root_.scala.Symbol(${s})"
        val witnessTree = q"_root_.shapeless.Witness.apply($symTree)"
        c.Expr[Witness.Lt[scala.Symbol]](c.typecheck(witnessTree))

      case _ =>
        c.abort(c.enclosingPosition, "symbol() requires a string literal")
    }
  }
}
