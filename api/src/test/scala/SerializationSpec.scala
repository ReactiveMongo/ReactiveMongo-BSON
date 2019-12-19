package reactivemongo
package api.bson

import reactivemongo.api.bson.buffer._

import org.specs2.specification.core.Fragments

final class SerializationSpec extends org.specs2.mutable.Specification {
  "Serialization" title

  import SerializationFixtures._
  import DefaultBufferHandler.{ readDocument, writeArray, writeDocument }

  "Buffer" should {
    "write bytes" in {
      val buf = WritableBuffer.empty.writeBytes(expectedWholeDocumentBytes)

      buf.size must_=== expectedWholeDocumentBytes.size and {
        buf.array must_=== expectedWholeDocumentBytes
      } and {
        val readable = buf.toReadableBuffer

        readable.size must_=== buf.size and {
          val bytes = Array.ofDim[Byte](buf.size)

          readable.readBytes(bytes) must_=== readable and {
            bytes must_=== expectedWholeDocumentBytes
          }
        }
      }
    }

    "write BSON string" in {
      val str = "étoile du nord"
      val bytes = Array[Byte](16, 0, 0, 0, -61, -87, 116, 111, 105, 108, 101, 32, 100, 117, 32, 110, 111, 114, 100, 0)

      val buf = WritableBuffer.empty

      buf.writeBsonString(str) must_=== buf and {
        buf.array must_=== bytes
      } and {
        buf.toReadableBuffer.readBsonString() must_=== str
      }
    }

    "write C string" in {
      val str = "étoile du nord"
      val bytes = Array[Byte](-61, -87, 116, 111, 105, 108, 101, 32, 100, 117, 32, 110, 111, 114, 100, 0)

      val buf = WritableBuffer.empty

      buf.writeCString(str) must_=== buf and {
        buf.array must_=== bytes
      } and {
        buf.toReadableBuffer.readCString() must_=== str
      }
    }

    "skip until condition" in {
      val buf = WritableBuffer.empty.
        writeCString("foo").writeInt(13).toReadableBuffer

      buf.skipUntil(_ == (0x0: Byte)).readInt() must_=== 13
    }

    "split after n bytes" in {
      val buf = WritableBuffer.empty.
        writeInt(21).writeLong(3L).toReadableBuffer

      buf.splitAt(4) must beLike {
        case (before, `buf`) => before.readInt() must_=== 21 and {
          buf.readLong() must_=== 3L
        }
      }
    }
  }

