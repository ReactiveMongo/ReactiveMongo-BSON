package reactivemongo.api.bson

/**
 * Implicit conversions for handler & values types
 * between `org.bson` and `reactivemongo.api.bson` .
 *
 * {{{
 * import reactivemongo.api.bson.msb._
 * }}}
 *
 * For more specific imports, see [[ValueConverters]]
 * and [[HandlerConverters]] .
 */
package object msb extends ValueConverters //TODO:with HandlerConverters
