package reactivemongo.api.bson

import scala.quoted.{ Expr, Quotes, Type, quotes }

private[api] object MacroImpl:
  def documentClass[A: Type](using q: Quotes): Expr[DocumentClass[A]] = {
    import q.reflect.*

    val anyValTpe = TypeRepr.of[AnyVal]
    val bsonValueTpe = TypeRepr.of[BSONValue]
    val bsonDocTpe = TypeRepr.of[BSONDocument]
    val aTpe = TypeRepr.of[A].dealias

    def throwNotDoc =
      report.throwError(s"Type ${aTpe.show} is not a document one")

    if (aTpe <:< bsonValueTpe) {
      if (aTpe <:< bsonDocTpe) '{
        DocumentClass.unchecked[A]
      } else {
        throwNotDoc
      }
    } else aTpe.classSymbol match {
      case Some(tpeSym) => {
        if (
          (tpeSym.flags.is(Flags.Abstract) && !(aTpe <:< anyValTpe)) ||
            tpeSym.flags.is(Flags.Trait) ||
            tpeSym.companionClass.declaredMethod("unapply").nonEmpty) '{
          DocumentClass.unchecked[A]
        } else {
          throwNotDoc
        }
      }

      case _ =>
        throwNotDoc
    }
  }

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