  "BSON default serializer" should {
    def codec(expected: BSONValue): Option[BSONValue] = scala.util.Try {
      val buffer = DefaultBufferHandler.serialize(
        expected, WritableBuffer.empty).toReadableBuffer

      DefaultBufferHandler.readValue(buffer, expected.byteCode)
    }.toOption

    "serialize a BSON decimal" in {
      BSONDecimal.fromLong(123L) must beSuccessfulTry[BSONDecimal].like {
        case expected => codec(expected) must_=== Some(expected)
      }
    }

    "serialize an array" in {
      val arr = BSONArray(
        Option.empty[String],
        BSONBoolean(true),
        Option.empty[String])

      val expected = Array[Byte](9, 0, 0, 0, 8, 48, 0, 1, 0)
      val buffer = WritableBuffer.empty

      writeArray(arr.values, buffer) must_=== buffer and {
        buffer.array must_=== expected
      }
    }

    "serialize documents" >> {
      "with complex structure" in {
        val buffer = WritableBuffer.empty

        writeDocument(wholeDoc, buffer) must_=== buffer and {
          val doc = readDocument(buffer.toReadableBuffer)

          doc must_=== wholeDoc and {
            doc.elements must_=== wholeDoc.elements // elements in same order
          }
        } and {
          buffer.array must_=== expectedWholeDocumentBytes
        }
      }

      "when containing a boolean" in {
        val dbool = BSONDocument("boo" -> BSONBoolean(true))
        val expected = Array[Byte](11, 0, 0, 0, 8, 98, 111, 111, 0, 1, 0)
        val buffer = WritableBuffer.empty

        writeDocument(dbool, buffer) must_=== buffer and {
          buffer.array must_=== expected
        } and {
          readDocument(buffer.toReadableBuffer) must_=== dbool
        }
      }

      "when containing a double" in {
        val ddoub = BSONDocument("doo" -> BSONDouble(9))
        val expected = Array[Byte](18, 0, 0, 0, 1, 100, 111, 111, 0, 0, 0, 0, 0, 0, 0, 34, 64, 0)
        val buffer = WritableBuffer.empty

        writeDocument(ddoub, buffer) must_=== buffer and {
          buffer.array must_=== expected
        }
      }

      "when containing an integer" in {
        val dint = BSONDocument("int" -> BSONInteger(8))
        val expected = Array[Byte](14, 0, 0, 0, 16, 105, 110, 116, 0, 8, 0, 0, 0, 0)
        val buffer = WritableBuffer.empty

        writeDocument(dint, buffer) must_=== (buffer) and {
          buffer.array must_=== expected
        }
      }

      "when containing a long" in {
        val dlong = BSONDocument("long" -> BSONLong(8888122134234L))
        val expected = Array[Byte](19, 0, 0, 0, 18, 108, 111, 110, 103, 0, -38, -50, 92, 109, 21, 8, 0, 0, 0)
        val buffer = WritableBuffer.empty

        writeDocument(dlong, buffer) must_=== (buffer) and {
          buffer.array must_=== expected
        }
      }

      "when containing a string" in {
        val dstr = BSONDocument("str" -> BSONString("étoile du nord"))
        val expected = Array[Byte](30, 0, 0, 0, 2, 115, 116, 114, 0, 16, 0, 0, 0, -61, -87, 116, 111, 105, 108, 101, 32, 100, 117, 32, 110, 111, 114, 100, 0, 0)
        val buffer = WritableBuffer.empty

        writeDocument(dstr, buffer) must_=== buffer and {
          buffer.array must_=== expected
        } and {
          readDocument(buffer.toReadableBuffer) must_=== dstr
        }
      }

      "when containing a binary and a double" in {
        val bin = BSONBinary(
          Array[Byte](4, 5, 6, 7, 8), Subtype.GenericBinarySubtype)

        val doc = BSONDocument("blob" -> bin, "v" -> BSONDouble(1.23D))
        val buffer = WritableBuffer.empty

        writeDocument(doc, buffer) must_=== buffer and {
          readDocument(buffer.toReadableBuffer) must_=== doc
        }
      }

      "when containing a document containing a document" in {
        val docdoc = BSONDocument("doc" -> BSONDocument("str" -> BSONString("strv")))
        val expected = Array[Byte](29, 0, 0, 0, 3, 100, 111, 99, 0, 19, 0, 0, 0, 2, 115, 116, 114, 0, 5, 0, 0, 0, 115, 116, 114, 118, 0, 0, 0)
        val buffer = WritableBuffer.empty

        writeDocument(docdoc, buffer) must_=== buffer and {
          buffer.array must_=== expected
        }
      }

      "when containing an array" in {
        val docarray = BSONDocument("contact" -> BSONDocument(
          "emails" -> BSONArray(
            BSONString("james@example.org"),
            BSONString("spamaddrjames@example.org")),
          "address" -> BSONString("coucou")))
        val expected = Array[Byte](110, 0, 0, 0, 3, 99, 111, 110, 116, 97, 99, 116, 0, 96, 0, 0, 0, 4, 101, 109, 97, 105, 108, 115, 0, 63, 0, 0, 0, 2, 48, 0, 18, 0, 0, 0, 106, 97, 109, 101, 115, 64, 101, 120, 97, 109, 112, 108, 101, 46, 111, 114, 103, 0, 2, 49, 0, 26, 0, 0, 0, 115, 112, 97, 109, 97, 100, 100, 114, 106, 97, 109, 101, 115, 64, 101, 120, 97, 109, 112, 108, 101, 46, 111, 114, 103, 0, 0, 2, 97, 100, 100, 114, 101, 115, 115, 0, 7, 0, 0, 0, 99, 111, 117, 99, 111, 117, 0, 0, 0)
        val buffer = WritableBuffer.empty

        writeDocument(docarray, buffer) must_=== (buffer) and {
          buffer.array must_=== expected
        } and {
          readDocument(buffer.toReadableBuffer) must_=== docarray
        }
      }

      "when duplicate in binary representation" >> {
        val binRepr = Array[Byte](44, 0, 0, 0, 16, 108, 111, 114, 101, 109, 0, 1, 0, 0, 0, 2, 102, 111, 111, 0, 4, 0, 0, 0, 98, 97, 114, 0, 1, 108, 111, 114, 101, 109, 0, -102, -103, -103, -103, -103, -103, 11, 64, 0)

        val doc = BSONDocument("lorem" -> 1, "foo" -> "bar", "lorem" -> 3.45D)
        lazy val strictDoc = doc.asStrict

        "with duplicate preserved using default handlers" in {
          readDocument(ReadableBuffer(binRepr)) must_=== doc
        }

        "without duplicate field in document" in {
          val strictHandlers = new BufferHandler with StrictBufferHandler

          strictHandlers.readDocument(
            ReadableBuffer(binRepr)) must_=== strictDoc
        }
      }
    }
  }

