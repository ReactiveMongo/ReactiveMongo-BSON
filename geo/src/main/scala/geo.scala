package reactivemongo.api.bson

import scala.util.{ Failure, Success, Try }

/**
 * GeoJSON [[https://tools.ietf.org/html/rfc7946#section-3.1.1 Position]]
 *
 * @param _1 either longitude or easting
 * @param _2 either latitude (if `_1` is longitude) or northing
 * @param elevation the optional elevation value (altitude)
 */
final class GeoPosition private[api] (
  val _1: Double,
  val _2: Double,
  val elevation: Option[Double]) {

  private[api] lazy val tupled = Tuple3(_1, _2, elevation)

  override def equals(that: Any): Boolean = that match {
    case other: GeoPosition =>
      this.tupled == other.tupled

    case _ =>
      false
  }

  override def hashCode: Int = tupled.hashCode

  override def toString = s"GeoPosition${tupled.toString}"
}

/** [[GeoPosition]] factories and utilities */
object GeoPosition {
  /**
   * Creates a full-qualified position.
   *
   * {{{
   * reactivemongo.api.bson.GeoPosition(12.3D, 4.56D, None)
   * }}}
   */
  def apply(
    _1: Double,
    _2: Double,
    elevation: Option[Double]): GeoPosition = new GeoPosition(_1, _2, elevation)

  /**
   * Convenient factory (equivalent to `GeoPosition(_1, _2, None)`).
   */
  def apply(_1: Double, _2: Double): GeoPosition = GeoPosition(_1, _2, None)

  /**
   * Extracts the position properties.
   *
   * {{{
   * import reactivemongo.api.bson.GeoPosition
   *
   * def pos_1(pos: GeoPosition): Option[Double] = pos match {
   *   case GeoPosition(a, b, elevation) => {
   *     println("_1 = " + a + ", _2 = " + b + " / " + elevation)
   *     Some(a)
   *   }
   *
   *   case _ =>
   *     None
   * }
   * }}}
   */
  def unapply(pos: GeoPosition): Option[(Double, Double, Option[Double])] =
    Option(pos).map(_.tupled)

  implicit val reader: BSONReader[GeoPosition] =
    BSONReader.from[GeoPosition]({
      case GeoPosition.BSON(pos) =>
        Success(pos)

      case bson =>
        Failure(typeDoesNotMatch(bson))

    })

  private[bson] val safeWriter = BSONWriter.safe[GeoPosition] { coords =>
    coords.elevation match {
      case Some(e) => BSONArray(coords._1, coords._2, e)
      case _ => BSONArray(coords._1, coords._2, None)
    }
  }

  implicit val writer: BSONWriter[GeoPosition] = safeWriter

  private[bson] def readSeq(seq: Seq[BSONValue]): Try[Seq[GeoPosition]] = {
    @annotation.tailrec
    def go(s: Seq[BSONValue], out: List[GeoPosition]): Try[Seq[GeoPosition]] =
      s.headOption match {
        case Some(BSON(pos)) => go(s.tail, pos :: out)

        case Some(bson) =>
          Failure(typeDoesNotMatch(bson))

        case _ =>
          Success(out.reverse)
      }

    go(seq, List.empty)
  }

  private[bson] object BSON {
    def unapply(bson: BSONValue): Option[GeoPosition] = bson match {
      case BSONArray(Seq(BSONDouble(x), BSONDouble(y))) =>
        Some(GeoPosition(x, y, None))

      case BSONArray(Seq(
        BSONDouble(x), BSONDouble(y), BSONDouble(z))) =>
        Some(GeoPosition(x, y, Some(z)))

      case _ =>
        None
    }
  }

  @inline private def typeDoesNotMatch(bson: BSONValue) =
    exceptions.TypeDoesNotMatchException(
      "[<double>, <double>]", bson.getClass.getSimpleName)
}

/**
 * GeoJSON [[https://docs.mongodb.com/manual/reference/geojson/#polygon linear ring]]
 */
final class GeoLinearRing private[bson] (
  val _1: GeoPosition,
  val _2: GeoPosition,
  val _3: GeoPosition,
  val more: Seq[GeoPosition]) {

  /** The start position (alias for [[_1]]). */
  @inline def start: GeoPosition = _1

  /** The end position (alias for [[_1]] or [[start]]). */
  @inline def end: GeoPosition = _1

  private[bson] lazy val tupled = Tuple4(_1, _2, _3, more)

  override def equals(that: Any): Boolean = that match {
    case other: GeoLinearRing => tupled == other.tupled
    case _ => false
  }

  override lazy val hashCode: Int = tupled.hashCode

  override def toString: String =
    s"""GeoLinearRing(${_1}, ${_2}, ${_3}${more.mkString(", ", ", ", "")})"""
}

