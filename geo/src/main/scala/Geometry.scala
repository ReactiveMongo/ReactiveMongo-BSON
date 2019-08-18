package reactivemongo.api.bson

import scala.util.{ Failure, Success, Try }

import GeoPosition.safeWriter.{ safeWrite => writePos }

/**
 * GeoJSON [[https://docs.mongodb.com/manual/reference/geojson/#overview geometry object]]
 */
sealed trait GeoGeometry {
  /** The type of coordinates (depends on the type of geometry). */
  private[bson] type C

  def `type`: String

  /**
   * The coordinates
   */
  protected def coordinates: C
}

object GeoGeometry {
  implicit val handler: BSONDocumentHandler[GeoGeometry] = {
    implicit val cfg: MacroConfiguration = MacroConfiguration(
      discriminator = "type",
      typeNaming = TypeNaming(
        TypeNaming.SimpleName.andThen(_ stripPrefix "Geo")))

    Macros.handler[GeoGeometry]
  }

  // ---

  private[bson] def reader[G <: GeoGeometry](
    readCoordinates: BSONValue => Try[G]): BSONDocumentReader[G] =
    BSONDocumentReader.from[G] { doc =>
      doc.get("coordinates") match {
        case Some(coords) =>
          readCoordinates(coords)

        case _ =>
          Failure(exceptions.BSONValueNotFoundException("coordinates", doc))
      }
    }

  private[bson] def writer[G <: GeoGeometry](
    writeCoordinates: G => BSONValue): BSONDocumentWriter[G] with SafeBSONDocumentWriter[G] = BSONDocumentWriter.safe[G] { geometry =>
    BSONDocument(
      "type" -> geometry.`type`,
      "coordinates" -> writeCoordinates(geometry))
  }
}

/**
 * GeoJSON [[https://docs.mongodb.com/manual/reference/geojson/#point Point]]
 */
final case class GeoPoint(
  coordinates: GeoPosition) extends GeoGeometry {
  type C = GeoPosition

  @inline def `type` = GeoPoint.`type`
}

/** See [[GeoPoint]] */
object GeoPoint {
  val `type` = "Point"

  /**
   * Convenient factory (See [[GeoPosition]]).
   *
   * {{{
   * // Equivalent to
   * GeoPoint(GeoPosition(_1, _2))
   * }}}
   */
  @inline def apply(_1: Double, _2: Double): GeoPoint =
    GeoPoint(GeoPosition(_1, _2, None))

  /**
   * Convenient factory (See [[GeoPosition]]).
   *
   * {{{
   * // Equivalent to
   * GeoPoint(GeoPosition(_1, _2, elevation))
   * }}}
   */
  @inline def apply(_1: Double, _2: Double, elevation: Option[Double]): GeoPoint = GeoPoint(GeoPosition(_1, _2, elevation))

  implicit val reader: BSONDocumentReader[GeoPoint] =
    GeoGeometry.reader[GeoPoint] {
      case GeoPosition.BSON(pos) =>
        Success(GeoPoint(pos))

      case bson =>
        Failure(exceptions.TypeDoesNotMatchException(
          "<position>", bson.getClass.getSimpleName))

    }

  implicit val writer: BSONDocumentWriter[GeoPoint] =
    GeoGeometry.writer[GeoPoint] { point =>
      writePos(point.coordinates)
    }
}

/**
 * GeoJSON [[https://tools.ietf.org/html/rfc7946#section-3.1.4 LineString]]
 */
final case class GeoLineString(
  _1: GeoPosition,
  _2: GeoPosition,
  more: Seq[GeoPosition]) extends GeoGeometry {
  type C = Tuple3[GeoPosition, GeoPosition, Seq[GeoPosition]]
  @inline def `type` = GeoLineString.`type`

  val coordinates = Tuple3(_1, _2, more)

  /**
   * Appends more positions.
   *
   * {{{
   * // Equivalent to
   * lineString.copy(more = lineString.more ++ positions)
   * }}}
   */
  @inline def ++(positions: GeoPosition*): GeoLineString =
    copy(more = more ++ positions)
}

/** See [[GeoLineString]] */
object GeoLineString {
  val `type` = "LineString"

  /**
   * Convenient factory.
   *
   * {{{
   * // Equivalent to
   * GeoLineString(_1, _2, Seq.empty)
   * }}}
   */
  @inline def apply(_1: GeoPosition, _2: GeoPosition): GeoLineString =
    GeoLineString(_1, _2, Seq.empty)

