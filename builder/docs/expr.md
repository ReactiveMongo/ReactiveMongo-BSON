# Expressions

The `ExprBuilder` provides a type-safe way to construct MongoDB aggregation expressions. It ensures at compile-time that field references exist and that expression types are consistent, producing `Expr[T, V]` values that can be used in aggregation pipelines, projections, and updates.

## Table of Contents

- [Basic Setup](#basic-setup)
  - [Creating an ExprBuilder](#creating-an-exprbuilder)
  - [Field References with `select`](#field-references-with-select)
  - [Constant Values with `from`](#constant-values-with-from)
  - [Raw BSON with `Expr.unsafe`](#raw-bson-with-exprunsafe)
- [Arithmetic Operators](#arithmetic-operators)
  - [Add (`$add`)](#add-add)
  - [Subtract (`$subtract`)](#subtract-subtract)
  - [Multiply (`$multiply`)](#multiply-multiply)
  - [Divide (`$divide`)](#divide-divide)
  - [Modulo (`$mod`)](#modulo-mod)
  - [Abs, Ceil, Floor, Round](#abs-ceil-floor-round)
  - [Sqrt and Pow](#sqrt-and-pow)
- [Comparison Operators](#comparison-operators)
- [Logical Operators](#logical-operators)
- [String Operators](#string-operators)
- [Array Operators](#array-operators)
- [Set Operators](#set-operators)
- [Date Operators](#date-operators)
- [Conditional Operators](#conditional-operators)
- [Type Conversion Operators](#type-conversion-operators)
- [Accumulator Operators](#accumulator-operators)
- [Document/Object Operators](#documentobject-operators)
- [Integration with Aggregation Pipelines](#integration-with-aggregation-pipelines)
- [Type Safety](#type-safety)
- [Supported Operations Reference](#supported-operations-reference)
- [Best Practices](#best-practices)

## Basic Setup

**⚠️ Important Note on Imports**

When using `Expr` values in comparable operations (e.g., in updates), you need to import:

```ocaml
import reactivemongo.api.bson.builder.Expr.implicits._
```

**Only import this when expressions are supported by MongoDB:**

- ✅ **Supported:** Aggregation pipelines, [update with aggregation pipeline](https://www.mongodb.com/docs/manual/reference/method/db.collection.update/#std-label-update-with-aggregation-pipeline) (MongoDB 4.2+), `$expr` in queries
- ❌ **Not supported:** Simple update operations (`$set`, `$inc`, etc. without aggregation pipeline)

Example:

```scala
import reactivemongo.api.bson.builder.{ ExprBuilder, UpdateBuilder }
import reactivemongo.api.bson.builder.Expr.implicits._ // Only for aggregation pipeline updates

case class Product(
  name: String, 
  price: Double, 
  tax: Double, 
  shipping: Double,
  discount: Double)

val exprBuilder = ExprBuilder.empty[Product]
val updateBuilder = UpdateBuilder.empty[Product]

val price = exprBuilder.select(Symbol("price"))
val tax = exprBuilder.select(Symbol("tax"))
val totalPrice = exprBuilder.add(price, tax)

// OK: Using expressions in aggregation pipeline update (MongoDB 4.2+)
val update = updateBuilder.set(Symbol("price"), totalPrice)
```

### Creating an ExprBuilder

```scala
import reactivemongo.api.bson.builder.ExprBuilder

case class User(
  name: String,
  age: Int,
  email: String,
  isActive: Boolean,
  tags: Seq[String],
  isVerified: Boolean)

ExprBuilder.empty[User]
```

### Field References with `select`

Use `select` to create type-safe references to document fields. The compiler verifies that the field exists and has the expected type:

```scala
import reactivemongo.api.bson.builder.ExprBuilder

{
  val builder = ExprBuilder.empty[User]

  // Select a top-level field
  val nameExpr = builder.select(Symbol("name"))
  // Results in: "$name"

  val ageExpr = builder.select(Symbol("age"))
  // Results in: "$age"
}
```

For nested fields, pass multiple path segments:

```scala
import reactivemongo.api.bson.builder.ExprBuilder

case class Address(city: String, country: String)

case class Person(
  name: String, 
  firstName: String, 
  lastName: String, 
  address: Address)

{
  val builder = ExprBuilder.empty[Person]

  // Select a nested field
  val cityExpr = builder.select(Symbol("address"), Symbol("city"))
  // Results in: "$address.city"
}
```

### Constant Values with `from`

Wrap constant values into expressions:

```scala
import reactivemongo.api.bson.builder.ExprBuilder

{
  val builder = ExprBuilder.empty[Product]

  val constStr = builder.from("hello")
  val constNum = builder.from(42)
  val constSeq = builder.from(Seq("tag1", "tag2"))
}
```

### Raw BSON with `Expr.unsafe`

For advanced use cases or unsupported operators, create expressions from raw BSON values:

```scala
import reactivemongo.api.bson.{ BSONString, BSONDocument }
import reactivemongo.api.bson.builder.Expr

case class Foo(name: String)

{
  // Create from a raw BSON value
  val rawExpr = Expr.unsafe[Foo, String](BSONString(f"$$name"))

  // Create a MongoDB operator expression
  val upperExpr = Expr.unsafe[Foo, String](
    BSONDocument(f"$$toUpper" -> f"$$name")
  )
}
```

## Arithmetic Operators

Arithmetic operators work on numeric types (types with a `Numeric` instance).

### Add (`$add`)

Add numbers or dates:

```scala
import reactivemongo.api.bson.builder.ExprBuilder

{
  val builder = ExprBuilder.empty[Product]
  val price = builder.select(Symbol("price"))
  val tax = builder.select(Symbol("tax"))
  val shipping = builder.select(Symbol("shipping"))

  // Add two values
  val subtotal = builder.add(price, tax)
  // Result: { "$add": ["$price", "$tax"] }

  // Add multiple values (variadic)
  val total = builder.add(price, tax, shipping)
  // Result: { "$add": ["$price", "$tax", "$shipping"] }
}
```

### Subtract (`$subtract`)

Subtract one value from another:

```scala
import reactivemongo.api.bson.builder.ExprBuilder

{
  val builder = ExprBuilder.empty[Product]
  val price = builder.select(Symbol("price"))
  val discount = builder.select(Symbol("discount"))

  val finalPrice = builder.subtract(price, discount)
  // Result: { "$subtract": ["$price", "$discount"] }
}
```

### Multiply (`$multiply`)

Multiply numbers:

```scala
import reactivemongo.api.bson.builder.ExprBuilder

case class OrderLine(price: Double, quantity: Int)

{
  val builder = ExprBuilder.empty[OrderLine]
  val price = builder.select(Symbol("price"))
  val qty = builder.select(Symbol("quantity"))

  val lineTotal = builder.multiply(price, qty)
  // Result: { "$multiply": ["$price", "$quantity"] }
}
```

### Divide (`$divide`)

Divide one number by another:

```scala
import reactivemongo.api.bson.builder.ExprBuilder

case class Stats(total: Double, count: Int)

{
  val builder = ExprBuilder.empty[Stats]
  val total = builder.select(Symbol("total"))
  val count = builder.select(Symbol("count"))

  val average = builder.divide(total, count)
  // Result: { "$divide": ["$total", "$count"] }
}
```

### Modulo (`$mod`)

Return the remainder of dividing two numbers:

```scala
import reactivemongo.api.bson.builder.ExprBuilder

case class Item(quantity: Int)

{
  val builder = ExprBuilder.empty[Item]
  val qty = builder.select(Symbol("quantity"))
  val two = builder.from(2)

  val remainder = builder.mod(qty, two)
  // Result: { "$mod": ["$quantity", 2] }
}
```

### Abs, Ceil, Floor, Round

Unary math operations:

```scala
import reactivemongo.api.bson.builder.ExprBuilder

case class Measurement(value: Double)

{
  val builder = ExprBuilder.empty[Measurement]
  val value = builder.select(Symbol("value"))

  val absVal = builder.abs(value)
  // Result: { "$abs": "$value" }

  val ceilVal = builder.ceil(value)
  // Result: { "$ceil": "$value" }

  val floorVal = builder.floor(value)
  // Result: { "$floor": "$value" }

  val rounded = builder.round(value, 2)
  // Result: { "$round": ["$value", 2] }
}
```

### Sqrt and Pow

```scala
import reactivemongo.api.bson.builder.ExprBuilder

case class Geometry(side: Double)

{
  val builder = ExprBuilder.empty[Geometry]
  val side = builder.select(Symbol("side"))

  val root = builder.sqrt(side)
  // Result: { "$sqrt": "$side" }

  val squared = builder.pow(side, builder.from(2.0))
  // Result: { "$pow": ["$side", 2.0] }
}
```

## Comparison Operators

Compare two values and return a boolean (or integer for `cmp`). Both operands must be expressions of the same type.

```scala
import reactivemongo.api.bson.builder.ExprBuilder

{
  val builder = ExprBuilder.empty[Product]
  val price = builder.select(Symbol("price"))
  val shipping = builder.select(Symbol("shipping"))

  // Equal
  val isEqual = builder.eq(price, shipping)
  // Result: { "$eq": ["$price", "$shipping"] }

  // Not Equal
  val isNotEqual = builder.ne(price, shipping)
  // Result: { "$ne": ["$price", "$shipping"] }

  // Greater Than
  val isGreater = builder.gt(price, shipping)
  // Result: { "$gt": ["$price", "$shipping"] }

  // Greater Than or Equal
  val isGte = builder.gte(price, shipping)
  // Result: { "$gte": ["$price", "$shipping"] }

  // Less Than
  val isLess = builder.lt(price, shipping)
  // Result: { "$lt": ["$price", "$shipping"] }

  // Less Than or Equal
  val isLte = builder.lte(price, shipping)
  // Result: { "$lte": ["$price", "$shipping"] }

  // Compare (-1, 0, or 1)
  val comparison = builder.cmp(price, shipping)
  // Result: { "$cmp": ["$price", "$shipping"] }
}
```

## Logical Operators

Combine boolean expressions:

```scala
import reactivemongo.api.bson.builder.ExprBuilder

{
  val builder = ExprBuilder.empty[User]
  val age = builder.select(Symbol("age"))
  val active = builder.select(Symbol("isActive"))
  val verified = builder.select(Symbol("isVerified"))
  val eighteen = builder.from(18)

  // AND - all conditions must be true
  val bothActive = builder.and(active, verified)
  // Result: { "$and": ["$isActive", "$isVerified"] }

  // OR - any condition can be true
  val eitherOne = builder.or(active, verified)
  // Result: { "$or": ["$isActive", "$isVerified"] }

  // NOT - negate a boolean expression
  val notActive = builder.not(active)
  // Result: { "$not": "$isActive" }

  // Combine operators
  val eligible = builder.and(
    builder.gte(age, eighteen),
    builder.or(active, verified)
  )
  // Result: { "$and": [{ "$gte": ["$age", 18] }, { "$or": ["$isActive", "$isVerified"] }] }
}
```

## String Operators

Manipulate string expressions:

### Concat (`$concat`)

```scala
import reactivemongo.api.bson.builder.ExprBuilder

{
  val builder = ExprBuilder.empty[Person]
  val first = builder.select(Symbol("firstName"))
  val last = builder.select(Symbol("lastName"))
  val space = builder.from(" ")

  val fullName = builder.concat(first, space, last)
  // Result: { "$concat": ["$firstName", " ", "$lastName"] }
}
```

### Substr (`$substr`)

```scala
import reactivemongo.api.bson.builder.ExprBuilder

case class Doc(code: String, scores: Seq[Int])

{
  val builder = ExprBuilder.empty[Doc]
  val code = builder.select(Symbol("code"))

  val prefix = builder.substr(code, 0, 3)
  // Result: { "$substr": ["$code", 0, 3] }
}
```

### Case Conversion

```scala
import reactivemongo.api.bson.builder.ExprBuilder

{
  val builder = ExprBuilder.empty[Doc]
  val code = builder.select(Symbol("code"))

  val lower = builder.toLower(code)
  // Result: { "$toLower": "$code" }

  val upper = builder.toUpper(code)
  // Result: { "$toUpper": "$code" }
}
```

### String Length (`$strLen`)

```scala
import reactivemongo.api.bson.builder.ExprBuilder

{
  val builder = ExprBuilder.empty[Doc]
  val code = builder.select(Symbol("code"))

  val len = builder.strLen(code)
  // Result: { "$strLen": "$code" }
}
```

### Trim (`$trim`)

```scala
import reactivemongo.api.bson.builder.ExprBuilder

{
  val builder = ExprBuilder.empty[Doc]
  val code = builder.select(Symbol("code"))

  val trimmed = builder.trim(code)
  // Result: { "$trim": { "input": "$code" } }
}
```

### Split (`$split`)

```scala
import reactivemongo.api.bson.builder.ExprBuilder

{
  val builder = ExprBuilder.empty[Doc]
  val code = builder.select(Symbol("code"))

  val parts = builder.split(code, ",")
  // Result: { "$split": ["$code", ","] }
}
```

### Index Of Bytes (`$indexOfBytes`)

```scala
import reactivemongo.api.bson.builder.ExprBuilder

{
  val builder = ExprBuilder.empty[Doc]
  val code = builder.select(Symbol("code"))

  val idx = builder.indexOfBytes(code, "world")
  // Result: { "$indexOfBytes": ["$code", "world", 0] }

  // With start and end positions
  val idxRange = builder.indexOfBytes(code, "world", 5, Some(20))
  // Result: { "$indexOfBytes": ["$code", "world", 5, 20] }
}
```

### Replace All (`$replaceAll`)

```scala
import reactivemongo.api.bson.builder.ExprBuilder

{
  val builder = ExprBuilder.empty[Doc]
  val code = builder.select(Symbol("code"))

  val cleaned = builder.replaceAll(code, "foo", "bar")
  // Result: { "$replaceAll": { "input": "$code", "find": "foo", "replacement": "bar" } }
}
```

### Regex Match (`$regexMatch`)

```scala
import reactivemongo.api.bson.builder.ExprBuilder

{
  val builder = ExprBuilder.empty[Doc]
  val code = builder.select(Symbol("code"))

  val isValid = builder.regexMatch(code, "^[a-zA-Z0-9+_.-]+@[a-zA-Z0-9.-]+$")
  // Result: { "$regexMatch": { "input": "$code", "regex": "^[a-zA-Z0-9+_.-]+@[a-zA-Z0-9.-]+$" } }

  // With options (e.g., case-insensitive)
  val matchCI = builder.regexMatch(code, "admin", "i")
  // Result: { "$regexMatch": { "input": "$code", "regex": "admin", "options": "i" } }
}
```

## Array Operators

### Element At (`$arrayElemAt`)

Return the element at a specific index:

```scala
import reactivemongo.api.bson.builder.ExprBuilder

{
  val builder = ExprBuilder.empty[User]
  val tags = builder.select(Symbol("tags"))

  val firstTag = builder.arrayElemAt(tags, 0)
  // Result: { "$arrayElemAt": ["$tags", 0] }

  val lastTag = builder.arrayElemAt(tags, -1)
  // Result: { "$arrayElemAt": ["$tags", -1] }
}
```

### Concat Arrays (`$concatArrays`)

Concatenate multiple arrays:

```scala
import reactivemongo.api.bson.builder.ExprBuilder

{
  val builder = ExprBuilder.empty[User]
  val tags = builder.select(Symbol("tags"))
  val more = builder.from(Seq("foo", "bar"))

  val all = builder.concatArrays(tags, more)
  // Result: { "$concatArrays": ["$tags", ["foo", "bar"]] }
}
```

### Size (`$size`)

Return the number of elements in an array:

```scala
import reactivemongo.api.bson.builder.ExprBuilder

{
  val builder = ExprBuilder.empty[User]
  val tags = builder.select(Symbol("tags"))

  val count = builder.size(tags)
  // Result: { "$size": "$tags" }
}
```

### Slice (`$slice`)

Return a subset of an array:

```scala
import reactivemongo.api.bson.builder.ExprBuilder

{
  val builder = ExprBuilder.empty[User]
  val results = builder.select(Symbol("tags"))

  // First 3 elements
  val top3 = builder.slice(results, 3)
  // Result: { "$slice": ["$results", 3] }

  // 5 elements starting at position 2
  val page = builder.slice(results, 2, 5)
  // Result: { "$slice": ["$results", 2, 5] }
}
```

### In (`$in`)

Test if a value is in an array:

```scala
import reactivemongo.api.bson.builder.ExprBuilder

{
  val builder = ExprBuilder.empty[User]
  val name = builder.select(Symbol("name"))
  val tags = builder.select(Symbol("tags"))

  val isTagged = builder.in(name, tags)
  // Result: { "$in": ["$name", "$tags"] }
}
```

### First and Last (`$first`, `$last`)

```scala
import reactivemongo.api.bson.builder.ExprBuilder

{
  val builder = ExprBuilder.empty[User]
  val values = builder.select(Symbol("tags"))

  val head = builder.first(values)
  // Result: { "$first": "$tags" }

  val tail = builder.last(values)
  // Result: { "$last": "$tags" }
}
```

### Reverse Array (`$reverseArray`)

```scala
import reactivemongo.api.bson.builder.ExprBuilder

{
  val builder = ExprBuilder.empty[User]
  val tags = builder.select(Symbol("tags"))

  val reversed = builder.reverseArray(tags)
  // Result: { "$reverseArray": "$tags" }
}
```

### Range (`$range`)

Generate a sequence of integers:

```scala
import reactivemongo.api.bson.builder.ExprBuilder

{
  val builder = ExprBuilder.empty[Doc]

  val nums = builder.range(0, 10)
  // Result: { "$range": [0, 10, 1] }

  val evens = builder.range(0, 20, 2)
  // Result: { "$range": [0, 20, 2] }
}
```

### Filter (`$filter`)

Filter array elements based on a condition:

```scala
import reactivemongo.api.bson.builder.ExprBuilder

{
  val builder = ExprBuilder.empty[Doc]
  val scores = builder.select(Symbol("scores"))

  val passing = builder.filter(scores) { itemExpr =>
    builder.gte(itemExpr, builder.from(60))
  }
  // Result: { "$filter": { "input": "$scores", "cond": { "$gte": ["$$this", 60] }, "as": "this" } }
}
```

## Set Operators

Perform set operations on arrays:

```scala
import reactivemongo.api.bson.builder.ExprBuilder

{
  val builder = ExprBuilder.empty[User]
  val setA = builder.select(Symbol("tags"))
  val setB = builder.from(Seq("foo", "bar"))

  // Is Subset
  val isSubset = builder.setIsSubset(setA, setB)
  // Result: { "$setIsSubset": ["$tags", ["foo", "bar"]] }

  // Intersection
  val common = builder.setIntersection(setA, setB)
  // Result: { "$setIntersection": ["$tags", ["foo", "bar"]] }

  // Union
  val combined = builder.setUnion(setA, setB)
  // Result: { "$setUnion": ["$tags", ["foo", "bar"]] }

  // Difference
  val diff = builder.setDifference(setA, setB)
  // Result: { "$setDifference": ["$tags", ["foo", "bar"]] }
}
```

## Date Operators

Extract date components from date fields:

```scala
import reactivemongo.api.bson.builder.ExprBuilder
import java.time.Instant

case class Event(name: String, createdAt: Instant)

{
  val builder = ExprBuilder.empty[Event]
  val date = builder.select(Symbol("createdAt"))

  val y = builder.year(date)
  // Result: { "$year": "$createdAt" }

  val m = builder.month(date)
  // Result: { "$month": "$createdAt" }

  val d = builder.dayOfMonth(date)
  // Result: { "$dayOfMonth": "$createdAt" }

  val dow = builder.dayOfWeek(date)
  // Result: { "$dayOfWeek": "$createdAt" }

  val h = builder.hour(date)
  // Result: { "$hour": "$createdAt" }

  val min = builder.minute(date)
  // Result: { "$minute": "$createdAt" }

  val sec = builder.second(date)
  // Result: { "$second": "$createdAt" }

  val ms = builder.millisecond(date)
  // Result: { "$millisecond": "$createdAt" }
}
```

### Date to String (`$dateToString`)

```scala
import reactivemongo.api.bson.builder.ExprBuilder

{
  val builder = ExprBuilder.empty[Event]
  val date = builder.select(Symbol("createdAt"))

  val formatted = builder.dateToString(date, "%Y-%m-%d")
  // Result: { "$dateToString": { "date": "$createdAt", "format": "%Y-%m-%d" } }
}
```

## Conditional Operators

### Cond (`$cond`)

Return one of two values based on a boolean condition:

```scala
import reactivemongo.api.bson.builder.ExprBuilder

case class Order(customerId: String, quantity: Int, price: Double)

{
  val builder = ExprBuilder.empty[Order]
  val qty = builder.select(Symbol("quantity"))
  val price = builder.select(Symbol("price"))
  val ten = builder.from(10)
  val discountedPrice = builder.multiply(price, builder.from(0.9))

  builder.cond(
    builder.gt(qty, ten),
    discountedPrice,
    price
  )
  // Result: { "$cond": { "if": { "$gt": ["$quantity", 10] }, "then": { "$multiply": ["$price", 0.9] }, "else": "$price" } }
}
```

### If Null (`$ifNull`)

Return a replacement value when an expression is null:

```scala
import reactivemongo.api.bson.builder.ExprBuilder

{
  val builder = ExprBuilder.empty[User]
  val email = builder.select(Symbol("email"))
  val name = builder.select(Symbol("name"))

  val displayName = builder.ifNull(email, name)
  // Result: { "$ifNull": ["$email", "$name"] }
}
```

### Switch (`$switch`)

Evaluate a series of case expressions:

```scala
import reactivemongo.api.bson.builder.ExprBuilder

case class Student(score: Int)

{
  val builder = ExprBuilder.empty[Student]
  val score = builder.select(Symbol("score"))

  builder.switch(
    branches = Seq(
      (builder.gte(score, builder.from(90)), builder.from("A")),
      (builder.gte(score, builder.from(80)), builder.from("B")),
      (builder.gte(score, builder.from(70)), builder.from("C"))
    ),
    default = builder.from("F")
  )
  // Result: { "$switch": {
  //   "branches": [
  //     { "case": { "$gte": ["$score", 90] }, "then": "A" },
  //     { "case": { "$gte": ["$score", 80] }, "then": "B" },
  //     { "case": { "$gte": ["$score", 70] }, "then": "C" }
  //   ],
  //   "default": "F"
  // }}
}
```

## Type Conversion Operators

Convert values between BSON types:

```scala
import reactivemongo.api.bson.builder.ExprBuilder

{
  val builder = ExprBuilder.empty[Product]
  val name = builder.select(Symbol("name"))
  val price = builder.select(Symbol("price"))

  // Get BSON type
  val bsonType = builder.`type`(name)
  // Result: { "$type": "$name" }

  // Convert to string
  val str = builder.toString(price)
  // Result: { "$toString": "$price" }

  // Convert to numeric types
  val asInt = builder.toInt(name)
  // Result: { "$toInt": "$name" }

  val asDouble = builder.toDouble(name)
  // Result: { "$toDouble": "$name" }

  val asLong = builder.toLong(name)
  // Result: { "$toLong": "$name" }

  // Convert to boolean
  val asBool = builder.toBool(name)
  // Result: { "$toBool": "$name" }
}
```

### Convert (`$convert`)

For more control over type conversion, including error handling:

```scala
import reactivemongo.api.bson.builder.ExprBuilder

{
  val builder = ExprBuilder.empty[Doc]
  val code = builder.select(Symbol("code"))

  // Convert with error and null handling
  builder.convert[String, Int](
    code,
    to = "int",
    onError = Some(builder.from(0)),
    onNull = Some(builder.from(0))
  )
  // Result: { "$convert": { "input": "$input", "to": "int", "onError": 0, "onNull": 0 } }
}
```

## Accumulator Operators

For use in `$group` stages or window functions:

```scala
import reactivemongo.api.bson.builder.ExprBuilder

{
  val builder = ExprBuilder.empty[Order]
  val price = builder.select(Symbol("price"))
  val qty = builder.select(Symbol("quantity"))

  // Sum
  val totalPrice = builder.sum(price)
  // Result: { "$sum": "$price" }

  // Average
  val avgPrice = builder.avg(price)
  // Result: { "$avg": "$price" }

  // Max (single expression)
  val maxPrice = builder.max(price)
  // Result: { "$max": "$price" }

  // Max (multiple expressions)
  val maxOfTwo = builder.max(price, builder.from(100.0))
  // Result: { "$max": ["$price", 100.0] }

  // Min (single expression)
  val minQty = builder.min(qty)
  // Result: { "$min": "$quantity" }

  // Min (multiple expressions)
  val minOfTwo = builder.min(qty, builder.from(1))
  // Result: { "$min": ["$quantity", 1] }
}
```

## Document/Object Operators

### Literal (`$literal`)

Wrap a value so it is not interpreted as an expression:

```scala
import reactivemongo.api.bson.builder.ExprBuilder

{
  ExprBuilder.empty[Doc].literal("$notAFieldRef")
  // Result: { "$literal": "$notAFieldRef" }
}
```

### Merge Objects (`$mergeObjects`)

Merge multiple documents into one:

```scala
import reactivemongo.api.bson.BSONDocument
import reactivemongo.api.bson.builder.ExprBuilder

{
  val builder = ExprBuilder.empty[Person]
  val address = builder.select(Symbol("address"))
  val overrides = builder.from(BSONDocument("city" -> "Foo"))

  val merged = builder.mergeObjects(address, overrides)
  // Result: { "$mergeObjects": ["$address", { "city": "Foo" }] }
}
```

### Get Field (`$getField`)

Retrieve a field value from a document expression:

```scala
import reactivemongo.api.bson.builder.ExprBuilder

{
  val builder = ExprBuilder.empty[Person]
  val address = builder.select(Symbol("address"))

  val city = builder.getField(address, Symbol("city"))
  // Result: { "$getField": { "field": "city", "input": "$address" } }
}
```

### Object to Array / Array to Object

Convert between document and array representations:

```scala
import reactivemongo.api.bson.builder.ExprBuilder

{
  val builder = ExprBuilder.empty[Person]
  val address = builder.select(Symbol("address"))

  // Object to array of { k, v } pairs
  val asArray = builder.objectToArray(address)
  // Result: { "$objectToArray": "$address" }

  // Array to object (reverse)
  val asObj = builder.arrayToObject(asArray)
  // Result: { "$arrayToObject": { "$objectToArray": "$address" } }
}
```

## Integration with Aggregation Pipelines

`Expr` values have an implicit `BSONWriter`, so they can be used directly in BSON documents:

```scala
import reactivemongo.api.bson.{ BSONArray, BSONDocument }
import reactivemongo.api.bson.builder.ExprBuilder

{
  val builder = ExprBuilder.empty[Order]
  val amount = builder.select(Symbol("price"))
  val qty = builder.select(Symbol("quantity"))

  // Use in a $project stage
  // TODO: Use ProjectionBuilder
  val projectStage = BSONDocument(
    f"$$project" -> BSONDocument(
      "customerId" -> 1,
      "totalAmount" -> builder.multiply(
        amount,
        builder.select(Symbol("quantity"))
      )
    )
  )

  // Use in a $group stage
  val groupStage = BSONDocument(
    f"$$group" -> BSONDocument(
      "_id" -> "$customerId",
      "totalSpent" -> builder.sum(amount),
      "avgAmount" -> builder.avg(amount),
      "maxAmount" -> builder.max(amount),
      "orderCount" -> builder.sum(builder.from(1))
    )
  )

  // Build a complete pipeline
  val pipeline = BSONArray(
    BSONDocument(f"$$match" -> BSONDocument("status" -> "completed")),
    groupStage,
    BSONDocument(f"$$sort" -> BSONDocument("totalSpent" -> -1))
  )
}
```

### Combining with FilterBuilder

```scala
import reactivemongo.api.bson.{ BSONArray, BSONDocument }
import reactivemongo.api.bson.builder.{ ExprBuilder, FilterBuilder }

{
  // Build filter
  val filter = FilterBuilder.empty[Product]
    .gte(Symbol("price"), 10.0)
    .result()

  // Build computed fields with ExprBuilder
  val exprBuilder = ExprBuilder.empty[Product]
  val price = exprBuilder.select(Symbol("price"))

  val pipeline = BSONArray(
    BSONDocument(f"$$match" -> filter),
    BSONDocument(f"$$project" -> BSONDocument(
      "name" -> 1,
      "priceWithTax" -> exprBuilder.multiply(price, exprBuilder.from(1.2))
    ))
  )
}
```

## Type Safety

The `ExprBuilder` uses compile-time type checking to ensure:

1. **Field Existence**: The compiler verifies that fields exist in the case class when using `select`
2. **Type Consistency**: Expression types must be compatible (e.g., arithmetic operators require `Numeric` types)
3. **Operator Validity**: 
   - Arithmetic operators (`add`, `subtract`, etc.) require `Numeric[N]` evidence
   - Comparison operators require `BSONWriter[V]` evidence
   - Array operators work on `Seq[V]` typed expressions
   - Logical operators work on `Expr[T, Boolean]` values

```scala
import reactivemongo.api.bson.builder.ExprBuilder

{
  val builder = ExprBuilder.empty[User]

  // ✅ This will compile:
  val nameExpr = builder.select(Symbol("name"))
  val ageExpr = builder.select(Symbol("age"))
  val sum = builder.add(ageExpr, builder.from(1))

  // ❌ This will NOT compile (nonexistent field):
  // builder.select(Symbol("nonexistent"))

  // ❌ This will NOT compile (wrong field type):
  // builder.select(Symbol("name"))

  // ❌ This will NOT compile (can't add strings):
  // builder.add(nameExpr, nameExpr)
}
```

## Supported Operations Reference

| Category | Operator | Method | Description |
|----------|----------|--------|-------------|
| **Arithmetic** | `$add` | `.add()` | Add numbers (variadic) |
| | `$subtract` | `.subtract()` | Subtract two numbers |
| | `$multiply` | `.multiply()` | Multiply numbers (variadic) |
| | `$divide` | `.divide()` | Divide two numbers |
| | `$mod` | `.mod()` | Modulo of two numbers |
| | `$abs` | `.abs()` | Absolute value |
| | `$ceil` | `.ceil()` | Ceiling |
| | `$floor` | `.floor()` | Floor |
| | `$round` | `.round()` | Round to decimal place |
| | `$sqrt` | `.sqrt()` | Square root |
| | `$pow` | `.pow()` | Exponentiation |
| **Comparison** | `$eq` | `.eq()` | Equal |
| | `$ne` | `.ne()` | Not equal |
| | `$gt` | `.gt()` | Greater than |
| | `$gte` | `.gte()` | Greater than or equal |
| | `$lt` | `.lt()` | Less than |
| | `$lte` | `.lte()` | Less than or equal |
| | `$cmp` | `.cmp()` | Compare (-1, 0, 1) |
| **Logical** | `$and` | `.and()` | Logical AND (variadic) |
| | `$or` | `.or()` | Logical OR (variadic) |
| | `$not` | `.not()` | Logical NOT |
| **String** | `$concat` | `.concat()` | Concatenate strings (variadic) |
| | `$substr` | `.substr()` | Substring |
| | `$toLower` | `.toLower()` | Lowercase |
| | `$toUpper` | `.toUpper()` | Uppercase |
| | `$strLen` | `.strLen()` | String length |
| | `$trim` | `.trim()` | Trim whitespace |
| | `$split` | `.split()` | Split by delimiter |
| | `$indexOfBytes` | `.indexOfBytes()` | Find substring index |
| | `$replaceAll` | `.replaceAll()` | Replace all occurrences |
| | `$regexMatch` | `.regexMatch()` | Regex pattern test |
| **Array** | `$arrayElemAt` | `.arrayElemAt()` | Element at index |
| | `$concatArrays` | `.concatArrays()` | Concatenate arrays |
| | `$filter` | `.filter()` | Filter elements |
| | `$size` | `.size()` | Array length |
| | `$slice` | `.slice()` | Array subset |
| | `$in` | `.in()` | Test membership |
| | `$first` | `.first()` | First element |
| | `$last` | `.last()` | Last element |
| | `$reverseArray` | `.reverseArray()` | Reverse array |
| | `$range` | `.range()` | Generate integer range |
| **Set** | `$setIsSubset` | `.setIsSubset()` | Subset test |
| | `$setIntersection` | `.setIntersection()` | Intersection |
| | `$setUnion` | `.setUnion()` | Union |
| | `$setDifference` | `.setDifference()` | Difference |
| **Date** | `$year` | `.year()` | Extract year |
| | `$month` | `.month()` | Extract month (1-12) |
| | `$dayOfMonth` | `.dayOfMonth()` | Extract day (1-31) |
| | `$dayOfWeek` | `.dayOfWeek()` | Extract day of week (1-7) |
| | `$hour` | `.hour()` | Extract hour (0-23) |
| | `$minute` | `.minute()` | Extract minute (0-59) |
| | `$second` | `.second()` | Extract second (0-59) |
| | `$millisecond` | `.millisecond()` | Extract millisecond (0-999) |
| | `$dateToString` | `.dateToString()` | Format date as string |
| **Conditional** | `$cond` | `.cond()` | If-then-else |
| | `$ifNull` | `.ifNull()` | Null coalesce |
| | `$switch` | `.switch()` | Multi-branch conditional |
| **Type** | `$type` | `.type()` | Get BSON type |
| | `$toString` | `.toString()` | Convert to string |
| | `$toInt` | `.toInt()` | Convert to int |
| | `$toDouble` | `.toDouble()` | Convert to double |
| | `$toLong` | `.toLong()` | Convert to long |
| | `$toBool` | `.toBool()` | Convert to boolean |
| | `$convert` | `.convert()` | Convert with error handling |
| **Accumulator** | `$sum` | `.sum()` | Sum values |
| | `$avg` | `.avg()` | Average values |
| | `$max` | `.max()` | Maximum value |
| | `$min` | `.min()` | Minimum value |
| **Object** | `$literal` | `.literal()` | Literal value |
| | `$mergeObjects` | `.mergeObjects()` | Merge documents |
| | `$getField` | `.getField()` | Get field from document |
| | `$objectToArray` | `.objectToArray()` | Document to array |
| | `$arrayToObject` | `.arrayToObject()` | Array to document |

## Best Practices

1. **Use `select` for Field References**: Always prefer `select` over `Expr.unsafe` for field references to get compile-time validation
2. **Use `from` for Constants**: Wrap constant values with `from` rather than using `Expr.unsafe`
3. **Compose Expressions**: Build complex expressions by composing simpler ones — the type system will guide you
4. **Leverage Type Inference**: Let the compiler infer expression types when possible to keep code concise
5. **Reserve `Expr.unsafe` for Edge Cases**: Only use `Expr.unsafe` for MongoDB operators not yet covered by the builder API
6. **Use with Aggregation Pipelines**: `Expr` values can be embedded directly in `BSONDocument` thanks to the implicit `BSONWriter`

## Troubleshot

### Compiler Error: "No field Symbol with shapeless.tag.Tagged[String("...")] comparable with type reactivemongo.api.bson.builder.Expr[.., ..] in .."

This error occurs when trying to use an `Expr` value in a comparable operation (such as in an update) without the required implicit conversion.

**Solution:** Ensure you have imported the necessary implicits:

```ocaml
import reactivemongo.api.bson.builder.Expr.implicits._
```

See the [Basic Setup](#basic-setup) section for detailed information about when to import this and the MongoDB contexts where expressions are supported.

## See Also

- [Filters Documentation](./filters.md) - Building type-safe MongoDB query filters
- [Projections Documentation](./projection.md) - Building type-safe MongoDB projections
- [Update Documentation](./update.md) - Building type-safe MongoDB updates
- [Getting Started](./get-started.md) - Introduction to the builder API
- [BSON Documentation](../README.md) - Overview of BSON handling in ReactiveMongo