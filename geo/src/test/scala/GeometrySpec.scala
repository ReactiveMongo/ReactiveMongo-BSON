import reactivemongo.api.bson._

final class GeometrySpec extends org.specs2.mutable.Specification {
  "Geometry" title

  "Point" should {
    // { type: "Point", coordinates: [ 40, 5 ] }
    val bson = BSONDocument(
      "type" -> "Point",
      "coordinates" -> BSONArray(BSONDouble(40D), BSONDouble(5D))
    )

    val point = GeoPoint(40, 5)

    "have expected coordinates" in {
      point.coordinates._1 must_=== 40D and {
        point.coordinates._2 == 5D
      } and {
        point.coordinates.elevation must beNone
      }
    }

    "be equal" in {
      point must_=== GeoPoint(GeoPosition(40, 5, None))
    }

    "be written to BSON" in {
      val w = implicitly[BSONDocumentWriter[GeoPoint]]

      w.writeTry(point) must beSuccessfulTry(bson)
    }

    "be read from BSON" in {
      val r = implicitly[BSONDocumentReader[GeoPoint]]

      r.readTry(bson) must beSuccessfulTry(point)
    }
  }

  "LineString" should {
    // { type: "LineString", coordinates: [ [ 40, 5 ], [ 41, 6 ] ] }
    val bson = BSONDocument(
      "type" -> "LineString",
      "coordinates" -> BSONArray(
        BSONArray(BSONDouble(40), BSONDouble(5)),
        BSONArray(BSONDouble(41), BSONDouble(6))
      )
    )

    val lineString =
      GeoLineString(GeoPosition(40D, 5D, None), GeoPosition(41D, 6D))

    "have expected coordinates" in {
      lineString.coordinates._1 must_=== GeoPosition(40D, 5D, None) and {
        lineString.coordinates._2 == GeoPosition(41D, 6D)
      } and {
        lineString.coordinates._3 /* more */ must beEmpty
      } and {
        lineString.more must beEmpty
      }
    }

    "be equal" in {
      lineString must_=== GeoLineString(
        _1 = GeoPosition(40D, 5D, None),
        _2 = GeoPosition(41D, 6D),
        more = Seq.empty[GeoPosition]
      )
    }

    val moreLens = monocle.field[BSONArray]("coordinates")
    val moreBson = moreLens.modify(_.++(values = BSONArray(51D, 9D)))(bson)
    val moreLineString = lineString ++ GeoPosition(51D, 9D)

    "be written to BSON" in {
      val w = implicitly[BSONDocumentWriter[GeoLineString]]

      w.writeTry(lineString) must beSuccessfulTry(bson) and {
        w.writeTry(moreLineString) must beSuccessfulTry(moreBson)
      }
    }

    "be read from BSON" in {
      val r = implicitly[BSONDocumentReader[GeoLineString]]

      r.readTry(bson) must beSuccessfulTry(lineString) and {
        r.readTry(moreBson) must beSuccessfulTry(moreLineString)
      }
    }
  }

  "Linear ring" should {
    def specs(bson: BSONArray, ring: GeoLinearRing) = {
      "be equal" in {
        ring must_=== GeoLinearRing(ring._1, ring._2, ring._3, ring.more)
      }

      "be written to BSON" in {
        val w = implicitly[BSONWriter[GeoLinearRing]]

        w.writeTry(ring) must beSuccessfulTry(bson)
      }

      "be read from BSON" in {
        val r = implicitly[BSONReader[GeoLinearRing]]

        r.readTry(bson) must beSuccessfulTry(ring)
      }
    }

    "as a minimal one" >> {
      val bson = BSONArray(
        BSONArray(0D, 0D),
        BSONArray(3D, 6D),
        BSONArray(6D, 1D),
        BSONArray(0D, 0D)
      )

      val ring = GeoLinearRing(
        GeoPosition(0D, 0D),
        GeoPosition(3D, 6D),
        GeoPosition(6D, 1D)
      )

      "have expected coordinates" in {
        ring._1 must_=== GeoPosition(0D, 0D) and {
          (ring.start must_=== ring._1) and (ring.end must_=== ring._1)
        } and {
          ring._2 must_=== GeoPosition(3D, 6D)
        } and {
          ring._3 must_=== GeoPosition(6D, 1D)
        } and {
          ring.more must beEmpty
        }
      }

      specs(bson, ring)
    }

    "as a medium one" >> {
      val bson = BSONArray(
        BSONArray(0D, 0D),
        BSONArray(3D, 6D),
        BSONArray(6D, 1D),
        /* more:*/ BSONArray(7.1D, 0.5D),
        BSONArray(8D, 2D),
        BSONArray(0D, 0D)
      )

      val ring = GeoLinearRing(
        GeoPosition(0D, 0D),
        GeoPosition(3D, 6D),
        GeoPosition(6D, 1D),
        more = Seq(GeoPosition(7.1D, 0.5D), GeoPosition(8D, 2D))
      )

      "have expected coordinates" in {
        ring._1 must_=== GeoPosition(0D, 0D) and {
          (ring.start must_=== ring._1) and (ring.end must_=== ring._1)
        } and {
          ring._2 must_=== GeoPosition(3D, 6D)
        } and {
          ring._3 must_=== GeoPosition(6D, 1D)
        } and {
          ring.more must contain(
            exactly(GeoPosition(7.1D, 0.5D), GeoPosition(8D, 2D)).inOrder
          )
        }
      }

      specs(bson, ring)
    }
  }