  "BSON Default Deserializer" should {
    "deserialize a complex document" in {
      val buffer = ReadableBuffer(expectedWholeDocumentBytes)

      readDocument(buffer) must_=== wholeDoc
    }

    Fragments.foreach(BSONValueFixtures.bsonJSWsFixtures) { jsWs =>
      s"de/serialize $jsWs" in {
        val buffer = WritableBuffer.empty.writeByte(jsWs.code)

        val readable = DefaultBufferHandler.
          serialize(jsWs, buffer).toReadableBuffer

        DefaultBufferHandler.
          deserialize(readable) must beSuccessfulTry[BSONValue].like {
            case res => res must_== jsWs and {
              res.code must_=== jsWs.code
            }
          }
      }
    }
  }

  "Code" should {
    Fragments.foreach(BSONValueFixtures.bsonValueFixtures) { v =>
      s"be consistent for $v" in {
        v.code.toByte must_=== v.byteCode
      }
    }
  }

  "Byte size" should {
    import BSONValueFixtures._

    def written(v: BSONValue): Int = {
      val buf = WritableBuffer.empty
      DefaultBufferHandler.serialize(v, buf)
      buf.size
    }

    "be 0 for any BSON constant" >> {
      Fragments.foreach(bsonConstFixtures) { bsonConst =>
        s"like $bsonConst" in {
          bsonConst.byteSize must_=== 0 and {
            written(bsonConst) must_=== 0
          }
        }
      }
    }

    "be the Double one (8) for any BSONDouble" >> {
      Fragments.foreach(bsonDoubleFixtures) { bsonDouble =>
        s"like $bsonDouble" in {
          bsonDouble.byteSize must_=== 8 and {
            written(bsonDouble) must_=== 8
          }
        }
      }
    }

    "be the Long one (8) for any BSONLong" >> {
      Fragments.foreach(bsonLongFixtures) { bsonLong =>
        s"like $bsonLong" in {
          bsonLong.byteSize must_=== 8 and {
            written(bsonLong) must_=== 8
          }
        }
      }
    }

    "be the Long one (8) for any BSONDateTime" >> {
      Fragments.foreach(bsonDateTimeFixtures) { bsonDateTime =>
        s"like $bsonDateTime" in {
          bsonDateTime.byteSize must_=== 8 and {
            written(bsonDateTime) must_=== 8
          }
        }
      }
    }

    "be the Long one (8) for any BSONTimestamp" >> {
      Fragments.foreach(bsonTsFixtures) { bsonTimestamp =>
        s"like $bsonTimestamp" in {
          bsonTimestamp.byteSize must_=== 8 and {
            written(bsonTimestamp) must_=== 8
          }
        }
      }
    }

    "be the Int one (4) for any BSONInteger" >> {
      Fragments.foreach(bsonIntFixtures) { bsonInt =>
        s"like $bsonInt" in {
          bsonInt.byteSize must_=== 4 and {
            written(bsonInt) must_=== 4
          }
        }
      }
    }

    "be the Boolean one (1) for any BSONBoolean" >> {
      Fragments.foreach(bsonBoolFixtures) { bsonBool =>
        s"like $bsonBool" in {
          bsonBool.byteSize must_=== 1 and {
            written(bsonBool) must_=== 1
          }
        }
      }
    }

    "be the OID one (12) for any BSONObjectID" >> {
      Fragments.foreach(bsonOidFixtures) { bsonOid =>
        s"like $bsonOid" in {
          bsonOid.byteSize must_=== 12 and {
            written(bsonOid) must_=== 12
          }
        }
      }
    }

    "be the expected one for any BSONString" >> {
      Fragments.foreach(bsonStrFixtures zip bsonStrByteSizes) {
        case (bsonStr, byteSize) =>
          s"as $byteSize for $bsonStr" in {
            bsonStr.byteSize must_=== byteSize and {
              written(bsonStr) must_=== byteSize
            }
          }
      }
    }

    "be the expected one for any BSONRegex" >> {
      Fragments.foreach(bsonRegexFixtures zip bsonRegexByteSizes) {
        case (bsonRegex, byteSize) =>
          s"as $byteSize for $bsonRegex" in {
            bsonRegex.byteSize must_=== byteSize and {
              written(bsonRegex) must_=== byteSize
            }
          }
      }
    }

    "be the expected one for any BSONBinary" >> {
      Fragments.foreach(bsonBinFixtures zip bsonBinByteSizes) {
        case (bsonBin, byteSize) =>
          s"as $byteSize for $bsonBin" in {
            bsonBin.byteSize must_=== byteSize and {
              written(bsonBin) must_=== byteSize
            }
          }
      }
    }

    "be the expected one for any BSONJavaScript" >> {
      Fragments.foreach(bsonJSFixtures zip bsonJSByteSizes) {
        case (bsonJs, byteSize) =>
          s"as $byteSize for $bsonJs" in {
            bsonJs.byteSize must_=== byteSize and {
              written(bsonJs) must_=== byteSize
            }
          }
      }
    }

    "be the expected one for any BSONJavaScriptWS" >> {
      Fragments.foreach(bsonJSWsFixtures zip bsonJSWSByteSizes) {
        case (bsonJsws, byteSize) =>
          s"as $byteSize for $bsonJsws" in {
            bsonJsws.byteSize must_=== byteSize and {
              written(bsonJsws) must_=== byteSize
            }
          }
      }
    }

    "be the expected one for any BSONArray" >> {
      Fragments.foreach(bsonArrayFixtures zip bsonArrayByteSizes) {
        case (bsonArray, byteSize) =>
          s"as $byteSize for $bsonArray" in {
            bsonArray.byteSize must_=== byteSize and {
              written(bsonArray) must_=== byteSize
            }
          }
      }
    }

    "be the expected one for any BSONDocument" >> {
      Fragments.foreach(bsonDocFixtures zip bsonDocByteSizes) {
        case (bsonDoc, byteSize) =>
          s"as $byteSize for $bsonDoc" in {
            bsonDoc.byteSize must_=== byteSize and {
              written(bsonDoc) must_=== byteSize
            }
          }
      }
    }

    "be 16 for any BSONDecimal" >> {
      Fragments.foreach(bsonDecimalFixtures) { bsonDec =>
        s"like $bsonDec" in {
          bsonDec.byteSize must_=== 16 and {
            written(bsonDec) must_=== 16
          }
        }
      }
    }
  }
}

