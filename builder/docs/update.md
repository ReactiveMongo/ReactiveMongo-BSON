# Update Operations Guide

This guide covers how to use the `UpdateBuilder` API to construct type-safe MongoDB update operations. You'll learn to create update documents from simple field assignments to complex operations involving arrays, nested objects, and conditional updates.

## Table of Contents

- [Basic Update Operations](#basic-update-operations)
  - [Setting Field Values](#setting-field-values)
  - [Unsetting Fields](#unsetting-fields)
  - [Mixed Set and Unset](#mixed-set-and-unset)
- [Field Operations](#field-operations)
  - [Increment and Decrement](#increment-and-decrement)
  - [Multiplication](#multiplication)
  - [Min and Max Operations](#min-and-max-operations)
  - [Renaming Fields](#renaming-fields)
  - [Current Date](#current-date)
- [Array Operations](#array-operations)
  - [Adding Items to Arrays](#adding-items-to-arrays)
  - [Removing Items from Arrays](#removing-items-from-arrays)
  - [Add to Set (Unique Arrays)](#add-to-set-unique-arrays)
  - [Array Position Operations](#array-position-operations)
- [Advanced Array Operations](#advanced-array-operations)
  - [Batch Push with Modifiers](#batch-push-with-modifiers)
  - [Push with Slice](#push-with-slice)
  - [Push with Sort](#push-with-sort)
  - [Push with Position](#push-with-position)
  - [Combining All Modifiers](#combining-all-modifiers)
- [Nested Field Operations](#nested-field-operations)
  - [Single Nested Field](#single-nested-field)
  - [Multiple Nested Updates](#multiple-nested-updates)
  - [Deeply Nested Fields](#deeply-nested-fields)
  - [Combining Nested and Regular Updates](#combining-nested-and-regular-updates)
- [Conditional Operations](#conditional-operations)
  - [Using ifSome](#using-ifsome)
  - [Conditional Operations with Other Updates](#conditional-operations-with-other-updates)
- [Mixed Operations](#mixed-operations)
- [Untyped Operations](#untyped-operations)
- [Update Operators Reference](#update-operators-reference)

// TODO: Section about using expression with UpdateBuilder

## Basic Update Operations

### Setting Field Values

The most common update operation is setting field values using `set`:

```scala
import java.time.OffsetDateTime
import reactivemongo.api.bson.builder.UpdateBuilder

case class Status(
  name: String,
  updated: OffsetDateTime,
  code: Option[Int]
)

case class User(
  id: String,
  name: String,
  age: Int,
  email: String,
  isActive: Boolean,
  tags: Seq[String],
  points: Int,
  loginCount: Int,
  lastLogin: Option[OffsetDateTime],
  status: Option[Status]
)

// Single field update
UpdateBuilder.empty[User]
  .set(Symbol("name"), "John Doe")
  .result()
// Result: { "$set": { "name": "John Doe" } }

// Multiple field updates
UpdateBuilder.empty[User]
  .set(Symbol("name"), "Jane Smith")
  .set(Symbol("email"), "jane@example.com")
  .set(Symbol("isActive"), true)
  .result()
// Result: {
//   "$set": {
//     "name": "Jane Smith",
//     "email": "jane@example.com",
//     "isActive": true
//   }
// }
```

### Unsetting Fields

Use `unset` to remove fields from documents. Note that `unset` can only be used with optional fields:

```scala
import reactivemongo.api.bson.builder.UpdateBuilder

// Remove optional fields
UpdateBuilder.empty[User]
  .unset(Symbol("lastLogin"))
  .unset(Symbol("status"))
  .result()
// Result: {
//   "$unset": {
//     "lastLogin": 1,
//     "status": 1
//   }
// }

// Type safety: This will not compile (id is not optional)
// UpdateBuilder.empty[User].unset(Symbol("id"))
```

### Mixed Set and Unset

Combine `set` and `unset` operations in a single update:

```scala
import reactivemongo.api.bson.builder.UpdateBuilder

UpdateBuilder.empty[User]
  .set(Symbol("isActive"), false)
  .unset(Symbol("lastLogin"))
  .set(Symbol("email"), "archived@example.com")
  .result()
// Result: {
//   "$set": {
//     "isActive": false,
//     "email": "archived@example.com"
//   },
//   "$unset": {
//     "lastLogin": 1
//   }
// }
```

## Field Operations

### Increment and Decrement

Use `inc` for numeric field operations:

```scala
import reactivemongo.api.bson.builder.UpdateBuilder

case class UserStats(
  userId: String,
  loginCount: Int,
  points: Long,
  balance: Double
)

// Increment values
val incUpdate = UpdateBuilder.empty[UserStats]
  .inc(Symbol("loginCount"), 1)
  .inc(Symbol("points"), 100L)
  .inc(Symbol("balance"), 25.50)
  .result()
// Result: {
//   "$inc": {
//     "loginCount": 1,
//     "points": 100,
//     "balance": 25.50
//   }
// }

// Decrement (negative increment)
val decUpdate = UpdateBuilder.empty[UserStats]
  .inc(Symbol("points"), -50L)
  .inc(Symbol("balance"), -10.0)
  .result()
// Result: {
//   "$inc": {
//     "points": -50,
//     "balance": -10.0
//   }
// }
```

### Multiplication

Use `mul` to multiply field values:

```scala
import reactivemongo.api.bson.builder.UpdateBuilder

case class Product(
  id: String,
  name: String,
  price: Double,
  quantity: Long,
  discount: Option[Double],
  tags: Seq[String],
  featured: Boolean
)

UpdateBuilder.empty[Product]
  .mul(Symbol("price"), 1.1)       // Apply 10% price increase
  .mul(Symbol("quantity"), 2L)     // Double the quantity
  .result()
// Result: {
//   "$mul": {
//     "price": 1.1,
//     "quantity": 2
//   }
// }
```

### Min and Max Operations

Use `max` and `min` for comparison-based field updates:

```scala
import reactivemongo.api.bson.builder.UpdateBuilder

case class GameStats(
  playerId: String,
  highScore: Int,
  lowScore: Int,
  bestTime: Double,
  worstTime: Double
)

// Update high score only if new score is higher
val maxUpdate = UpdateBuilder.empty[GameStats]
  .max(Symbol("highScore"), 1500)
  .min(Symbol("lowScore"), 100)
  .result()
// Result: {
//   "$max": { "highScore": 1500 },
//   "$min": { "lowScore": 100 }
// }

// Performance tracking with time bounds
val timeUpdate = UpdateBuilder.empty[GameStats]
  .min(Symbol("bestTime"), 45.5)   // Update best time if faster
  .max(Symbol("worstTime"), 120.0) // Update worst time if slower
  .result()
// Result: {
//   "$min": { "bestTime": 45.5 },
//   "$max": { "worstTime": 120.0 }
// }
```

### Renaming Fields

Use `rename` to change field names:

```scala
import reactivemongo.api.bson.builder.UpdateBuilder

case class Committer(
  username: String,
  email: String
)

case class Tracker(
  id: Long,
  committer: Committer,
  active: Boolean
)

case class Document(
  id: String,
  name: String,
  status: Status,
  tracker: Tracker,
  counter: Int
)

UpdateBuilder.empty[Document]
  .rename[String](Symbol("name"), "Foo")
  .result()
// Result: {
//   "$rename": {
//     "name": "Foo"
//   }
// }
```

### Current Date

Set a field to the current date or timestamp:

```scala
import reactivemongo.api.bson.builder.UpdateBuilder

case class Timestamps(
  id: String,
  lastModified: java.time.OffsetDateTime,
  createdAt: java.time.OffsetDateTime
)

// Set to current date (default)
val dateUpdate = UpdateBuilder.empty[Timestamps]
  .currentDate[java.time.OffsetDateTime](
    Symbol("lastModified"),
    UpdateBuilder.CurrentDateType.Date
  )
  .result()
// Result: {
//   "$currentDate": {
//     "lastModified": { "$type": "date" }
//   }
// }

// Set to current timestamp
val timestampUpdate = UpdateBuilder.empty[Timestamps]
  .currentDate[java.time.OffsetDateTime](
    Symbol("createdAt"),
    UpdateBuilder.CurrentDateType.Timestamp
  )
  .result()
// Result: {
//   "$currentDate": {
//     "createdAt": { "$type": "timestamp" }
//   }
// }
```

## Array Operations

### Adding Items to Arrays

Use `push` to add items to arrays:

```scala
import reactivemongo.api.bson.builder.UpdateBuilder

case class Article(
  id: String,
  tags: Seq[String],
  favoriteColors: Seq[String]
)

// Add single item
val singlePush = UpdateBuilder.empty[Article]
  .push(Symbol("tags"), "scala")
  .result()
// Result: {
//   "$push": {
//     "tags": "scala"
//   }
// }

// Add multiple items (multiple push operations)
val multiPush = UpdateBuilder.empty[Article]
  .push(Symbol("tags"), "scala")
  .push(Symbol("tags"), "mongodb")
  .push(Symbol("tags"), "reactive")
  .result()
// Result: {
//   "$push": {
//     "tags": "scala",
//     "tags": "mongodb",
//     "tags": "reactive"
//   }
// }
```

### Removing Items from Arrays

Use `pull`, `pullAll`, and `pop` to remove items:

```scala
import reactivemongo.api.bson.builder.UpdateBuilder
import reactivemongo.api.bson.BSONDocument

case class UserProfile(
  id: String,
  tags: Seq[String],
  scores: Seq[Int],
  loginHistory: Seq[java.time.OffsetDateTime]
)

// Remove specific value
val pullUpdate = UpdateBuilder.empty[UserProfile]
  .pull(Symbol("tags"), "inactive")
  .result()
// Result: {
//   "$pull": {
//     "tags": "inactive"
//   }
// }

// Remove multiple values
val pullAllUpdate = UpdateBuilder.empty[UserProfile]
  .pullAll(Symbol("tags"), Seq("inactive", "suspended"))
  .result()
// Result: {
//   "$pullAll": {
//     "tags": ["inactive", "suspended"]
//   }
// }

// Remove with query condition
val pullExprUpdate = UpdateBuilder.empty[UserProfile]
  .pullExpr(Symbol("scores"), BSONDocument(f"$$gte" -> 100))
  .result()
// Result: {
//   "$pull": {
//     "scores": { "$gte": 100 }
//   }
// }

// Remove first or last element
val popUpdate = UpdateBuilder.empty[UserProfile]
  .pop(
    Symbol("loginHistory"),
    UpdateBuilder.PopStrategy.First
  )
  .result()
// Result: {
//   "$pop": {
//     "loginHistory": -1
//   }
// }

val popLastUpdate = UpdateBuilder.empty[UserProfile]
  .pop(
    Symbol("scores"),
    UpdateBuilder.PopStrategy.Last
  )
  .result()
// Result: {
//   "$pop": {
//     "scores": 1
//   }
// }
```

### Add to Set (Unique Arrays)

Use `addToSet` to add items only if they don't already exist:

```scala
import reactivemongo.api.bson.builder.UpdateBuilder

// Add single unique item
val addToSetUpdate = UpdateBuilder.empty[Article]
  .addToSet(Symbol("tags"), "verified")
  .result()
// Result: {
//   "$addToSet": {
//     "tags": "verified"
//   }
// }

// Add multiple unique items efficiently
val addToSetEachUpdate = UpdateBuilder.empty[Article]
  .addToSetEach(Symbol("tags"), Seq("tag1", "tag2", "tag3"))
  .result()
// Result: {
//   "$addToSet": {
//     "tags": {
//       "$each": ["tag1", "tag2", "tag3"]
//     }
//   }
// }
```

### Array Position Operations

Combine multiple array operations:

```scala
import reactivemongo.api.bson.builder.UpdateBuilder

case class Queue(
  id: String,
  items: Seq[String]
)

UpdateBuilder.empty[Queue]
  .addToSet(Symbol("items"), "unique-item")
  .pop(Symbol("items"), UpdateBuilder.PopStrategy.Last)
  .result()
// Result: {
//   "$addToSet": {
//     "items": "unique-item"
//   },
//   "$pop": {
//     "items": 1
//   }
// }
```

## Advanced Array Operations

### Batch Push with Modifiers

Use `pushEach` to add multiple items with optional modifiers:

```scala
import reactivemongo.api.bson.builder.UpdateBuilder

case class GameData(
  id: String,
  scores: Seq[Int],
  tags: Seq[String]
)

// Basic pushEach
val basicPush = UpdateBuilder.empty[GameData]
  .pushEach(Symbol("scores"), Seq(100, 200, 300))
  .result()
// Result: {
//   "$push": {
//     "scores": {
//       "$each": [100, 200, 300]
//     }
//   }
// }
```

### Push with Slice

Use the `slice` parameter to limit array size:

```scala
import reactivemongo.api.bson.builder.UpdateBuilder

case class History(
  id: String,
  recentEvents: Seq[String]
)

// Keep only the last 10 items
val pushWithSlice = UpdateBuilder.empty[History]
  .pushEach(
    Symbol("recentEvents"),
    Seq("event1", "event2", "event3"),
    slice = Some(UpdateBuilder.PushSlice.Last(10))
  )
  .result()
// Result: {
//   "$push": {
//     "recentEvents": {
//       "$each": ["event1", "event2", "event3"],
//       "$slice": -10
//     }
//   }
// }

// Keep only the first 5 items
val pushWithFirstSlice = UpdateBuilder.empty[History]
  .pushEach(
    Symbol("recentEvents"),
    Seq("event1", "event2"),
    slice = Some(UpdateBuilder.PushSlice.First(5))
  )
  .result()
// Result: {
//   "$push": {
//     "recentEvents": {
//       "$each": ["event1", "event2"],
//       "$slice": 5
//     }
//   }
// }

// Empty array (remove all)
val pushWithEmpty = UpdateBuilder.empty[History]
  .pushEach(
    Symbol("recentEvents"),
    Seq.empty[String],
    slice = Some(UpdateBuilder.PushSlice.Empty)
  )
  .result()
// Result: {
//   "$push": {
//     "recentEvents": {
//       "$each": [],
//       "$slice": 0
//     }
//   }
// }
```

### Push with Sort

Sort array elements after pushing:

```scala
import reactivemongo.api.bson.builder.UpdateBuilder

case class SortedData(
  id: String,
  values: Seq[String]
)

// Sort ascending
val pushSortAsc = UpdateBuilder.empty[SortedData]
  .pushEach(
    Symbol("values"),
    Seq("z", "a", "m"),
    sort = Some(UpdateBuilder.PushSort.Ascending)
  )
  .result()
// Result: {
//   "$push": {
//     "values": {
//       "$each": ["z", "a", "m"],
//       "$sort": 1
//     }
//   }
// }

// Sort descending
val pushSortDesc = UpdateBuilder.empty[SortedData]
  .pushEach(
    Symbol("values"),
    Seq("z", "a", "m"),
    sort = Some(UpdateBuilder.PushSort.Descending)
  )
  .result()
// Result: {
//   "$push": {
//     "values": {
//       "$each": ["z", "a", "m"],
//       "$sort": -1
//     }
//   }
// }

// Sort by document fields
import reactivemongo.api.bson.BSONDocument

val pushSortDoc = UpdateBuilder.empty[SortedData]
  .pushEach(
    Symbol("values"),
    Seq("item1", "item2"),
    sort = Some(UpdateBuilder.PushSort.Document(
      BSONDocument("score" -> -1, "name" -> 1)
    ))
  )
  .result()
// Result: {
//   "$push": {
//     "values": {
//       "$each": ["item1", "item2"],
//       "$sort": { "score": -1, "name": 1 }
//     }
//   }
// }
```

### Push with Position

Insert items at a specific position:

```scala
import reactivemongo.api.bson.builder.UpdateBuilder

case class OrderedList(
  id: String,
  items: Seq[String]
)

val pushWithPosition = UpdateBuilder.empty[OrderedList]
  .pushEach(
    Symbol("items"),
    Seq("first", "second"),
    position = Some(0)
  )
  .result()
// Result: {
//   "$push": {
//     "items": {
//       "$each": ["first", "second"],
//       "$position": 0
//     }
//   }
// }
```

### Combining All Modifiers

Use multiple modifiers together:

```scala
import reactivemongo.api.bson.builder.UpdateBuilder

case class ComplexArray(
  id: String,
  items: Seq[String]
)

val complexPush = UpdateBuilder.empty[ComplexArray]
  .pushEach(
    Symbol("items"),
    Seq("new1", "new2"),
    slice = Some(UpdateBuilder.PushSlice.First(5)),
    sort = Some(UpdateBuilder.PushSort.Ascending),
    position = Some(2)
  )
  .result()
// Result: {
//   "$push": {
//     "items": {
//       "$each": ["new1", "new2"],
//       "$slice": 5,
//       "$sort": 1,
//       "$position": 2
//     }
//   }
// }
```

## Nested Field Operations

### Single Nested Field

Update fields within nested objects:

```scala
import reactivemongo.api.bson.builder.UpdateBuilder
import java.time.OffsetDateTime

case class Location(
  city: String,
  country: String,
  updated: OffsetDateTime,
  detectedAt: Option[OffsetDateTime]
)

case class UserWithLocation(
  id: String,
  name: String,
  location: Location
)

UpdateBuilder.empty[UserWithLocation]
  .nestedField(Symbol("location"))
  .at { _.set(Symbol("updated"), OffsetDateTime.now()) }
  .result()
// Result: {
//   "$set": {
//     "location.updated": "2026-02-03T..."
//   }
// }
```

### Multiple Nested Updates

Perform multiple operations on nested fields:

```scala
import reactivemongo.api.bson.builder.UpdateBuilder
import java.time.OffsetDateTime

UpdateBuilder.empty[Document]
  .nestedField(Symbol("status"))
  .at { nested =>
    nested
      .set(Symbol("updated"), OffsetDateTime.now())
      .set(Symbol("name"), "Active")
  }
  .result()
// Result: {
//   "$set": {
//     "status.updated": "2026-02-03T...",
//     "status.name": "Active"
//   }
// }
```

### Deeply Nested Fields

Navigate through multiple levels of nesting:

```scala
import reactivemongo.api.bson.builder.UpdateBuilder

case class Project(
  id: String,
  name: String,
  tracker: Tracker
)

UpdateBuilder.empty[Project]
  .nested(Symbol("tracker"), Symbol("committer"))
  .at { _.set(Symbol("username"), "Alice") }
  .result()
// Result: {
//   "$set": {
//     "tracker.committer.username": "Alice"
//   }
// }
```

### Combining Nested and Regular Updates

Mix nested and top-level field updates:

```scala
import reactivemongo.api.bson.builder.UpdateBuilder

UpdateBuilder.empty[User]
  .set(Symbol("name"), "John Smith")
  .nestedField(Symbol("status"))
  .at { _.set(Symbol("name"), "Active") }
  .inc(Symbol("loginCount"), 1)
  .result()
// Result: {
//   "$set": {
//     "name": "John Smith",
//     "status.name": "Active"
//   },
//   "$inc": {
//     "loginCount": 1
//   }
// }
```

### Multiple Nested Objects

Update different nested objects in the same operation:

```scala
import reactivemongo.api.bson.builder.UpdateBuilder

UpdateBuilder.empty[Document]
  .nestedField(Symbol("status"))
  .at { _.set(Symbol("name"), "Active") }
  .nestedField(Symbol("tracker"))
  .at { _.set(Symbol("id"), 12345L) }
  .result()
// Result: {
//   "$set": {
//     "status.name": "Active",
//     "tracker.id": 12345
//   }
// }
```

### Nested Field with Different Operations

Apply various operations to nested fields:

```scala
import reactivemongo.api.bson.builder.UpdateBuilder
import java.time.OffsetDateTime

UpdateBuilder.empty[UserWithLocation]
  .nestedField(Symbol("location"))
  .at { nested =>
    nested
      .set(Symbol("updated"), OffsetDateTime.now())
      .unset(Symbol("detectedAt"))
  }
  .result()
// Result: {
//   "$set": {
//     "location.updated": "2026-02-03T..."
//   },
//   "$unset": {
//     "location.detectedAt": 1
//   }
// }
```

## Conditional Operations

### Using ifSome

Apply updates conditionally based on `Option` values:

```scala
import reactivemongo.api.bson.builder.UpdateBuilder

// Update only if value is defined
val maybeEmail: Option[String] = Some("user@example.com")

UpdateBuilder.empty[User]
  .set(Symbol("name"), "John Doe")
  .ifSome(maybeEmail) { (builder, email) =>
    builder.set(Symbol("email"), email)
  }
  .result()
// Result: {
//   "$set": {
//     "name": "John Doe",
//     "email": "user@example.com"
//   }
// }

// No update if None
val noEmail: Option[String] = None

UpdateBuilder.empty[User]
  .set(Symbol("name"), "Jane Doe")
  .ifSome(noEmail) { (builder, email) =>
    builder.set(Symbol("email"), email)
  }
  .result()
// Result: {
//   "$set": {
//     "name": "Jane Doe"
//   }
// }
```

### Conditional Operations with Other Updates

Combine `ifSome` with other operations:

```scala
import reactivemongo.api.bson.builder.UpdateBuilder

def updateUser(
  userId: String,
  name: Option[String],
  login: Option[java.time.OffsetDateTime]
): reactivemongo.api.bson.BSONDocument = {
  UpdateBuilder.empty[User]
    .set(Symbol("id"), userId)
    .ifSome(name) { (builder, n) =>
      builder.set(Symbol("name"), n)
    }
    .inc(Symbol("loginCount"), 1)
    .ifSome(login) { (builder, login) =>
      builder.set(Symbol("lastLogin"), login)
    }
    .result()
}

// Example usage:
// With all values:
// updateUser("123", Some("Alice"), Some(OffsetDateTime.now()))
// Result: {
//   "$set": {
//     "id": "123",
//     "name": "Alice",
//     "lastActivity": "2026-02-03T..."
//   },
//   "$inc": { "loginCount": 1 }
// }

// With some values missing:
// updateUser("456", None, Some(OffsetDateTime.now()))
// Result: {
//   "$set": {
//     "id": "456",
//     "lastActivity": "2026-02-03T..."
//   },
//   "$inc": { "loginCount": 1 }
// }
```

### Complex Conditional Logic

Use `ifSome` for complex conditional updates:

```scala
import reactivemongo.api.bson.builder.UpdateBuilder

def applyDiscount(
  productId: String,
  maybeDiscount: Option[Double]
): reactivemongo.api.bson.BSONDocument = {
  UpdateBuilder.empty[Product]
    .set(Symbol("id"), productId)
    .ifSome(maybeDiscount) { (builder, discount) =>
      builder
        .set(Symbol("discount"), discount)
        .push(Symbol("tags"), "on-sale")
        .set(Symbol("featured"), true)
    }
    .result()
}

// With discount:
// applyDiscount("prod-1", Some(0.25))
// Result: {
//   "$set": {
//     "id": "prod-1",
//     "discount": 0.25,
//     "featured": true
//   },
//   "$push": { "tags": "on-sale" }
// }

// Without discount:
// applyDiscount("prod-2", None)
// Result: {
//   "$set": { "id": "prod-2" }
// }
```

## Mixed Operations

Combine multiple update operators in a single operation:

```scala
import reactivemongo.api.bson.builder.UpdateBuilder

val comprehensiveUpdate = UpdateBuilder.empty[User]
  .set(Symbol("id"), "user-123")
  .set(Symbol("name"), "John Doe")
  .inc(Symbol("loginCount"), 1)
  .inc(Symbol("points"), 100L)
  .push(Symbol("tags"), "active")
  .addToSet(Symbol("tags"), "verified")
  .unset(Symbol("status"))
  .result()
// Result: {
//   "$set": {
//     "id": "user-123",
//     "name": "John Doe"
//   },
//   "$inc": {
//     "loginCount": 1,
//     "points": 100
//   },
//   "$push": { "tags": "active" },
//   "$addToSet": { "tags": "verified" },
//   "$unset": { "status": 1 }
// }
```

Another example with min/max operations:

```scala
import reactivemongo.api.bson.builder.UpdateBuilder

case class Stats(
  id: String,
  counter: Int,
  quantity: Long,
  highScore: Int,
  balance: Double
)

val statsUpdate = UpdateBuilder.empty[Stats]
  .set(Symbol("id"), "stats-1")
  .inc(Symbol("counter"), 10)
  .mul(Symbol("quantity"), 2L)
  .max(Symbol("counter"), 50)
  .min(Symbol("quantity"), 1L)
  .result()
// Result: {
//   "$set": { "id": "stats-1" },
//   "$inc": { "counter": 10 },
//   "$mul": { "quantity": 2 },
//   "$max": { "counter": 50 },
//   "$min": { "quantity": 1 }
// }
```

## Untyped Operations

For advanced use cases, you can use untyped operations (escape hatch):

```scala
import reactivemongo.api.bson.builder.UpdateBuilder
import reactivemongo.api.bson.{ BSONString, BSONInteger }

// Warning: Bypasses type safety
val untypedUpdate = UpdateBuilder.empty[Document]
  .untyped[String](Symbol("name"), f"$$set", BSONString("custom-value"))
  .untyped[Int](Symbol("counter"), f"$$inc", BSONInteger(10))
  .result()
// Result: {
//   "$set": { "name": "custom-value" },
//   "$inc": { "counter": 10 }
// }

// Combine with typed operations
val mixedUpdate = UpdateBuilder.empty[Document]
  .set(Symbol("id"), "doc-1")
  .untyped[String](Symbol("name"), f"$$set", BSONString("value"))
  .inc(Symbol("counter"), 5)
  .result()
// Result: {
//   "$set": {
//     "id": "doc-1",
//     "name": "value"
//   },
//   "$inc": { "counter": 5 }
// }
```

## Update Operators Reference

This table provides a quick reference for all available update operators and methods:

| Operator | Method | Description | Example |
|----------|--------|-------------|---------|
| **Field Setting** | | | |
| `$set` | `.set()` | Sets the value of a field | `.set(Symbol("name"), "Alice")` |
| `$unset` | `.unset()` | Removes a field (optional fields only) | `.unset(Symbol("status"))` |
| **Numeric Operations** | | | |
| `$inc` | `.inc()` | Increments a field by a value | `.inc(Symbol("count"), 1)` |
| `$mul` | `.mul()` | Multiplies a field by a value | `.mul(Symbol("price"), 1.1)` |
| `$max` | `.max()` | Updates only if value is greater | `.max(Symbol("highScore"), 100)` |
| `$min` | `.min()` | Updates only if value is less | `.min(Symbol("lowScore"), 10)` |
| **Other Field Operations** | | | |
| `$rename` | `.rename()` | Renames a field | `.rename[String](Symbol("old"), "new")` |
| `$currentDate` | `.currentDate()` | Sets field to current date/timestamp | `.currentDate[String](Symbol("updated"))` |
| **Array Operations** | | | |
| `$push` | `.push()` | Adds item to array | `.push(Symbol("tags"), "scala")` |
| `$push` (with `$each`) | `.pushEach()` | Adds multiple items with modifiers | `.pushEach(Symbol("tags"), Seq("a", "b"))` |
| `$pull` | `.pull()` | Removes matching items | `.pull(Symbol("tags"), "old")` |
| `$pull` (expression) | `.pullExpr()` | Removes items matching condition | `.pullExpr(Symbol("scores"), condition)` |
| `$pullAll` | `.pullAll()` | Removes multiple values | `.pullAll(Symbol("tags"), Seq("a", "b"))` |
| `$pop` | `.pop()` | Removes first or last item | `.pop(Symbol("items"), PopStrategy.Last)` |
| `$addToSet` | `.addToSet()` | Adds unique item | `.addToSet(Symbol("tags"), "unique")` |
| `$addToSet` (with `$each`) | `.addToSetEach()` | Adds multiple unique items | `.addToSetEach(Symbol("tags"), Seq("a", "b"))` |
| **Nested Operations** | | | |
| N/A | `.nestedField()` | Updates nested object field | `.nestedField(Symbol("status")).at { ... }` |
| N/A | `.nested()` | Updates deeply nested fields | `.nested(Symbol("a"), Symbol("b")).at { ... }` |
| **Conditional Operations** | | | |
| N/A | `.ifSome()` | Conditionally apply updates | `.ifSome(maybeValue) { (b, v) => ... }` |
| **Advanced** | | | |
| N/A | `.untyped()` | Escape hatch for custom operations | `.untyped[String](Symbol("field"), "$op", value)` |

### Push Modifiers

When using `.pushEach()`, you can specify these modifiers:

| Modifier | Parameter | Description | Example |
|----------|-----------|-------------|---------|
| `$each` | (automatic) | Adds multiple values | Automatically applied with `.pushEach()` |
| `$slice` | `slice` | Limits array size | `slice = Some(PushSlice.Last(10))` |
| `$sort` | `sort` | Sorts array elements | `sort = Some(PushSort.Ascending)` |
| `$position` | `position` | Inserts at specific index | `position = Some(0)` |

### Type Safety

The UpdateBuilder API provides compile-time type safety:

- Field names are verified to exist in the case class
- Field types are checked for compatibility with values
- `unset` only works with `Option[_]` fields
- Nested field paths are validated at compile time

### Empty Builder

Create an empty update (useful for conditional building):

```scala
import reactivemongo.api.bson.builder.UpdateBuilder

val empty = UpdateBuilder.empty[User].result()
// Result: {}

// Building conditionally
def conditionalBuild(shouldUpdateName: Boolean) = {
  val builder = UpdateBuilder.empty[User]

  if (shouldUpdateName) {
    builder.set(Symbol("name"), "New Name")
  }

  val result = builder.result()
}
```

### Method Chaining

All update methods return the builder instance, enabling fluent method chaining:

```scala
import reactivemongo.api.bson.builder.UpdateBuilder

UpdateBuilder.empty[User]
  .set(Symbol("id"), "123")
  .set(Symbol("name"), "Alice")
  .inc(Symbol("age"), 1)
  .push(Symbol("tags"), "verified")
  .result()
```

## Best Practices

1. **Use Type Safety**: Leverage compile-time checking by using proper field names and types
2. **Mutable Builder**: Remember that `UpdateBuilder` is mutable; operations modify the builder instance
3. **Result Method**: Always call `.result()` to generate the final BSON document
4. **Optional Fields**: Only use `.unset()` with optional (`Option[_]`) fields
5. **Nested Updates**: Use `.nestedField()` or `.nested()` for type-safe nested field updates
6. **Conditional Updates**: Use `.ifSome()` for clean conditional logic
7. **Array Batching**: Use `.pushEach()` and `.addToSetEach()` instead of multiple `.push()` calls for better performance
8. **Escape Hatch**: Use `.untyped()` sparingly when type safety cannot be guaranteed

## Next Steps

- Learn about [Filter Operations](filters.md) for querying documents
- Explore [Projection Operations](projection.md) for controlling returned fields
- Study the ReactiveMongo documentation for more advanced use cases