  "Polygon" should {
    def spec(bson: BSONDocument, polygon: GeoPolygon) = {
      "be equal" in {
        polygon must_=== GeoPolygon(polygon.exterior, polygon.interior: _*)
      }

      "be written to BSON" in {
        val w = implicitly[BSONDocumentWriter[GeoPolygon]]

        w.writeTry(polygon) must beSuccessfulTry(bson)
      }

      "be read from BSON" in {
        val r = implicitly[BSONDocumentReader[GeoPolygon]]

        r.readTry(bson) must beSuccessfulTry(polygon)
      }
    }

    // Fixtures
    val singleRingBson = BSONDocument(
      "type" -> "Polygon",
      "coordinates" -> BSONArray(
        /*exterior:*/ BSONArray(
          BSONArray(0D, 0D),
          BSONArray(3D, 6D),
          BSONArray(6D, 1D),
          BSONArray(0D, 0D)
        )
      )
    )

    val exterior = GeoLinearRing(
      GeoPosition(0D, 0D),
      GeoPosition(3D, 6D),
      GeoPosition(6D, 1D)
    )

    val singleRingPolygon = GeoPolygon(exterior)

    // ---

    "as a single ring one" in {
      /* {
       type: "Polygon",
       coordinates: [ [ [ 0 , 0 ] , [ 3 , 6 ] , [ 6 , 1 ] , [ 0 , 0  ] ] ]
     } */

      "have expected coordinates" in {
        singleRingPolygon.exterior must_=== exterior and {
          singleRingPolygon.interior must beEmpty
        }
      }

      spec(singleRingBson, singleRingPolygon)
    }

    "as a multi ring one" in {
      /* {
       type : "Polygon",
       coordinates : [
           [ [ 0 , 0 ] , [ 3 , 6 ] , [ 6 , 1 ] , [ 0 , 0 ] ],
           [ [ 2 , 2 ] , [ 3 , 3 ] , [ 4 , 2 ] , [ 2 , 2 ] ]
         ]
       } */

      val interiorRing = GeoLinearRing(
        GeoPosition(2D, 2D),
        GeoPosition(3D, 3D),
        GeoPosition(4D, 2D)
      )

      val interiorBson = BSONArray(
        BSONArray(2D, 2D),
        BSONArray(3D, 3D),
        BSONArray(4D, 2D),
        BSONArray(2D, 2D)
      )

      val polygon = singleRingPolygon ++ interiorRing
      val bson = {
        val addInterior = monocle.field[BSONArray]("coordinates").modify {
          _.++(values = interiorBson)
        }

        addInterior(singleRingBson)
      }

      "have expected coordinates" in {
        polygon.exterior must_=== exterior and {
          polygon.interior must contain(exactly(interiorRing).inOrder)
        }
      }

      spec(bson, polygon)
    }
  }

