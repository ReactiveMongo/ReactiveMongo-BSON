package reactivemongo.api.bson

/**
 * Implicit conversions for handler & values types
 * between `reactivemongo.bson` and `reactivemongo.api.bson` .
 *
 * {{{
 * import reactivemongo.api.bson.Converters._
 * }}}
 *
 * For more specific imports, see [[ValueConverters]]
 * and [[HandlerConverters]] .
 */
object Converters extends ValueConverters with HandlerConverters
