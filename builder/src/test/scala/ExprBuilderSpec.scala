package reactivemongo.api.bson.builder

import reactivemongo.api.bson.{
  BSONArray,
  BSONDocument,
  BSONInteger,
  BSONString
}

final class ExprBuilderSpec
    extends org.specs2.mutable.Specification
    with ExprBuilderSpecCompat {

  "Expression builder".title

  "Empty builder" should {
    "be created for any type" in {
      val builder = ExprBuilder.empty[Foo]

      builder must not(beNull)
    }
  }

  "Expr" should {
    "be created from unsafe BSON value" in {
      val bsonValue = BSONString("test")
      val expr = Expr.unsafe[Foo, String](bsonValue)

      expr must not(beNull) and {
        expr.writes() must beSuccessfulTry(bsonValue)
      }
    }

    "be created from unsafe BSON document" in {
      val bsonDoc = BSONDocument(f"$$toUpper" -> "$$name")
      val expr = Expr.unsafe[Foo, String](bsonDoc)

      expr must not(beNull) and {
        expr.writes() must beSuccessfulTry(bsonDoc)
      }
    }

    "have implicit BSONWriter" in {
      val expr = Expr.unsafe[Foo, String](BSONString("test"))
      val writer =
        implicitly[reactivemongo.api.bson.BSONWriter[Expr[Foo, String]]]

      writer must not(beNull) and {
        writer.writeTry(expr) must beSuccessfulTry(BSONString("test"))
      }
    }
  }

  "concat" should {
    "concatenate two string expressions" in {
      val expr1 = Expr.unsafe[Foo, String](BSONString("Hello"))
      val expr2 = Expr.unsafe[Foo, String](BSONString(" World"))

      val concatExpr = ExprBuilder.empty[Foo].concat(expr1, expr2)

      concatExpr.writes() must beSuccessfulTry(
        BSONDocument(
          f"$$concat" -> BSONArray(
            BSONString("Hello"),
            BSONString(" World")
          )
        )
      )
    }

    "concatenate multiple string expressions" in {
      val expr1 = Expr.unsafe[Foo, String](BSONString("A"))
      val expr2 = Expr.unsafe[Foo, String](BSONString("B"))
      val expr3 = Expr.unsafe[Foo, String](BSONString("C"))
      val expr4 = Expr.unsafe[Foo, String](BSONString("D"))

      val concatExpr = ExprBuilder.empty[Foo].concat(expr1, expr2, expr3, expr4)

      concatExpr.writes() must beSuccessfulTry(
        BSONDocument(
          f"$$concat" -> BSONArray(
            BSONString("A"),
            BSONString("B"),
            BSONString("C"),
            BSONString("D")
          )
        )
      )
    }

    "concatenate field references" in {
      val fieldExpr = Expr.unsafe[Foo, String](BSONString("$$name"))
      val literalExpr = Expr.unsafe[Foo, String](BSONString(" suffix"))

      val concatExpr = ExprBuilder.empty[Foo].concat(fieldExpr, literalExpr)

      concatExpr.writes() must beSuccessfulTry(
        BSONDocument(
          f"$$concat" -> BSONArray(
            BSONString("$$name"),
            BSONString(" suffix")
          )
        )
      )
    }

    "work with single expression" in {
      val expr = Expr.unsafe[Foo, String](BSONString("Single"))

      val concatExpr = ExprBuilder.empty[Foo].concat(expr)

      concatExpr.writes() must beSuccessfulTry(
        BSONDocument(
          f"$$concat" -> BSONArray(BSONString("Single"))
        )
      )
    }
  }

  "from" should {
    "create expression from string value" in {
      val expr = ExprBuilder.empty[Foo].from("test")

      expr.writes() must beSuccessfulTry(BSONString("test"))
    }

    "create expression from int value" in {
      val expr = ExprBuilder.empty[Foo].from(42)

      expr.writes() must beSuccessfulTry(BSONInteger(42))
    }

    "create expression from sequence" in {
      val expr = ExprBuilder.empty[Foo].from(Seq("a", "b", "c"))

      expr.writes() must beSuccessfulTry(
        BSONArray(BSONString("a"), BSONString("b"), BSONString("c"))
      )
    }
  }

  "Arithmetic operators" should {
    val builder = ExprBuilder.empty[Foo]

    "add numbers" in {
      val expr1 = Expr.unsafe[Foo, Int](BSONInteger(10))
      val expr2 = Expr.unsafe[Foo, Int](BSONInteger(20))

      val result = builder.add(expr1, expr2)

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$add" -> BSONArray(BSONInteger(10), BSONInteger(20)))
      )
    }

    "subtract numbers" in {
      val expr1 = Expr.unsafe[Foo, Int](BSONInteger(50))
      val expr2 = Expr.unsafe[Foo, Int](BSONInteger(20))

      val result = builder.subtract(expr1, expr2)

      result.writes() must beSuccessfulTry(
        BSONDocument(
          f"$$subtract" -> BSONArray(BSONInteger(50), BSONInteger(20))
        )
      )
    }

    "multiply numbers" in {
      val expr1 = Expr.unsafe[Foo, Int](BSONInteger(5))
      val expr2 = Expr.unsafe[Foo, Int](BSONInteger(3))

      val result = builder.multiply(expr1, expr2)

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$multiply" -> BSONArray(BSONInteger(5), BSONInteger(3)))
      )
    }

    "divide numbers" in {
      val expr1 = Expr.unsafe[Foo, Double](BSONDocument(f"$$field1" -> 1))
      val expr2 = Expr.unsafe[Foo, Double](BSONDocument(f"$$field2" -> 1))

      val result = builder.divide(expr1, expr2)

      result.writes() must beSuccessfulTry(
        BSONDocument(
          f"$$divide" -> BSONArray(
            BSONDocument(f"$$field1" -> 1),
            BSONDocument(f"$$field2" -> 1)
          )
        )
      )
    }

    "compute modulo" in {
      val expr1 = Expr.unsafe[Foo, Int](BSONInteger(10))
      val expr2 = Expr.unsafe[Foo, Int](BSONInteger(3))

      val result = builder.mod(expr1, expr2)

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$mod" -> BSONArray(BSONInteger(10), BSONInteger(3)))
      )
    }

    "compute absolute value" in {
      val expr = Expr.unsafe[Foo, Int](BSONInteger(-42))

      val result = builder.abs(expr)

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$abs" -> BSONInteger(-42))
      )
    }

    "compute ceil" in {
      val expr = Expr.unsafe[Foo, Double](BSONDocument(f"$$field" -> 1))

      val result = builder.ceil(expr)

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$ceil" -> BSONDocument(f"$$field" -> 1))
      )
    }

    "compute floor" in {
      val expr = Expr.unsafe[Foo, Double](BSONDocument(f"$$field" -> 1))

      val result = builder.floor(expr)

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$floor" -> BSONDocument(f"$$field" -> 1))
      )
    }

    "round to specified place" in {
      val expr = Expr.unsafe[Foo, Double](BSONDocument(f"$$field" -> 1))

      val result = builder.round(expr, 2)

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$round" -> BSONArray(BSONDocument(f"$$field" -> 1), 2))
      )
    }

    "compute square root" in {
      val expr = Expr.unsafe[Foo, Int](BSONInteger(16))

      val result = builder.sqrt(expr)

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$sqrt" -> BSONInteger(16))
      )
    }

    "compute power" in {
      val base = Expr.unsafe[Foo, Int](BSONInteger(2))
      val exp = Expr.unsafe[Foo, Int](BSONInteger(3))

      val result = builder.pow(base, exp)

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$pow" -> BSONArray(BSONInteger(2), BSONInteger(3)))
      )
    }
  }

  "Comparison operators" should {
    val builder = ExprBuilder.empty[Foo]

    "compare equality" in {
      val expr1 = Expr.unsafe[Foo, String](BSONString("test"))
      val expr2 = Expr.unsafe[Foo, String](BSONString("test"))

      val result = builder.eq(expr1, expr2)

      result.writes() must beSuccessfulTry(
        BSONDocument(
          f"$$eq" -> BSONArray(BSONString("test"), BSONString("test"))
        )
      )
    }

    "compare inequality" in {
      val expr1 = Expr.unsafe[Foo, String](BSONString("test1"))
      val expr2 = Expr.unsafe[Foo, String](BSONString("test2"))

      val result = builder.ne(expr1, expr2)

      result.writes() must beSuccessfulTry(
        BSONDocument(
          f"$$ne" -> BSONArray(BSONString("test1"), BSONString("test2"))
        )
      )
    }

    "compare greater than" in {
      val expr1 = Expr.unsafe[Foo, Int](BSONInteger(20))
      val expr2 = Expr.unsafe[Foo, Int](BSONInteger(10))

      val result = builder.gt(expr1, expr2)

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$gt" -> BSONArray(BSONInteger(20), BSONInteger(10)))
      )
    }

    "compare greater than or equal" in {
      val expr1 = Expr.unsafe[Foo, Int](BSONInteger(20))
      val expr2 = Expr.unsafe[Foo, Int](BSONInteger(20))

      val result = builder.gte(expr1, expr2)

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$gte" -> BSONArray(BSONInteger(20), BSONInteger(20)))
      )
    }

    "compare less than" in {
      val expr1 = Expr.unsafe[Foo, Int](BSONInteger(10))
      val expr2 = Expr.unsafe[Foo, Int](BSONInteger(20))

      val result = builder.lt(expr1, expr2)

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$lt" -> BSONArray(BSONInteger(10), BSONInteger(20)))
      )
    }

    "compare less than or equal" in {
      val expr1 = Expr.unsafe[Foo, Int](BSONInteger(20))
      val expr2 = Expr.unsafe[Foo, Int](BSONInteger(20))

      val result = builder.lte(expr1, expr2)

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$lte" -> BSONArray(BSONInteger(20), BSONInteger(20)))
      )
    }

    "compare values with cmp" in {
      val expr1 = Expr.unsafe[Foo, Int](BSONInteger(15))
      val expr2 = Expr.unsafe[Foo, Int](BSONInteger(20))

      val result = builder.cmp(expr1, expr2)

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$cmp" -> BSONArray(BSONInteger(15), BSONInteger(20)))
      )
    }

    "compare different ordered types for equality" in {
      val expr1 = Expr.unsafe[Foo, Int](BSONInteger(100))
      val expr2 =
        Expr.unsafe[Foo, Long](BSONDocument(f"$$toLong" -> f"$$value"))

      val result = builder.eq(expr1, expr2)

      result.writes() must beSuccessfulTry(
        BSONDocument(
          f"$$eq" -> BSONArray(
            BSONInteger(100),
            BSONDocument(f"$$toLong" -> f"$$value")
          )
        )
      )
    }
  }

  "Logical operators" should {
    val builder = ExprBuilder.empty[Foo]

    "combine expressions with and" in {
      val expr1 = Expr.unsafe[Foo, Boolean](BSONDocument(f"$$expr1" -> 1))
      val expr2 = Expr.unsafe[Foo, Boolean](BSONDocument(f"$$expr2" -> 1))

      val result = builder.and(expr1, expr2)

      result.writes() must beSuccessfulTry(
        BSONDocument(
          f"$$and" -> BSONArray(
            BSONDocument(f"$$expr1" -> 1),
            BSONDocument(f"$$expr2" -> 1)
          )
        )
      )
    }

    "combine expressions with or" in {
      val expr1 = Expr.unsafe[Foo, Boolean](BSONDocument(f"$$expr1" -> 1))
      val expr2 = Expr.unsafe[Foo, Boolean](BSONDocument(f"$$expr2" -> 1))

      val result = builder.or(expr1, expr2)

      result.writes() must beSuccessfulTry(
        BSONDocument(
          f"$$or" -> BSONArray(
            BSONDocument(f"$$expr1" -> 1),
            BSONDocument(f"$$expr2" -> 1)
          )
        )
      )
    }

    "negate expression with not" in {
      val expr = Expr.unsafe[Foo, Boolean](BSONDocument(f"$$expr" -> 1))

      val result = builder.not(expr)

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$not" -> BSONDocument(f"$$expr" -> 1))
      )
    }
  }

  "String operators" should {
    val builder = ExprBuilder.empty[Foo]

    "extract substring" in {
      val expr = Expr.unsafe[Foo, String](BSONString("Hello World"))

      val result = builder.substr(expr, 0, 5)

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$substr" -> BSONArray(BSONString("Hello World"), 0, 5))
      )
    }

    "convert to lowercase" in {
      val expr = Expr.unsafe[Foo, String](BSONString("HELLO"))

      val result = builder.toLower(expr)

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$toLower" -> BSONString("HELLO"))
      )
    }

    "convert to uppercase" in {
      val expr = Expr.unsafe[Foo, String](BSONString("hello"))

      val result = builder.toUpper(expr)

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$toUpper" -> BSONString("hello"))
      )
    }

    "get string length" in {
      val expr = Expr.unsafe[Foo, String](BSONString("test"))

      val result = builder.strLen(expr)

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$strLen" -> BSONString("test"))
      )
    }

    "trim whitespace" in {
      val expr = Expr.unsafe[Foo, String](BSONString("  test  "))

      val result = builder.trim(expr)

      result.writes() must beSuccessfulTry(
        BSONDocument(
          f"$$trim" -> BSONDocument("input" -> BSONString("  test  "))
        )
      )
    }

    "split string" in {
      val expr = Expr.unsafe[Foo, String](BSONString("a,b,c"))

      val result = builder.split(expr, ",")

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$split" -> BSONArray(BSONString("a,b,c"), ","))
      )
    }

    "find substring index" in {
      val expr = Expr.unsafe[Foo, String](BSONString("test string"))

      val result = builder.indexOfBytes(expr, "string", 0)

      result.writes() must beSuccessfulTry(
        BSONDocument(
          f"$$indexOfBytes" -> BSONArray(BSONString("test string"), "string", 0)
        )
      )
    }

    "replace all occurrences" in {
      val expr = Expr.unsafe[Foo, String](BSONString("test test"))

      val result = builder.replaceAll(expr, "test", "new")

      result.writes() must beSuccessfulTry(
        BSONDocument(
          f"$$replaceAll" -> BSONDocument(
            "input" -> BSONString("test test"),
            "find" -> "test",
            "replacement" -> "new"
          )
        )
      )
    }

    "match regex pattern" in {
      val expr = Expr.unsafe[Foo, String](BSONString("test123"))

      val result = builder.regexMatch(expr, "\\\\d+")

      result.writes() must beSuccessfulTry(
        BSONDocument(
          f"$$regexMatch" -> BSONDocument(
            "input" -> BSONString("test123"),
            "regex" -> "\\\\d+"
          )
        )
      )
    }
  }

  "Array operators" should {
    val builder = ExprBuilder.empty[Foo]

    "get array element at index" in {
      val array = Expr.unsafe[Foo, Seq[String]](BSONArray("a", "b", "c"))
      val result = builder.arrayElemAt(array, 1)

      result.writes() must beSuccessfulTry(
        BSONDocument(
          f"$$arrayElemAt" -> BSONArray(BSONArray("a", "b", "c"), 1)
        )
      )
    }

    "concatenate arrays" in {
      val arr1 = Expr.unsafe[Foo, Seq[String]](BSONArray("a", "b"))
      val arr2 = Expr.unsafe[Foo, Seq[String]](BSONArray("c", "d"))

      val result = builder.concatArrays(arr1, arr2)

      result.writes() must beSuccessfulTry(
        BSONDocument(
          f"$$concatArrays" -> BSONArray(
            BSONArray("a", "b"),
            BSONArray("c", "d")
          )
        )
      )
    }

    "get array size" in {
      val array = Expr.unsafe[Foo, Seq[String]](BSONArray("a", "b", "c"))

      val result = builder.size(array)

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$size" -> BSONArray("a", "b", "c"))
      )
    }

    "slice array" in {
      val array = Expr.unsafe[Foo, Seq[Int]](BSONArray(1, 2, 3, 4, 5))

      val result = builder.slice(array, 2)

      result.writes() must beSuccessfulTry(
        BSONDocument(
          f"$$slice" -> BSONArray(BSONArray(1, 2, 3, 4, 5), 2)
        )
      )
    }

    "slice array with position and length" in {
      val array = Expr.unsafe[Foo, Seq[Int]](BSONArray(1, 2, 3, 4, 5))

      val result = builder.slice(array, 1, 2)

      result.writes() must beSuccessfulTry(
        BSONDocument(
          f"$$slice" -> BSONArray(BSONArray(1, 2, 3, 4, 5), 1, 2)
        )
      )
    }

    "check value in array" in {
      val value = Expr.unsafe[Foo, String](BSONString("test"))
      val array = Expr.unsafe[Foo, Seq[String]](BSONArray("a", "test", "b"))

      val result = builder.in(value, array)

      result.writes() must beSuccessfulTry(
        BSONDocument(
          f"$$in" -> BSONArray(
            BSONString("test"),
            BSONArray("a", "test", "b")
          )
        )
      )
    }

    "check set is subset" in {
      val subset = Expr.unsafe[Foo, Seq[String]](BSONArray("a", "b"))
      val superset = Expr.unsafe[Foo, Seq[String]](BSONArray("a", "b", "c"))

      val result = builder.setIsSubset(subset, superset)

      result.writes() must beSuccessfulTry(
        BSONDocument(
          f"$$setIsSubset" -> BSONArray(
            BSONArray("a", "b"),
            BSONArray("a", "b", "c")
          )
        )
      )
    }

    "get set intersection" in {
      val set1 = Expr.unsafe[Foo, Seq[String]](BSONArray("a", "b", "c"))
      val set2 = Expr.unsafe[Foo, Seq[String]](BSONArray("b", "c", "d"))

      val result = builder.setIntersection(set1, set2)

      result.writes() must beSuccessfulTry(
        BSONDocument(
          f"$$setIntersection" -> BSONArray(
            BSONArray("a", "b", "c"),
            BSONArray("b", "c", "d")
          )
        )
      )
    }

    "get set union" in {
      val set1 = Expr.unsafe[Foo, Seq[String]](BSONArray("a", "b"))
      val set2 = Expr.unsafe[Foo, Seq[String]](BSONArray("c", "d"))

      val result = builder.setUnion(set1, set2)

      result.writes() must beSuccessfulTry(
        BSONDocument(
          f"$$setUnion" -> BSONArray(
            BSONArray("a", "b"),
            BSONArray("c", "d")
          )
        )
      )
    }

    "get set difference" in {
      val set1 = Expr.unsafe[Foo, Seq[String]](BSONArray("a", "b", "c"))
      val set2 = Expr.unsafe[Foo, Seq[String]](BSONArray("b"))

      val result = builder.setDifference(set1, set2)

      result.writes() must beSuccessfulTry(
        BSONDocument(
          f"$$setDifference" -> BSONArray(
            BSONArray("a", "b", "c"),
            BSONArray("b")
          )
        )
      )
    }

    "get first element" in {
      val array = Expr.unsafe[Foo, Seq[String]](BSONArray("a", "b", "c"))

      val result = builder.first(array)

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$first" -> BSONArray("a", "b", "c"))
      )
    }

    "get last element" in {
      val array = Expr.unsafe[Foo, Seq[String]](BSONArray("a", "b", "c"))

      val result = builder.last(array)

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$last" -> BSONArray("a", "b", "c"))
      )
    }

    "reverse array" in {
      val array = Expr.unsafe[Foo, Seq[String]](BSONArray("a", "b", "c"))

      val result = builder.reverseArray(array)

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$reverseArray" -> BSONArray("a", "b", "c"))
      )
    }

    "create range" in {
      val result = builder.range(0, 10, 2)

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$range" -> BSONArray(0, 10, 2))
      )
    }
  }

  "Date operators" should {
    val builder = ExprBuilder.empty[Foo]
    val dateExpr =
      Expr.unsafe[Foo, java.util.Date](BSONDocument(f"$$date" -> 1))

    "extract year" in {
      val result = builder.year(dateExpr)

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$year" -> BSONDocument(f"$$date" -> 1))
      )
    }

    "extract month" in {
      val result = builder.month(dateExpr)

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$month" -> BSONDocument(f"$$date" -> 1))
      )
    }

    "extract day of month" in {
      val result = builder.dayOfMonth(dateExpr)

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$dayOfMonth" -> BSONDocument(f"$$date" -> 1))
      )
    }

    "extract day of week" in {
      val result = builder.dayOfWeek(dateExpr)

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$dayOfWeek" -> BSONDocument(f"$$date" -> 1))
      )
    }

    "extract hour" in {
      val result = builder.hour(dateExpr)

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$hour" -> BSONDocument(f"$$date" -> 1))
      )
    }

    "extract minute" in {
      val result = builder.minute(dateExpr)

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$minute" -> BSONDocument(f"$$date" -> 1))
      )
    }

    "extract second" in {
      val result = builder.second(dateExpr)

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$second" -> BSONDocument(f"$$date" -> 1))
      )
    }

    "extract millisecond" in {
      val result = builder.millisecond(dateExpr)

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$millisecond" -> BSONDocument(f"$$date" -> 1))
      )
    }

    "convert date to string" in {
      val result = builder.dateToString(dateExpr, "%Y-%m-%d")

      result.writes() must beSuccessfulTry(
        BSONDocument(
          f"$$dateToString" -> BSONDocument(
            "date" -> BSONDocument(f"$$date" -> 1),
            "format" -> "%Y-%m-%d"
          )
        )
      )
    }
  }

  "Conditional operators" should {
    val builder = ExprBuilder.empty[Foo]

    "evaluate cond expression" in {
      val condition =
        Expr.unsafe[Foo, Boolean](
          BSONDocument(f"$$gt" -> BSONArray(f"$$field", 10))
        )
      val ifTrue = Expr.unsafe[Foo, String](BSONString("high"))
      val ifFalse = Expr.unsafe[Foo, String](BSONString("low"))

      val result = builder.cond(condition, ifTrue, ifFalse)

      result.writes() must beSuccessfulTry(
        BSONDocument(
          f"$$cond" -> BSONDocument(
            "if" -> BSONDocument(f"$$gt" -> BSONArray(f"$$field", 10)),
            "then" -> BSONString("high"),
            "else" -> BSONString("low")
          )
        )
      )
    }

    "evaluate ifNull expression" in {
      val expr = Expr.unsafe[Foo, String](BSONDocument(f"$$field" -> 1))
      val replacement = Expr.unsafe[Foo, String](BSONString("default"))

      val result = builder.ifNull(expr, replacement)

      result.writes() must beSuccessfulTry(
        BSONDocument(
          f"$$ifNull" -> BSONArray(
            BSONDocument(f"$$field" -> 1),
            BSONString("default")
          )
        )
      )
    }

    "evaluate switch expression" in {
      val cond1 =
        Expr.unsafe[Foo, Boolean](
          BSONDocument(f"$$gt" -> BSONArray(f"$$field", 100))
        )
      val val1 = Expr.unsafe[Foo, String](BSONString("high"))
      val cond2 =
        Expr.unsafe[Foo, Boolean](
          BSONDocument(f"$$gt" -> BSONArray(f"$$field", 50))
        )
      val val2 = Expr.unsafe[Foo, String](BSONString("medium"))
      val default = Expr.unsafe[Foo, String](BSONString("low"))

      val result = builder.switch(Seq((cond1, val1), (cond2, val2)), default)

      result.writes() must beSuccessfulTry(
        BSONDocument(
          f"$$switch" -> BSONDocument(
            "branches" -> BSONArray(
              BSONDocument(
                "case" -> BSONDocument(f"$$gt" -> BSONArray(f"$$field", 100)),
                "then" -> BSONString("high")
              ),
              BSONDocument(
                "case" -> BSONDocument(f"$$gt" -> BSONArray(f"$$field", 50)),
                "then" -> BSONString("medium")
              )
            ),
            "default" -> BSONString("low")
          )
        )
      )
    }
  }

  "Type conversion operators" should {
    val builder = ExprBuilder.empty[Foo]

    "get type of value" in {
      val expr = Expr.unsafe[Foo, Any](BSONString("test"))

      val result = builder.`type`(expr)

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$type" -> BSONString("test"))
      )
    }

    "convert to string" in {
      val expr = Expr.unsafe[Foo, Int](BSONInteger(42))

      val result = builder.toString(expr)

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$toString" -> BSONInteger(42))
      )
    }

    "convert to int" in {
      val expr = Expr.unsafe[Foo, String](BSONString("42"))

      val result = builder.toInt(expr)

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$toInt" -> BSONString("42"))
      )
    }

    "convert to double" in {
      val expr = Expr.unsafe[Foo, String](BSONString("3.14"))

      val result = builder.toDouble(expr)

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$toDouble" -> BSONString("3.14"))
      )
    }

    "convert to long" in {
      val expr = Expr.unsafe[Foo, String](BSONString("1234567890"))

      val result = builder.toLong(expr)

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$toLong" -> BSONString("1234567890"))
      )
    }

    "convert to bool" in {
      val expr = Expr.unsafe[Foo, Int](BSONInteger(1))

      val result = builder.toBool(expr)

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$toBool" -> BSONInteger(1))
      )
    }

    "convert with explicit type" in {
      val expr = Expr.unsafe[Foo, String](BSONString("42"))

      val result = builder.convert[String, Int](expr, "int")

      result.writes() must beSuccessfulTry(
        BSONDocument(
          f"$$convert" -> BSONDocument(
            "input" -> BSONString("42"),
            "to" -> "int"
          )
        )
      )
    }
  }

  "Aggregation operators" should {
    val builder = ExprBuilder.empty[Foo]

    "sum values" in {
      val expr = Expr.unsafe[Foo, Int](BSONDocument(f"$$field" -> 1))

      val result = builder.sum(expr)

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$sum" -> BSONDocument(f"$$field" -> 1))
      )
    }

    "average values" in {
      val expr = Expr.unsafe[Foo, Int](BSONDocument(f"$$field" -> 1))

      val result = builder.avg(expr)

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$avg" -> BSONDocument(f"$$field" -> 1))
      )
    }

    "find maximum value" in {
      val expr = Expr.unsafe[Foo, Int](BSONDocument(f"$$field" -> 1))

      val result = builder.max(expr)

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$max" -> BSONDocument(f"$$field" -> 1))
      )
    }

    "find maximum of multiple values" in {
      val expr1 = Expr.unsafe[Foo, Int](BSONInteger(10))
      val expr2 = Expr.unsafe[Foo, Int](BSONInteger(20))

      val result = builder.max(expr1, expr2)

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$max" -> BSONArray(BSONInteger(10), BSONInteger(20)))
      )
    }

    "find minimum value" in {
      val expr = Expr.unsafe[Foo, Int](BSONDocument(f"$$field" -> 1))

      val result = builder.min(expr)

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$min" -> BSONDocument(f"$$field" -> 1))
      )
    }

    "find minimum of multiple values" in {
      val expr1 = Expr.unsafe[Foo, Int](BSONInteger(10))
      val expr2 = Expr.unsafe[Foo, Int](BSONInteger(20))

      val result = builder.min(expr1, expr2)

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$min" -> BSONArray(BSONInteger(10), BSONInteger(20)))
      )
    }
  }

  "Object operators" should {
    val builder = ExprBuilder.empty[Foo]

    "merge objects" in {
      val obj1 = Expr.unsafe[Foo, BSONDocument](BSONDocument("a" -> 1))
      val obj2 = Expr.unsafe[Foo, BSONDocument](BSONDocument("b" -> 2))

      val result = builder.mergeObjects(obj1, obj2)

      result.writes() must beSuccessfulTry(
        BSONDocument(
          f"$$mergeObjects" -> BSONArray(
            BSONDocument("a" -> 1),
            BSONDocument("b" -> 2)
          )
        )
      )
    }

    "convert object to array" in {
      val doc = Expr.unsafe[Foo, BSONDocument](BSONDocument("a" -> 1, "b" -> 2))

      val result = builder.objectToArray(doc)

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$objectToArray" -> BSONDocument("a" -> 1, "b" -> 2))
      )
    }

    "convert array to object" in {
      val array = Expr.unsafe[Foo, Seq[BSONDocument]](
        BSONArray(
          BSONDocument("k" -> "a", "v" -> 1),
          BSONDocument("k" -> "b", "v" -> 2)
        )
      )

      val result = builder.arrayToObject(array)

      result.writes() must beSuccessfulTry(
        BSONDocument(
          f"$$arrayToObject" -> BSONArray(
            BSONDocument("k" -> "a", "v" -> 1),
            BSONDocument("k" -> "b", "v" -> 2)
          )
        )
      )
    }
  }

  "Miscellaneous operators" should {
    val builder = ExprBuilder.empty[Foo]

    "create literal value" in {
      val result = builder.literal(f"$$field")

      result.writes() must beSuccessfulTry(
        BSONDocument(f"$$literal" -> f"$$field")
      )
    }
  }
}