  "MultiPoint" should {
    /* {
     type: "MultiPoint",
     coordinates: [
         [ -73.9580, 40.8003 ],
         [ -73.9498, 40.7968 ],
         [ -73.9737, 40.7648 ],
         [ -73.9814, 40.7681 ]
       ]
     } */
    val bson = BSONDocument(
      "type" -> "MultiPoint",
      "coordinates" -> BSONArray(
        BSONArray(-73.9580D, 40.8003D),
        BSONArray(-73.9498D, 40.7968D),
        BSONArray(-73.9737D, 40.7648D),
        BSONArray(-73.9814D, 40.7681D)
      )
    )

    val multiPoint = GeoMultiPoint(
      Seq(
        GeoPosition(-73.9580D, 40.8003D),
        GeoPosition(-73.9498D, 40.7968D),
        GeoPosition(-73.9737D, 40.7648D),
        GeoPosition(-73.9814D, 40.7681D)
      )
    )

    "be written to BSON" in {
      val w = implicitly[BSONDocumentWriter[GeoMultiPoint]]

      w.writeTry(multiPoint) must beSuccessfulTry(bson)
    }

    "be read from BSON" in {
      val r = implicitly[BSONDocumentReader[GeoMultiPoint]]

      r.readTry(bson) must beSuccessfulTry(multiPoint)
    }
  }

  "MultiLineString" should {
    /* {
     type: "MultiLineString",
     coordinates: [
         [ [ -73.96943, 40.78519 ], [ -73.96082, 40.78095 ] ],
         [ [ -73.96415, 40.79229 ], [ -73.95544, 40.78854 ] ],
         [ [ -73.97162, 40.78205 ], [ -73.96374, 40.77715 ] ],
         [ [ -73.97880, 40.77247 ], [ -73.97036, 40.76811 ] ]
       ]
     } */
    val bson = BSONDocument(
      "type" -> "MultiLineString",
      "coordinates" -> BSONArray(
        BSONArray(
          BSONArray(-73.96943D, 40.78519D),
          BSONArray(-73.96082D, 40.78095D)
        ),
        BSONArray(
          BSONArray(-73.96415D, 40.79229D),
          BSONArray(-73.95544D, 40.78854D)
        ),
        BSONArray(
          BSONArray(-73.97162D, 40.78205D),
          BSONArray(-73.96374D, 40.77715D)
        ),
        BSONArray(
          BSONArray(-73.97880D, 40.77247D),
          BSONArray(-73.97036D, 40.76811D)
        )
      )
    )

    val multiLineString = GeoMultiLineString(
      Seq(
        GeoLineString(
          GeoPosition(-73.96943D, 40.78519D),
          GeoPosition(-73.96082D, 40.78095D)
        ),
        GeoLineString(
          GeoPosition(-73.96415D, 40.79229D),
          GeoPosition(-73.95544D, 40.78854D)
        ),
        GeoLineString(
          GeoPosition(-73.97162D, 40.78205D),
          GeoPosition(-73.96374D, 40.77715D)
        ),
        GeoLineString(
          GeoPosition(-73.97880D, 40.77247D),
          GeoPosition(-73.97036D, 40.76811D)
        )
      )
    )

    "be written to BSON" in {
      val w = implicitly[BSONDocumentWriter[GeoMultiLineString]]

      w.writeTry(multiLineString) must beSuccessfulTry(bson)
    }

    "be read to BSON" in {
      val r = implicitly[BSONDocumentReader[GeoMultiLineString]]

      r.readTry(bson) must beSuccessfulTry(multiLineString)
    }
  }

  "MultiPolygon" should {
    /* {
     type: "MultiPolygon",
     coordinates: [
         [ [ [ -73.958, 40.8003 ], [ -73.9498, 40.7968 ], [ -73.9737, 40.7648 ], [ -73.9814, 40.7681 ], [ -73.958, 40.8003 ] ] ],
         [ [ [ -73.958, 40.8003 ], [ -73.9498, 40.7968 ], [ -73.9737, 40.7648 ], [ -73.958, 40.8003 ] ] ]
       ]
     } */
    val bson = BSONDocument(
      "type" -> "MultiPolygon",
      "coordinates" -> BSONArray(
        BSONArray(
          BSONArray(
            BSONArray(-73.958D, 40.8003D),
            BSONArray(-73.9498D, 40.7968D),
            BSONArray(-73.9737D, 40.7648D),
            BSONArray(-73.9814D, 40.7681D),
            BSONArray(-73.958D, 40.8003D)
          )
        ),
        BSONArray(
          BSONArray(
            BSONArray(-73.958D, 40.8003D),
            BSONArray(-73.9498D, 40.7968D),
            BSONArray(-73.9737D, 40.7648D),
            BSONArray(-73.958D, 40.8003D)
          )
        )
      )
    )

    val multiPolygon = GeoMultiPolygon(
      Seq(
        GeoPolygon(
          GeoLinearRing(
            GeoPosition(-73.958D, 40.8003D),
            GeoPosition(-73.9498D, 40.7968D),
            GeoPosition(-73.9737D, 40.7648D),
            Seq(GeoPosition(-73.9814D, 40.7681D))
          )
        ),
        GeoPolygon(
          GeoLinearRing(
            GeoPosition(-73.958D, 40.8003D),
            GeoPosition(-73.9498D, 40.7968D),
            GeoPosition(-73.9737D, 40.7648D)
          )
        )
      )
    )

    "be written to BSON" in {
      val w = implicitly[BSONDocumentWriter[GeoMultiPolygon]]

      w.writeTry(multiPolygon) must beSuccessfulTry(bson)
    }

    "be read to BSON" in {
      val r = implicitly[BSONDocumentReader[GeoMultiPolygon]]

      r.readTry(bson) must beSuccessfulTry(multiPolygon)
    }
  }

