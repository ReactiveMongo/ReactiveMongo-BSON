package reactivemongo.api.bson

import scala.quoted.{ Expr, Quotes, Type, quotes }

private[api] object MacroImpl:
  def migrationRequired[A: Type](details: Expr[String])(using Quotes): Expr[A] = {
    if (!sys.props.get("reactivemongo.api.migrationRequired.nonFatal").exists {
      v => v.toLowerCase == "true" || v.toLowerCase == "yes"
    }) {
      val q = quotes
      val msg: String = q.value(details) match {
        case Some(str) =>
          s"Migration required: $str"

        case _ => {
          q.reflect.report.warning(
            s"Invalid 'details' parameter for 'migrationRequired': ${q show details}")

          "Migration required"
        }
      }

      q.reflect.report.throwError(msg)
    }

    '{ scala.Predef.`???` }
  }
