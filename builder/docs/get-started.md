# Getting Started with ReactiveMongo-BSON Builders

This guide introduces the type-safe builder API for MongoDB operations in Scala, explaining why it's needed and how to use it effectively.

## Table of Contents

- [Introduction & Motivation](#introduction--motivation)
  - [What are the Builders?](#what-are-the-builders)
  - [The Problem: String-Based Field Access](#the-problem-string-based-field-access)
  - [The Builder Solution](#the-builder-solution)
  - [Key Advantages](#key-advantages)
- [Getting Started](#getting-started)
  - [Dependencies](#dependencies)
  - [Basic Setup](#basic-setup)
  - [Your First Filter](#your-first-filter)
  - [Your First Projection](#your-first-projection)
  - [Your First Update](#your-first-update)
  - [Complete Example](#complete-example)
- [Core Concepts](#core-concepts)
- [Next Steps](#next-steps)

## Introduction & Motivation

### What are the Builders?

The ReactiveMongo-BSON builder library provides **type-safe query, projection, and update builders** for MongoDB operations in Scala. It offers compile-time validation of field names and types, ensuring that database operations are correct before your code runs.

The library includes three main builders:

- **FilterBuilder**: Construct MongoDB query filters with type-safe field access
- **ProjectionBuilder**: Create projections with compile-time field validation
- **UpdateBuilder**: Build update documents with type-safe field operations

### The Problem: String-Based Field Access

Traditional MongoDB query construction in Scala relies on string-based field names, which leads to several issues:

#### Manual Document Construction

```scala
import reactivemongo.api.bson._

case class User(
  name: String,
  age: Int,
  email: String,
  isActive: Boolean,
  tags: Seq[String]
)

// Traditional approach with BSONDocument
val query = BSONDocument(
  "name" -> "John",        // ❌ No compile-time validation
  "agee" -> BSONDocument(  // ❌ Typo won't be caught until runtime
    f"$$gt" -> 18
  )
)

// Update document
BSONDocument(
  f"$$set" -> BSONDocument(
    "emial" -> "john@example.com"  // ❌ Another typo
  ),
  "$inc" -> BSONDocument(
    "age" -> 1
  )
)
```

**Issues with this approach:**

- ✅ **Works for**: Simple queries and full document operations
- ❌ **No validation**: Field names are strings, typos cause runtime errors
- ❌ **No type safety**: Wrong types for field values aren't caught
- ❌ **Maintenance burden**: Renaming case class fields doesn't update queries
- ❌ **No IDE support**: No autocomplete for field names

#### Real-World Scenarios Requiring Better Control

1. **Complex Filtering**: Building queries with multiple conditions, logical operators
2. **Partial Projections**: Selecting only specific fields, hiding sensitive data
3. **Granular Updates**: Setting specific fields, incrementing counters, array operations
4. **Dynamic Queries**: Building queries conditionally based on runtime parameters
5. **Aggregation Pipelines**: Type-safe filters and updates in pipeline stages

### The Builder Solution

The builder API addresses these limitations by providing **type-safe builders** with compile-time field validation:

#### Compile-Time Field Validation

```scala
import reactivemongo.api.bson.builder.FilterBuilder

FilterBuilder.empty[User]
  .eq(Symbol("name"), "John")     // ✅ Compile-time validated
  .gt(Symbol("age"), 18)          // ✅ Field type checked
  // .eq(Symbol("agee"), "John")  // ❌ Compile error - field doesn't exist
  .result()
```

#### Type-Safe Operations

```scala
import reactivemongo.api.bson.builder.FilterBuilder

FilterBuilder.empty[User]
  // .eq(Symbol("age"), "not a number")  // ❌ Compile error - wrong type
  .eq(Symbol("age"), 25)                 // ✅ Correct type
  .result()
```

#### Fine-Grained Control

```scala
import reactivemongo.api.bson.builder._

// Complex filtering
FilterBuilder.empty[User]
  .eq(Symbol("name"), "John")
  .gt(Symbol("age"), 18)
  .ne(Symbol("email"), "")
  .result()

// Selective projections
ProjectionBuilder.empty[User]
  .includes(Symbol("name"))
  .includes(Symbol("age"))
  .excludes(Symbol("email"))  // Hide sensitive data
  .result()

// Granular updates
UpdateBuilder.empty[User]
  .set(Symbol("name"), "Jane")
  .inc(Symbol("age"), 1)
  .push(Symbol("tags"), "verified")
  .result()
```

### Key Advantages

#### 1. Compile-Time Safety

- Field names are validated at compile time using Shapeless
- Type mismatches are caught before runtime
- Refactoring case class fields automatically updates all usages

#### 2. ReactiveMongo Integration

- Generated documents work seamlessly with ReactiveMongo
- No runtime overhead - validation happens at compile time
- Compatible with queries, updates, and aggregation pipelines

#### 3. Developer Experience

- IDE autocomplete for field names (using Symbols)
- Clear compile errors for invalid operations
- Fluent, chainable API for building complex operations

#### 4. Maintainability

- Changing case class field names updates all builder usages at compile time
- Type changes are enforced across the entire codebase
- No string-based field references to maintain

## Getting Started

### Dependencies

Add ReactiveMongo-BSON to your `build.sbt`:

```ocaml
libraryDependencies ++= Seq(
  "org.reactivemongo" %% "reactivemongo-bson-api" % "VERSION",
  "org.reactivemongo" %% "reactivemongo-bson-builder" % "VERSION"
)
```

The builder library requires:
- **Shapeless**: For compile-time type checking and field validation
- **ReactiveMongo BSON**: For BSON document types and serialization

### Basic Setup

#### 1. Define Your Data Structure

Start by defining a case class that represents your MongoDB document:

```scala
case class Foo(
  bar: String,
  value: Int
)
```

**Important Notes:**

- All fields must be accessible for the builders to validate them
- Nested case classes are supported for nested field operations
- Optional fields (`Option[T]`) have special handling (e.g., `unset` operations)

#### 2. Using Field Names with Symbols

The builder API uses Scala's `Symbol` type for field names:

```scala
// Field references using Symbols
Symbol("name")      // References the 'name' field
Symbol("age")       // References the 'age' field
Symbol("email")     // References the 'email' field
Symbol("isActive")  // References the 'isActive' field
```

Symbols provide compile-time validation through Shapeless type-level programming. The compiler verifies that the symbol name corresponds to an actual field in your case class.

### Your First Filter

Let's build a simple filter to query users:

```scala
import reactivemongo.api.bson.builder.FilterBuilder

// Build filter with method chaining
FilterBuilder.empty[User]
  .eq(Symbol("name"), "John")        // Add name condition
  .gt(Symbol("age"), 18)             // Add age condition
  .eq(Symbol("isActive"), true)      // Add active condition
  .result()
// Result: { "name": "John", "age": { "$gt": 18 }, "isActive": true }

// Or combine explicitly with logical operators
FilterBuilder.empty[User]
  .eq(Symbol("name"), "John")
  .gt(Symbol("age"), 18)
  .and()
// Result: { "$and": [{ "name": "John" }, { "age": { "$gt": 18 }}] }
```

### Your First Projection

Create a projection to select specific fields:

```scala
import reactivemongo.api.bson.builder.ProjectionBuilder

ProjectionBuilder.empty[User]
  .includes(Symbol("name"))
  .includes(Symbol("age"))
  .excludes(Symbol("email"))    // Hide sensitive data
  .result()
// { "name": 1, "age": 1, "email": 0 }
```

### Your First Update

Build an update document to modify existing records:

```scala
import reactivemongo.api.bson.builder.UpdateBuilder

UpdateBuilder.empty[User]
  .set(Symbol("name"), "Jane")
  .inc(Symbol("age"), 1)
  .set(Symbol("isActive"), true)
  .result()
// { "$set": { "name": "Jane", "isActive": true }, "$inc": { "age": 1 } }
```

### Complete Example

Here's a complete example showing how to use the builders with ReactiveMongo:

```scala
import reactivemongo.api.bson._
import reactivemongo.api.bson.builder._

{
  // Find users with type-safe filter
  val filter = FilterBuilder.empty[User]
    .gt(Symbol("age"), 18)
    .eq(Symbol("isActive"), true)
    .result()

  // collection.find(filter).cursor[User]().collect[Seq]()
}

{
  // Find users with projection (hide email)
  val filter = FilterBuilder.empty[User].result()
    
  val projection = ProjectionBuilder.empty[User]
    .includes(Symbol("name"))
    .includes(Symbol("age"))
    .excludes(Symbol("email"))
    .result()

  // collection.find(filter, Some(projection)).cursor[User]().collect[Seq]()
}

{
  val name = "Foo"

  // Update user with type-safe update builder
  val filter = FilterBuilder.empty[User]
    .eq(Symbol("name"), name)
    .result()

  val update = UpdateBuilder.empty[User]
    .inc(Symbol("age"), 1)
    .set(Symbol("isActive"), true)
    .push(Symbol("tags"), "updated")
    .result()

  // collection.update.one(filter, update)
}

{
  // Complex query with logical operators
  val minAge: Int = 1
  val tags: Seq[String] = Seq("foo", "bar")

  val filter = FilterBuilder.empty[User]
    .gte(Symbol("age"), minAge)
    .in(Symbol("tags"), tags)
    .eq(Symbol("isActive"), true)
    .result()

  // collection.find(filter).cursor[User]().collect[Seq]()
}
```

## Core Concepts

Before diving into more advanced topics, understand these fundamental concepts:

### 1. Type-Safe Field Access

The builders use Scala's type system (via Shapeless) to validate field names at compile time:

```scala
import reactivemongo.api.bson.builder.FilterBuilder

FilterBuilder.empty[User]
  .eq(Symbol("name"), "John")   // ✅ Valid - 'name' exists
  // .eq(Symbol("nam"), "John")  // ❌ Compile error - field doesn't exist
```

### 2. Mutable Builders

All builders are mutable - operations modify the builder instance:

```scala
import reactivemongo.api.bson.builder.UpdateBuilder

{
  val builder = UpdateBuilder.empty[User]

  builder.set(Symbol("name"), "Alice")
  builder.inc(Symbol("age"), 1)

  val result = builder.result()  // Generates final document
}
```

### 3. Method Chaining

Builders support fluent method chaining for readable code:

```scala
import reactivemongo.api.bson.builder.FilterBuilder

FilterBuilder.empty[User]
  .eq(Symbol("name"), "John")
  .gt(Symbol("age"), 18)
  .eq(Symbol("isActive"), true)
  .result()
```

### 4. Result Generation

Always call `.result()` or `.and()`/`.or()` to generate the final BSON document:

```scala
import reactivemongo.api.bson.builder.FilterBuilder

FilterBuilder.empty[User]
  .eq(Symbol("name"), "John")
  .result()  // Generates BSONDocument
```

## Common Patterns

### Conditional Query Building

Build queries dynamically based on runtime conditions:

```scala
import reactivemongo.api.bson.BSONDocument
import reactivemongo.api.bson.builder.FilterBuilder

def buildUserFilter(
  nameOpt: Option[String],
  minAge: Option[Int]
): BSONDocument = {
  val builder = FilterBuilder.empty[User]

  nameOpt.foreach { name =>
    builder.eq(Symbol("name"), name)
  }

  minAge.foreach { age =>
    builder.gte(Symbol("age"), age)
  }

  builder.result()
}

// Usage
buildUserFilter(Some("John"), Some(18))
// Result: { "name": "John", "age": { "$gte": 18 } }

buildUserFilter(None, Some(21))
// Result: { "age": { "$gte": 21 } }
```

### Using ifSome for Optional Fields

The `UpdateBuilder` provides `ifSome` for clean conditional updates:

```scala
import reactivemongo.api.bson.BSONDocument
import reactivemongo.api.bson.builder.UpdateBuilder

def updateUser(
  newName: String,
  maybeEmail: Option[String],
  ageIncrement: Option[Int]
): BSONDocument = {
  UpdateBuilder.empty[User]
    .set(Symbol("name"), newName)
    .ifSome(maybeEmail) { (builder, email) =>
      builder.set(Symbol("email"), email)
    }
    .ifSome(ageIncrement) { (builder, inc) =>
      builder.inc(Symbol("age"), inc)
    }
    .result()
}
```

### Nested Field Operations

Work with nested case classes:

```scala
import reactivemongo.api.bson.builder.UpdateBuilder

case class Address(city: String, country: String)
case class UserWithAddress(name: String, address: Address)

UpdateBuilder.empty[UserWithAddress]
  .nestedField(Symbol("address"))
  .at { nested =>
    nested.set(Symbol("city"), "Paris")
  }
  .result()
// Result: { "$set": { "address.city": "Paris" } }
```

### Compile-Time Error Examples

The builders catch errors at compile time:

```scala
import reactivemongo.api.bson.builder.FilterBuilder

{
  val builder = FilterBuilder.empty[User]

  // ❌ Compile error - field doesn't exist
  // builder.eq(Symbol("email"), "test@example.com")

  // ❌ Compile error - wrong type
  // builder.eq(Symbol("age"), "not a number")

  // ✅ Valid
  builder.eq(Symbol("name"), "John")
  builder.gt(Symbol("age"), 18)
}
```

## Next Steps

Now that you understand the basics:

- **[Filter Operations](filters.md)** - Learn advanced query building with logical operators
- **[Projection Operations](projection.md)** - Master field selection and projection
- **[Update Operations](update.md)** - Deep dive into update operations and array manipulation

## Additional Resources

- **ReactiveMongo Documentation**: [reactivemongo.org](http://reactivemongo.org)
- **Shapeless Documentation**: Understanding type-level programming
- **MongoDB Query Language**: [MongoDB Manual](https://docs.mongodb.com/manual/tutorial/query-documents/)

## Summary

The builder API provides:

✅ **Compile-time safety** - Field names and types validated before runtime  
✅ **IDE support** - Autocomplete and type checking in your editor  
✅ **Maintainability** - Refactoring case classes updates all queries  
✅ **Fluent API** - Readable, chainable method calls  
✅ **Zero runtime overhead** - All validation at compile time  

Start building type-safe MongoDB operations today!
