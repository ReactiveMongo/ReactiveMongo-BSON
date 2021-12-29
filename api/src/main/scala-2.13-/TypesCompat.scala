package reactivemongo.api.bson

private[bson] trait Aliases {
  type StringOps = scala.collection.immutable.StringOps
  type BaseColl[T] = Traversable[T]
}

private[bson] trait Utils {

  @inline private[bson] def lazyZip[A, B](a: Iterable[A], b: Iterable[B]) =
    (a -> b).zipped

  @inline private[bson] def toLazy[T](it: Traversable[T]) = it.toStream

  @inline private[bson] def mapValues[K, V, U](
      m: Map[K, V]
    )(f: V => U
    ): Map[K, U] = m.transform { case (_, v) => f(v) }

  import scala.language.higherKinds
  import scala.collection.generic.CanBuildFrom
  import scala.util.{ Failure, Try, Success }

  private[bson] def trySeq[A, B, M[_]](
      in: Iterable[A]
    )(f: A => Try[B]
    )(implicit
      cbf: CanBuildFrom[M[_], B, M[B]]
    ): Try[M[B]] = {
    val builder = cbf()

    @annotation.tailrec
    def go(in: Iterator[A]): Try[Unit] =
      if (!in.hasNext) Success({})
      else {
        f(in.next) match {
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
