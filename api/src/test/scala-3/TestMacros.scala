package reactivemongo.api.bson

import scala.deriving.Mirror.ProductOf
import scala.quoted.*

object TestMacros:

  inline def testProductElements[T]: List[String] =
    ${ testProductElementsMacro[T] }

  def testProductElementsMacro[T: Type](using q: Quotes): Expr[List[String]] = {
    import q.reflect.*

    val helper = new MacroImpl.QuotesHelper {
      type Q = q.type
      val quotes = q
    }

    val tpe = TypeRepr.of[T]
    val names = helper.productOf(tpe) match {
      case Some(expr) =>
        helper.productElements(tpe, expr).map(_.toString)

      case _ =>
        List.empty[String]
    }

    Expr(names)
  }

  // ---

  inline def testWithTuple[T](pure: T): String =
    ${ testWithTupleMacro[T]('{ pure }) }

  def testWithTupleMacro[T: Type](
      pure: Expr[T]
    )(using
      q: Quotes
    ): Expr[String] = {
    import q.reflect.*

    val helper = new MacroImpl.QuotesHelper {
      type Q = q.type
      val quotes = q
    }

    val tpe = TypeRepr.of[T]
    val tpeElements = helper
      .productOf(tpe)
      .map {
        helper.productElements(tpe, _)
      }
      .get
    val types = tpeElements.map(_._2)

    val (tupleTpe, withTuple) = helper.withTuple(tpe, types)

    (withTuple(pure.asTerm) { (tupled: Term) =>
      val a = Expr(tupleTpe.show)

      '{
        $a + "/" + ${ tupled.asExpr }.toString
      }.asTerm
    }).asExprOf[String]
  }

  inline def testWithFields[T](pure: T): String =
    ${ testWithFieldsMacro[T]('{ pure }) }

  def testWithFieldsMacro[T: Type](
      pure: Expr[T]
    )(using
      q: Quotes
    ): Expr[String] = {
    import q.reflect.*

    val helper = new MacroImpl.QuotesHelper {
      type Q = q.type
      val quotes = q
    }

    val tpe = TypeRepr.of[T]
    val tpeElements = helper
      .productOf(tpe)
      .map {
        helper.productElements(tpe, _)
      }
      .get
    val types = tpeElements.map(_._2)

    val (tupleTpe, withTuple) = helper.withTuple(tpe, types)

    (withTuple(pure.asTerm) { (tupled: Term) =>
      val fieldMap =
        helper.withFields(tupled, tupleTpe, tpeElements, debug = _ => ())

      val strs: List[Expr[String]] = fieldMap.map {
        case (nme, withField) =>
          (withField { fi =>
            val n = Expr[String](nme)

            '{ $n + "=" + ${ fi.asExpr }.toString }.asTerm
          }).asExprOf[String]
      }.toList

      '{ ${ Expr ofList strs }.mkString(",") }.asTerm
    }).asExprOf[String]
  }

end TestMacros
