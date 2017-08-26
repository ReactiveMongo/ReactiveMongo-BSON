import reactivemongo.api.bson._

class TypeSpec extends org.specs2.mutable.Specification {
  "BSON types" title

  "Generating BSONObjectID" should {
    "not throw a SocketException" in {

      /*
       * for i in `seq 1 257`; do
       *   openvpn --mktun --dev tun$i
       *   ip link set tun$i up
       *   ip -6 addr add 2001:DB8::`printf %04x $i`/128 dev tun$i
       * done
       */
      BSONObjectID.generate() must beAnInstanceOf[BSONObjectID]
    }
  }

  "BSON document" should {
    "be empty" in {
      BSONDocument().elements must beEmpty and (
        BSONDocument.empty.elements must beEmpty) and (
          document.elements must beEmpty) and (
            document().elements must beEmpty) and (
              BSONDocument.empty.contains("foo") must beFalse)
    }

    "be created with a new element " in {
      val doc = BSONDocument.empty :~ ("foo" -> 1)

      doc must_== BSONDocument("foo" -> 1) and (
        doc.contains("foo") must beTrue)
    }

    "remove specified elements" in {
      val doc = BSONDocument("Foo" -> 1, "Bar" -> 2, "Lorem" -> 3)

      doc.remove("Bar", "Lorem") must_== BSONDocument("Foo" -> 1) and (
        doc -- ("Foo", "Bar") must_== BSONDocument("Lorem" -> 3))
    }
  }

  "BSONArray" should {
    "be empty" in {
      BSONArray().values must beEmpty and (
        BSONArray.empty.values must beEmpty) and (
          array.values must beEmpty) and (
            array().values must beEmpty)

    }

    "be created with a new element " in {
      val arr = BSONArray.empty ++ ("foo", "bar")
      arr must_== BSONArray("foo", "bar")
    }

    "be returned with a prepended element" in {
      BSONString("bar") +: BSONArray("foo") must_== BSONArray("bar", "foo")
    }
  }

  "BSONBinary" should {
    "be read as byte array" in {
      val bytes = Array[Byte](1, 2, 3)
      val bson = BSONBinary(bytes, Subtype.GenericBinarySubtype)

      bson.byteArray aka "read #1" must_== bytes and (
        bson.byteArray aka "read #2" must_== bytes)
    }
  }

  "BSONTimestamp" should {
    "extract time and ordinal values" in {
      val ts = BSONTimestamp(6065270725701271558L)

      ts.value aka "raw value" must_== 6065270725701271558L and (
        ts.time aka "time" must_== 1412180887L) and (
          ts.ordinal aka "ordinal" must_== 6)
    }

    "be created from the time and ordinal values" in {
      BSONTimestamp(1412180887L, 6) must_== BSONTimestamp(6065270725701271558L)
    }
  }
}
