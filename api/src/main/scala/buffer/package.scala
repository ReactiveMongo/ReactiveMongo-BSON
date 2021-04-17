package reactivemongo.api.bson

package object buffer {

  private val strict = sys.props
    .get("reactivemongo.api.bson.document.strict")
    .fold(false)(_.toBoolean)

  private[reactivemongo] val DefaultBufferHandler: BufferHandler = {
    if (strict) {
      new BufferHandler with StrictBufferHandler
    } else {
      new BufferHandler with PlainBufferHandler
    }
  }
}
