package reactivemongo.api.bson

import scala.util.Success

object Macros extends MacroAnnotations {
  // --- Internals TODO: Common

  /** Only for internal purposes */
  final class Placeholder private () {}

  /** Only for internal purposes */
  object Placeholder {
    private val instance = new Placeholder()

    implicit object Handler extends BSONDocumentHandler[Placeholder] {
      def readDocument(bson: BSONDocument) = Success(instance)

      @com.github.ghik.silencer.silent
      def writeTry(pl: Placeholder) = Success(BSONDocument.empty)
    }
  }

  /* TODO
  /** Only for internal purposes */
  final class LocalVar[@specialized T] {
    private var underlying: T = _

    def take(value: T): LocalVar[T] = {
      underlying = value
      this
    }

    def value(): T = underlying
  }
   */
}