/** See [[GeoLinearRing]] */
object GeoLinearRing {
  /**
   * Creates a minimal linear ring.
   *
   * @param _1 the origin position (first and last)
   */
  @inline def apply(
    _1: GeoPosition,
    _2: GeoPosition,
    _3: GeoPosition): GeoLinearRing = new GeoLinearRing(_1, _2, _3, Seq.empty)

  /**
   * Creates a linear ring.
   *
   * @param _1 the origin position (first and last)
   */
  @inline def apply(
    _1: GeoPosition,
    _2: GeoPosition,
    _3: GeoPosition,
    more: Seq[GeoPosition]): GeoLinearRing = new GeoLinearRing(_1, _2, _3, more)

  /**
   * Positions extractor for the given `ring`.
   */
  def unapply(ring: GeoLinearRing): Option[Tuple4[GeoPosition, GeoPosition, GeoPosition, Seq[GeoPosition]]] = Option(ring).map(_.tupled)

  implicit val reader: BSONReader[GeoLinearRing] =
    BSONReader.from[GeoLinearRing] {
      case BSONArray(Seq(
        GeoPosition.BSON(_1),
        GeoPosition.BSON(_2),
        GeoPosition.BSON(_3),
        GeoPosition.BSON(_4))) if (_1 /*start*/ == _4 /*end*/ ) =>
        Success(GeoLinearRing(_1, _2, _3))

      case BSONArray(Seq(
        GeoPosition.BSON(_1),
        _,
        _,
        GeoPosition.BSON(_4))) =>
        Failure(exceptions.ValueDoesNotMatchException(
          s"start (${_1}) != end (${_4})"))

      case BSONArray(Seq(
        GeoPosition.BSON(_1),
        GeoPosition.BSON(_2),
        GeoPosition.BSON(_3),
        more @ _*)
        ) => GeoPosition.readSeq(more) match {
        case Success(morePositions) => morePositions.lastOption match {
          case Some(`_1`) =>
            Success(GeoLinearRing(_1, _2, _3, morePositions.init))

          case last =>
            Failure(exceptions.ValueDoesNotMatchException(
              s"start (${_1}) != end ($last)"))
        }

        case Failure(cause) =>
          Failure(cause)
      }

      case bson =>
        Failure(exceptions.TypeDoesNotMatchException(
          "[<geoposition>, <geoposition>, <geoposition>, ...]",
          bson.getClass.getSimpleName))
    }

  private[bson] val safeWriter = {
    import GeoPosition.safeWriter.safeWrite

    BSONWriter.safe[GeoLinearRing] { ring =>
      if (ring.more.isEmpty) {
        BSONArray(
          Seq(ring._1, ring._2, ring._3, ring._1).map(safeWrite))

      } else {
        safeWrite(ring._1) +: safeWrite(ring._2) +: safeWrite(
          ring._3) +: BSONArray((ring.more :+ ring._1).map(safeWrite))
      }
    }
  }

  implicit val writer: BSONWriter[GeoLinearRing] = safeWriter

  private[bson] def readSeq(seq: Seq[BSONValue]): Try[Seq[GeoLinearRing]] = {
    @annotation.tailrec
    def go(s: Seq[BSONValue], out: List[GeoLinearRing]): Try[Seq[GeoLinearRing]] = s.headOption match {
      case Some(bson) => reader.readTry(bson) match {
        case Success(ring) => go(s.tail, ring :: out)
        case Failure(cause) => Failure(cause)
      }

      case _ =>
        Success(out.reverse)
    }

    go(seq, List.empty)
  }
}

/**
 * GeoJSON [[https://docs.mongodb.com/manual/reference/geojson/#geometrycollection GeometryCollection]] (collection of [[GeoGeometry]])
 */
final class GeoGeometryCollection private[api] (
  val geometries: Seq[GeoGeometry]) {

  override def equals(that: Any): Boolean = that match {
    case other: GeoGeometryCollection =>
      this.geometries == other.geometries

    case _ =>
      false
  }

  override def hashCode: Int = geometries.hashCode

  override def toString =
    s"""GeoGeometryCollection${geometries.mkString("[", ", ", "]")}"""
}

/** See [[GeoGeometryCollection]] */
object GeoGeometryCollection {
  /** Creates a new collection. */
  def apply(geometries: Seq[GeoGeometry]): GeoGeometryCollection =
    new GeoGeometryCollection(geometries)

  /**
   * Extracts the geometries from the collection.
   */
  def unapply(collection: GeoGeometryCollection): Option[Seq[GeoGeometry]] =
    Option(collection).map(_.geometries)

  implicit val reader: BSONDocumentReader[GeoGeometryCollection] =
    Macros.readerOpts[GeoGeometryCollection, MacroOptions.Verbose] // ignore 'type'

  implicit val writer: BSONDocumentWriter[GeoGeometryCollection] =
    BSONDocumentWriter[GeoGeometryCollection] { geo =>
      BSONDocument(
        "type" -> "GeometryCollection",
        "geometries" -> geo.geometries)
    }

}

