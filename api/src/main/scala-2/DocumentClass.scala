package reactivemongo.api.bson

import com.github.ghik.silencer.silent

/**
 * Evidence that `T` can be serialized as a BSON document.
 */
@silent sealed trait DocumentClass[T]

/** See [[DocumentClass]] */
object DocumentClass {
  import language.experimental.macros

  private val unsafe = new DocumentClass[Nothing] {}

  /** Un-checked factory */
  @silent
  @SuppressWarnings(Array("AsInstanceOf"))
  def unchecked[T] = unsafe.asInstanceOf[DocumentClass[T]]

  /**
   * Implicit evidence of `DocumentClass` for `T`
   * if `T` is a case class or a sealed trait.
   */
  @SuppressWarnings(Array("NullParameter"))
  implicit def evidence[T]: DocumentClass[T] = macro MacroImpl.documentClass[T]
}