object SerializationFixtures {
  lazy val ismaster = Array[Byte](-72, 1, 0, 0, 2, 115, 101, 116, 78, 97, 109, 101, 0, 7, 0, 0, 0, 114, 101, 97, 99, 116, 109, 0, 16, 115, 101, 116, 86, 101, 114, 115, 105, 111, 110, 0, 3, 0, 0, 0, 8, 105, 115, 109, 97, 115, 116, 101, 114, 0, 1, 8, 115, 101, 99, 111, 110, 100, 97, 114, 121, 0, 0, 4, 104, 111, 115, 116, 115, 0, -122, 0, 0, 0, 2, 48, 0, 36, 0, 0, 0, 83, 116, 101, 112, 104, 97, 110, 101, 115, 45, 77, 97, 99, 66, 111, 111, 107, 45, 80, 114, 111, 45, 50, 46, 108, 111, 99, 97, 108, 58, 50, 55, 48, 49, 55, 0, 2, 49, 0, 36, 0, 0, 0, 83, 116, 101, 112, 104, 97, 110, 101, 115, 45, 77, 97, 99, 66, 111, 111, 107, 45, 80, 114, 111, 45, 50, 46, 108, 111, 99, 97, 108, 58, 50, 55, 48, 49, 57, 0, 2, 50, 0, 36, 0, 0, 0, 83, 116, 101, 112, 104, 97, 110, 101, 115, 45, 77, 97, 99, 66, 111, 111, 107, 45, 80, 114, 111, 45, 50, 46, 108, 111, 99, 97, 108, 58, 50, 55, 48, 49, 56, 0, 0, 2, 112, 114, 105, 109, 97, 114, 121, 0, 36, 0, 0, 0, 83, 116, 101, 112, 104, 97, 110, 101, 115, 45, 77, 97, 99, 66, 111, 111, 107, 45, 80, 114, 111, 45, 50, 46, 108, 111, 99, 97, 108, 58, 50, 55, 48, 49, 55, 0, 2, 109, 101, 0, 36, 0, 0, 0, 83, 116, 101, 112, 104, 97, 110, 101, 115, 45, 77, 97, 99, 66, 111, 111, 107, 45, 80, 114, 111, 45, 50, 46, 108, 111, 99, 97, 108, 58, 50, 55, 48, 49, 55, 0, 16, 109, 97, 120, 66, 115, 111, 110, 79, 98, 106, 101, 99, 116, 83, 105, 122, 101, 0, 0, 0, 0, 1, 16, 109, 97, 120, 77, 101, 115, 115, 97, 103, 101, 83, 105, 122, 101, 66, 121, 116, 101, 115, 0, 0, 108, -36, 2, 16, 109, 97, 120, 87, 114, 105, 116, 101, 66, 97, 116, 99, 104, 83, 105, 122, 101, 0, -24, 3, 0, 0, 9, 108, 111, 99, 97, 108, 84, 105, 109, 101, 0, 6, 89, 36, -79, 72, 1, 0, 0, 16, 109, 97, 120, 87, 105, 114, 101, 86, 101, 114, 115, 105, 111, 110, 0, 2, 0, 0, 0, 16, 109, 105, 110, 87, 105, 114, 101, 86, 101, 114, 115, 105, 111, 110, 0, 0, 0, 0, 0, 1, 111, 107, 0, 0, 0, 0, 0, 0, 0, -16, 63, 0)

