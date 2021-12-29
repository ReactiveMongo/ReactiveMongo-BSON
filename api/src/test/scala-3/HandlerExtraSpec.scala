package reactivemongo.api.bson

private[bson] trait HandlerExtraSpec { self: HandlerSpec =>
  "Typed tuple" should {
    "be handle from array" >> {
      def spec[T <: Tuple: BSONHandler](
          name: String,
          tuple: T,
          array: BSONArray
        ) = s"for ${name}" in {

        BSON.write(tuple) must beSuccessfulTry(array) and {
          array.asTry[T] must beSuccessfulTry(tuple)
        }
      }

      spec("tuple2", Tuple2("foo", 2), BSONArray("foo", 2))

      spec("tuple3", Tuple3("foo", 2, "bar"), BSONArray("foo", 2, "bar"))

      spec("tuple4", Tuple4("foo", 2, "bar", 4), BSONArray("foo", 2, "bar", 4))

      spec(
        "tuple5",
        Tuple5("foo", 2, "bar", 4, "lorem"),
        BSONArray("foo", 2, "bar", 4, "lorem")
      )
    }
  }
}
