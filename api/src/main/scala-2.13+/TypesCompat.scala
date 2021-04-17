package reactivemongo.api.bson

/** Type level evidence that type `A` is not type `B`. */
@SuppressWarnings(Array("ClassNames"))
final class ¬[A, B] private[bson] () {
  override val toString = "not"
}

@SuppressWarnings(Array("ObjectNames"))
object ¬ {
  implicit def defaultEvidence[A, B]: ¬[A, B] = new ¬[A, B]()

  @annotation.implicitAmbiguous("Could not prove type ${A} is not (¬) ${A}")
  implicit def ambiguousEvidence1[A]: ¬[A, A] = null
  implicit def ambiguousEvidence2[A]: ¬[A, A] = null
}

private[bson] trait Aliases {
  type StringOps = scala.collection.StringOps
  type BaseColl[T] = Iterable[T]
}

private[bson] trait Utils {

  @inline private[bson] def lazyZip[A, B](a: Iterable[A], b: Iterable[B]) =
    a.lazyZip(b)

  @inline private[bson] def toLazy[T](it: Iterable[T]) = it.to(LazyList)

  @inline private[bson] def mapValues[K, V, U](m: Map[K, V])(f: V => U) =
    m.view.mapValues(f).toMap

  import scala.collection.Factory
  import scala.util.{ Failure, Try, Success }

  private[bson] def trySeq[A, B, M[_]](
      in: Iterable[A]
    )(f: A => Try[B]
    )(implicit
      cbf: Factory[B, M[B]]
    ): Try[M[B]] = {
    val builder = cbf.newBuilder

    @annotation.tailrec
    def go(in: Iterator[A]): Try[Unit] =
      if (!in.hasNext) Success({})
      else {
        f(in.next()) match {
          case Success(b) => {
            builder += b
            go(in)
          }

          case Failure(e) =>
            Failure(e)
        }
      }

    go(in.iterator).map(_ => builder.result())
  }
}