  lazy val cpxDoc = Array[Byte](73, 1, 0, 0, 7, 95, 105, 100, 0, 84, 37, 32, -44, 86, 16, 26, 53, 33, 121, 71, -94, 2, 110, 97, 109, 101, 0, 5, 0, 0, 0, 106, 97, 99, 107, 0, 4, 99, 111, 110, 116, 97, 99, 116, 0, 113, 0, 0, 0, 3, 48, 0, 52, 0, 0, 0, 2, 116, 112, 101, 0, 4, 0, 0, 0, 116, 101, 108, 0, 2, 110, 117, 109, 0, 11, 0, 0, 0, 56, 55, 54, 56, 49, 55, 50, 54, 51, 56, 0, 1, 116, 114, 117, 99, 0, 0, 0, 0, 0, 0, 0, 88, 64, 0, 3, 49, 0, 50, 0, 0, 0, 2, 116, 112, 101, 0, 4, 0, 0, 0, 112, 114, 111, 0, 2, 110, 117, 109, 0, 9, 0, 0, 0, 48, 57, 56, 55, 49, 48, 57, 50, 0, 1, 116, 114, 117, 99, 0, -51, -52, -52, -52, -52, 76, 49, -64, 0, 0, 2, 97, 100, 100, 114, 101, 115, 115, 0, 36, 0, 0, 0, 108, 107, 110, 99, 118, 101, 111, 119, 110, 118, 101, 111, 119, 110, 118, 32, 119, 111, 105, 118, 110, 101, 119, 59, 111, 118, 110, 32, 113, 39, 112, 106, 102, 110, 32, 0, 13, 102, 117, 110, 99, 0, 44, 0, 0, 0, 102, 117, 110, 99, 116, 105, 111, 110, 32, 40, 41, 32, 123, 32, 118, 97, 114, 32, 97, 32, 61, 32, 57, 57, 59, 32, 114, 101, 116, 117, 114, 110, 32, 104, 101, 121, 32, 43, 32, 57, 57, 32, 125, 0, 5, 100, 97, 116, 97, 0, 9, 0, 0, 0, 0, -115, -86, -33, -45, -35, 123, -37, -121, -32, 11, 114, 120, 0, 91, 97, 122, 93, 123, 52, 125, 0, 105, 0, 7, 105, 100, 50, 0, 84, 37, 32, -44, 86, 16, 26, 53, 33, 121, 71, -95, 2, 101, 110, 100, 0, 7, 0, 0, 0, 101, 110, 102, 105, 110, 33, 0, 0)