  private[bson] val readCoordinates: BSONValue => Try[GeoLineString] = {
    case BSONArray(Seq(
      GeoPosition.BSON(a),
      GeoPosition.BSON(b),
      more @ _*)) =>
      GeoPosition.readSeq(more).map { morePos =>
        GeoLineString(a, b, morePos)
      }

    case bson =>
      Failure(exceptions.TypeDoesNotMatchException(
        "[ <position>, <position>, ... ]",
        bson.getClass.getSimpleName))
  }

  implicit val reader: BSONDocumentReader[GeoLineString] =
    GeoGeometry.reader[GeoLineString](readCoordinates)

  private[bson] val writeCoordinates: GeoLineString => BSONArray = { lineStr =>
    writePos(lineStr._1) +: writePos(lineStr._2) +: BSONArray(
      lineStr.more.map(writePos))
  }

  implicit def writer: BSONDocumentWriter[GeoLineString] =
    GeoGeometry.writer[GeoLineString](writeCoordinates)
}

/**
 * GeoJSON [[https://docs.mongodb.com/manual/reference/geojson/#polygon Polygon]]
 */
final class GeoPolygon private[bson] (
  val exterior: GeoLinearRing,
  val interior: Seq[GeoLinearRing]) extends GeoGeometry {
  type C = Tuple2[GeoLinearRing, Seq[GeoLinearRing]]
  @inline def `type`: String = GeoPolygon.`type`

  private[bson] lazy val tupled = Tuple2(exterior, interior)

  @inline def coordinates = tupled

  /**
   * {{{
   * // Equivalent to
   * GeoPolygon(this.exterior, this.interior ++ interior)
   * }}}
   */
  def ++(interior: GeoLinearRing*): GeoPolygon =
    new GeoPolygon(exterior, this.interior ++ interior)

  override def equals(that: Any): Boolean = that match {
    case other: GeoPolygon => tupled == other.tupled
    case _ => false
  }

  override lazy val hashCode: Int = tupled.hashCode

  override def toString: String =
    s"""GeoPolygon(${exterior}${interior mkString (", ", ", ", "")})"""
}

/** See [[GeoPolygon]] */
object GeoPolygon {
  val `type`: String = "Polygon"

  /**
   * Creates a [[https://docs.mongodb.com/manual/reference/geojson/#polygons-with-a-single-ring single ring]] polygon.
   *
   * @param exterior the single (exterior) ring (cannot self-intersect)
   */
  def apply(exterior: GeoLinearRing): GeoPolygon =
    new GeoPolygon(exterior, Seq.empty)

  /**
   * Creates a [[https://docs.mongodb.com/manual/reference/geojson/#polygons-with-multiple-rings multiple rings]] polygon.
   *
   * Any interior ring must be entirely contained by the outer ring.
   * Interior rings cannot intersect or overlap each other.
   * Interior rings cannot share an edge.
   *
   * @param exterior the exterior (mandatory) ring (cannot self-intersect)
   * @param interior some optional interior rings
   */
  def apply(exterior: GeoLinearRing, interior: GeoLinearRing*): GeoPolygon =
    new GeoPolygon(exterior, interior)

  @inline def unapply(polygon: GeoPolygon): Option[(GeoLinearRing, Seq[GeoLinearRing])] = Option(polygon).map(_.tupled)

  private[bson] val readCoordinates: BSONValue => Try[GeoPolygon] = {
    case BSONArray(Seq(exterior @ BSONArray(_), interior @ _*)) =>
      for {
        ex <- GeoLinearRing.reader.readTry(exterior)
        in <- GeoLinearRing.readSeq(interior)
      } yield new GeoPolygon(ex, in)

    case bson =>
      Failure(exceptions.TypeDoesNotMatchException(
        "[ <lineString>, ... ]", bson.getClass.getSimpleName))
  }

  implicit val reader: BSONDocumentReader[GeoPolygon] =
    GeoGeometry.reader[GeoPolygon](readCoordinates)

  private[bson] val writeCoordinates: GeoPolygon => BSONArray = { polygon =>
    import GeoLinearRing.safeWriter.safeWrite

    BSONArray(safeWrite(polygon.exterior) +: polygon.interior.map(safeWrite))
  }

