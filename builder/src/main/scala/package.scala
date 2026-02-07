package reactivemongo.api.bson

import scala.util.Try

package object builder {

  private[builder] val lazyValueWriter =
    BSONWriter.from[() => Try[BSONValue]](_())
}