  lazy val expectedWholeDocumentBytes = Array[Byte](-18, 0, 0, 0, 2, 115, 117, 114, 110, 97, 109, 101, 49, 0, 4, 0, 0, 0, 74, 105, 109, 0, 18, 108, 97, 115, 116, 83, 101, 101, 110, 0, -21, 96, -32, -60, 60, 1, 0, 0, 2, 110, 97, 109, 101, 0, 6, 0, 0, 0, 74, 97, 109, 101, 115, 0, 8, 111, 110, 108, 105, 110, 101, 0, 1, 7, 95, 105, 100, 0, 81, 23, -58, 57, 26, -91, 98, -87, 0, -104, -10, 33, 1, 115, 99, 111, 114, 101, 0, 10, -41, -93, 112, 61, 10, 15, 64, 3, 99, 111, 110, 116, 97, 99, 116, 0, 122, 0, 0, 0, 19, 112, 114, 105, 111, 114, 105, 116, 121, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -8, 2, 97, 100, 100, 114, 101, 115, 115, 0, 7, 0, 0, 0, 99, 111, 117, 99, 111, 117, 0, 4, 101, 109, 97, 105, 108, 115, 0, 63, 0, 0, 0, 2, 48, 0, 18, 0, 0, 0, 106, 97, 109, 101, 115, 64, 101, 120, 97, 109, 112, 108, 101, 46, 111, 114, 103, 0, 2, 49, 0, 26, 0, 0, 0, 115, 112, 97, 109, 97, 100, 100, 114, 106, 97, 109, 101, 115, 64, 101, 120, 97, 109, 112, 108, 101, 46, 111, 114, 103, 0, 0, 0, 16, 97, 103, 101, 0, 27, 0, 0, 0, 0)

  lazy val wholeDoc = BSONDocument(
    // 's' fields before 'n' (name); to check order is kept
    "surname1" -> Some("Jim"),
    "surname2" -> Option.empty[String],
    "lastSeen" -> BSONLong(1360512704747L),
    "name" -> "James",
    "online" -> true,
    "_id" -> BSONObjectID.parse("5117c6391aa562a90098f621").get,
    "score" -> 3.88,
    "contact" -> BSONDocument(
      "priority" -> BSONDecimal.NegativeInf,
      "address" -> BSONString("coucou"),
      "emails" -> BSONArray(
        "james@example.org",
        Option.empty[String],
        Some("spamaddrjames@example.org"))),
    "age" -> 27)
}
