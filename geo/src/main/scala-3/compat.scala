package reactivemongo.api.bson

private[bson] trait GeoGeometryCollectionCompat {
  _self: GeoGeometryCollection.type =>

  given Conversion[GeoGeometryCollection, Tuple1[Seq[GeoGeometry]]] = {
    (geo: GeoGeometryCollection) => Tuple1(geo.geometries)
  }

  implicit object ProductOfGeoGeometryCollection
      extends scala.deriving.Mirror.Product {
    type MirroredType = GeoGeometryCollection
    type MirroredElemTypes = Tuple1[Seq[GeoGeometry]]
    type MirroredMonoType = GeoGeometryCollection
    type MirroredLabel = "GeoGeometryCollection"
    type MirroredElemLabels = Tuple1["geometries"]

    def fromProduct(p: Product): MirroredMonoType =
      new GeoGeometryCollection(
        p.productElement(0).asInstanceOf[Seq[GeoGeometry]]
      )
  }

}
