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