  "GeoGeometryCollection" should {
    /* {
       type: "GeometryCollection",
       geometries: [
         {
           type: "MultiPoint",
           coordinates: [
             [ -73.9580, 40.8003 ],
             [ -73.9498, 40.7968 ],
             [ -73.9737, 40.7648 ],
             [ -73.9814, 40.7681 ]
           ]
         },
         {
           type: "MultiLineString",
           coordinates: [
             [ [ -73.96943, 40.78519 ], [ -73.96082, 40.78095 ] ],
             [ [ -73.96415, 40.79229 ], [ -73.95544, 40.78854 ] ],
             [ [ -73.97162, 40.78205 ], [ -73.96374, 40.77715 ] ],
             [ [ -73.97880, 40.77247 ], [ -73.97036, 40.76811 ] ]
           ]
         }
       ]
     } */

    val bson = BSONDocument(
      "type" -> "GeometryCollection",
      "geometries" -> BSONArray(
        BSONDocument(
          "type" -> "MultiPoint",
          "coordinates" -> BSONArray(
            BSONArray(-73.9580D, 40.8003D),
            BSONArray(-73.9498D, 40.7968D),
            BSONArray(-73.9737D, 40.7648D),
            BSONArray(-73.9814D, 40.7681D)
          )
        ),
        BSONDocument(
          "type" -> "MultiLineString",
          "coordinates" -> BSONArray(
            BSONArray(
              BSONArray(-73.96943D, 40.78519D),
              BSONArray(-73.96082D, 40.78095D)
            ),
            BSONArray(
              BSONArray(-73.96415D, 40.79229D),
              BSONArray(-73.95544D, 40.78854D)
            ),
            BSONArray(
              BSONArray(-73.97162D, 40.78205D),
              BSONArray(-73.96374D, 40.77715D)
            ),
            BSONArray(
              BSONArray(-73.97880D, 40.77247D),
              BSONArray(-73.97036D, 40.76811D)
            )
          )
        )
      )
    )

    val geocol = GeoGeometryCollection(
      Seq(
        GeoMultiPoint(
          Seq(
            GeoPosition(-73.9580D, 40.8003D),
            GeoPosition(-73.9498D, 40.7968D),
            GeoPosition(-73.9737D, 40.7648D),
            GeoPosition(-73.9814D, 40.7681D)
          )
        ),
        GeoMultiLineString(
          Seq(
            GeoLineString(
              GeoPosition(-73.96943D, 40.78519D),
              GeoPosition(-73.96082, 40.78095)
            ),
            GeoLineString(
              GeoPosition(-73.96415D, 40.79229D),
              GeoPosition(-73.95544D, 40.78854D)
            ),
            GeoLineString(
              GeoPosition(-73.97162D, 40.78205D),
              GeoPosition(-73.96374D, 40.77715D)
            ),
            GeoLineString(
              GeoPosition(-73.97880D, 40.77247D),
              GeoPosition(-73.97036D, 40.76811D)
            )
          )
        )
      )
    )

    "be written to BSON" in {
      val w = implicitly[BSONDocumentWriter[GeoGeometryCollection]]

      w.writeTry(geocol) must beSuccessfulTry(bson)
    }

    "be read from BSON" in {
      val r = implicitly[BSONDocumentReader[GeoGeometryCollection]]

      r.readTry(bson) must beSuccessfulTry(geocol)
    }
  }
}