  implicit val writer: BSONDocumentWriter[GeoPolygon] =
    GeoGeometry.writer[GeoPolygon](writeCoordinates)
}

/** GeoJSON [[https://docs.mongodb.com/manual/reference/geojson/#multipoint MultiPoint]] (collection of [[GeoPosition]]) */
case class GeoMultiPoint(
  coordinates: Seq[GeoPosition]) extends GeoGeometry {
  type C = Seq[GeoPosition]

  val `type`: String = GeoMultiPoint.`type`
}

/** See [[GeoMultiPoint]] */
object GeoMultiPoint {
  val `type`: String = "MultiPoint"

  implicit val reader: BSONDocumentReader[GeoMultiPoint] =
    GeoGeometry.reader[GeoMultiPoint] {
      case BSONArray(values) =>
        GeoPosition.readSeq(values).map(GeoMultiPoint(_))

    }

  implicit val writer: BSONDocumentWriter[GeoMultiPoint] =
    GeoGeometry.writer[GeoMultiPoint] { multi =>
      BSONArray(multi.coordinates.map(GeoPosition.safeWriter.safeWrite))
    }

}

/** GeoJSON [[https://docs.mongodb.com/manual/reference/geojson/#multilinestring MultiLineString]] (collection of [[GeoLineString]]) */
case class GeoMultiLineString(coordinates: Seq[GeoLineString]) extends GeoGeometry {
  type C = Seq[GeoLineString]
  @inline def `type`: String = GeoMultiLineString.`type`
}

/** See [[GeoMultiLineString]] */
object GeoMultiLineString {
  val `type`: String = "MultiLineString"

  implicit val reader: BSONDocumentReader[GeoMultiLineString] = {
    @annotation.tailrec
    def go(
      values: Seq[BSONValue],
      out: List[GeoLineString]): Try[Seq[GeoLineString]] =
      values.headOption match {
        case Some(v) => GeoLineString.readCoordinates(v) match {
          case Success(lineString) =>
            go(values.tail, lineString :: out)

          case Failure(cause) =>
            Failure(cause)
        }

        case _ =>
          Success(out.reverse)
      }

    GeoGeometry.reader[GeoMultiLineString] {
      case BSONArray(values) =>
        go(values, List.empty).map(GeoMultiLineString(_))

      case bson =>
        Failure(exceptions.TypeDoesNotMatchException(
          "[ <multiLineString>, ... ]", bson.getClass.getSimpleName))
    }
  }

  implicit val writer: BSONDocumentWriter[GeoMultiLineString] =
    GeoGeometry.writer[GeoMultiLineString] { multiLineString =>
      BSONArray(multiLineString.coordinates.map(GeoLineString.writeCoordinates))
    }

}

/** GeoJSON [[https://docs.mongodb.com/manual/reference/geojson/#multipolygon MultiPolygon]] (collection of [[GeoPolygon]]) */
case class GeoMultiPolygon(coordinates: Seq[GeoPolygon]) extends GeoGeometry {
  type C = Seq[GeoPolygon]
  @inline def `type`: String = GeoMultiPolygon.`type`
}

/** See [[GeoMultiPolygon]] */
object GeoMultiPolygon {
  val `type`: String = "MultiPolygon"

  implicit val reader: BSONDocumentReader[GeoMultiPolygon] = {
    @annotation.tailrec
    def go(
      values: Seq[BSONValue],
      out: List[GeoPolygon]): Try[Seq[GeoPolygon]] = values.headOption match {
      case Some(bson) => GeoPolygon.readCoordinates(bson) match {
        case Success(polygon) =>
          go(values.tail, polygon :: out)

        case Failure(cause) =>
          Failure(cause)
      }

      case _ =>
        Success(out.reverse)
    }

    GeoGeometry.reader[GeoMultiPolygon] {
      case BSONArray(values) =>
        go(values, List.empty).map(GeoMultiPolygon(_))

      case bson =>
        Failure(exceptions.TypeDoesNotMatchException(
          "[ <multiPolygon>, ... ]", bson.getClass.getSimpleName))
    }
  }

  implicit val writer: BSONDocumentWriter[GeoMultiPolygon] =
    GeoGeometry.writer[GeoMultiPolygon] { multiPolygon =>
      BSONArray(multiPolygon.coordinates.map(GeoPolygon.writeCoordinates))
    }

}
