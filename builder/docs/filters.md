# Filters

The `FilterBuilder` provides a type-safe way to construct MongoDB query filters. It ensures at compile-time that fields exist and are comparable with the specified values.

## Basic Setup

```scala
import reactivemongo.api.bson.builder.FilterBuilder

case class User(
  id: String,
  name: String,
  age: Int,
  email: String,
  status: String,
  isActive: Boolean, 
  optionalField: Option[String])

val builder = FilterBuilder.empty[User]
```

## Simple Equality Checks

### Equal (`$eq`)

Match values that are equal to a specified value:

```scala
import reactivemongo.api.bson.builder.FilterBuilder

FilterBuilder.empty[User]
  .eq(Symbol("id"), "user123")
  .result()  // Convert to BSONDocument
// Result: { "id": { "$eq": "user123" } }
```

### Not Equal (`$ne`)

Match all values that are not equal to a specified value:

```scala
import reactivemongo.api.bson.builder.FilterBuilder

FilterBuilder.empty[User]
  .ne(Symbol("email"), "spam@example.com")
  .result()
// Result: { "email": { "$ne": "spam@example.com" } }
```

## Numeric Comparisons

### Greater Than (`$gt`)

Match values greater than a specified value:

```scala
import reactivemongo.api.bson.builder.FilterBuilder

case class Product(name: String, price: Float, quantity: Int)

FilterBuilder.empty[Product]
  .gt(Symbol("price"), 100)
  .result()
// Result: { "price": { "$gt": 100 } }
```

### Greater Than or Equal (`$gte`)

Match values greater than or equal to a specified value:

```scala
import reactivemongo.api.bson.builder.FilterBuilder

FilterBuilder.empty[User]
  .gte(Symbol("age"), 18)
  .result()
// Result: { "age": { "$gte": 18 } }
```

### Less Than (`$lt`)

Match values less than a specified value:

```scala
import reactivemongo.api.bson.builder.FilterBuilder

FilterBuilder.empty[User]
  .lt(Symbol("age"), 65)
  .result()
// Result: { "age": { "$lt": 65 } }
```

### Less Than or Equal (`$lte`)

Match values less than or equal to a specified value:

```scala
import reactivemongo.api.bson.builder.FilterBuilder

FilterBuilder.empty[Product]
  .lte(Symbol("quantity"), 10)
  .result()
// Result: { "quantity": { "$lte": 10 } }
```

### Age Range Filter Example

Combine multiple comparison operators:

```scala
import reactivemongo.api.bson.builder.FilterBuilder

FilterBuilder.empty[User]
  .gte(Symbol("age"), 18)
  .lt(Symbol("age"), 65)
  .and()
// Result: { "$and": [{ "age": { "$gte": 18 } }, { "age": { "$lt": 65 } }] }
```

## Array Operations

### In Array (`$in`)

Match any of the values specified in an array:

```scala
import reactivemongo.api.bson.builder.FilterBuilder

FilterBuilder.empty[User]
  .in(Symbol("status"), Seq("active", "pending", "suspended"))
  .result()
// Result: { "status": { "$in": ["active", "pending", "suspended"] } }
```

### Not In Array (`$nin`)

Match none of the values specified in an array:

```scala
import reactivemongo.api.bson.builder.FilterBuilder

FilterBuilder.empty[User]
  .nin(Symbol("email"), Seq("spam@example.com", "test@example.com"))
  .result()
// Result: { "email": { "$nin": ["spam@example.com", "test@example.com"] } }
```

## Array Field Operations

### Exists (`$exists`)

Check if a field exists:

```scala
import reactivemongo.api.bson.builder.FilterBuilder

FilterBuilder.empty[User]
  .exists(Symbol("optionalField"), true)
  .result()
// Result: { "optionalField": { "$exists": true } }
```

### Size (`$size`)

Match arrays with a specific number of elements:

```scala
import reactivemongo.api.bson.builder.FilterBuilder

case class Book(title: String, tags: Seq[String])

FilterBuilder.empty[Book]
  .size(Symbol("tags"), 3)
  .result()
// Result: { "tags": { "$size": 3 } }
```

## Logical Operations

### AND Operations

Combine multiple conditions with logical AND:

```scala
import reactivemongo.api.bson.builder.FilterBuilder

FilterBuilder.empty[User]
  .eq(Symbol("isActive"), true)
  .gte(Symbol("age"), 18)
  .and()
// Result: { "$and": [{ "isActive": true }, { "age": { "$gte": 18 } }] }
```

### OR Operations - Multiple Builders

Combine multiple filter builders with logical OR:

```scala
import reactivemongo.api.bson.builder.FilterBuilder

val result = {
  val builder1 = FilterBuilder.empty[User].eq(Symbol("id"), "value1")
  val builder2 = FilterBuilder.empty[User].gte(Symbol("age"), 10)

  FilterBuilder.empty[User].or(builder1, builder2)
}
// Result: { "$or": [{ "id": { "$eq": "value1" } }, { "age": { "$gte": 10 } }] }
```

### OR Operations - Iterable Input

Build OR conditions from an iterable of values:

```scala
import reactivemongo.api.bson.builder.FilterBuilder

FilterBuilder.empty[User]
  .or(Seq("foo", "bar")) { (clauseBuilder, str) =>
    clauseBuilder.eq(Symbol("name"), str)
  }
  .result()
// Result: { "$or": [{ "name": { "$eq": "foo" } }, { "name": { "$eq": "bar" } }] }
```

### Negation (`$not`)

Create a negating operation for complex conditions:

```scala
import reactivemongo.api.bson.builder.FilterBuilder

case class Doc(tags: Seq[String])

FilterBuilder.empty[Doc]
  .not(Symbol("tags")).apply { tagsBuilder =>
    tagsBuilder.eq("foo")
  }
// Result: { "tags": { "$not": { "$eq": "foo" } } }
```

## Nested Field Filtering

### Single Nested Field

Filter on nested object fields:

```scala
import reactivemongo.api.bson.builder.FilterBuilder

case class Address(city: String, country: String)
case class Point(name: String, address: Address)

FilterBuilder.empty[Point]
  .nestedField[Address](Symbol("address")).at { addressBuilder =>
    addressBuilder.eq(Symbol("city"), "New York")
  }
// Result: { "address.city": { "$eq": "New York" } }
```

### Multiple Path Segments

Navigate through multiple nesting levels:

```scala
import reactivemongo.api.bson.builder.FilterBuilder

case class Coordinates(lat: Double, lon: Double)
case class Location(name: String, coords: Coordinates)
case class Place(id: String, location: Location)

FilterBuilder.empty[Place]
  .nested(Symbol("location"), Symbol("coords")).at { coordsBuilder =>
    coordsBuilder.gte(Symbol("lat"), 40.0)
  }
// Result: { "location.coords.lat": { "$gte": 40.0 } }
```

## Advanced Operations

### Multiple Conditions on Single Field

Use chaining to add multiple conditions to the same filter:

```scala
import reactivemongo.api.bson.builder.FilterBuilder

FilterBuilder.empty[Product]
  .gte(Symbol("price"), 10.0)
  .lte(Symbol("price"), 100.0)
  .and()
// Result: { "$and": [{ "price": { "$gte": 10.0 } }, { "price": { "$lte": 100.0 } }] }
```

### Comments in Filters

Add MongoDB `$comment` for documentation:

```scala
import reactivemongo.api.bson.builder.FilterBuilder

FilterBuilder.empty[User]
  .gte(Symbol("age"), 18)
  .comment("Adult users only")
  .result()
// Result: { "age": { "$gte": 18 }, "$comment": "Adult users only" }
```

### Combining AND and OR

Build complex filters with both AND and OR logic:

```scala
import reactivemongo.api.bson.builder.FilterBuilder

{
  // Build first OR group
  val builder1 = FilterBuilder.empty[User]
    .eq(Symbol("status"), "active")
  
  val builder2 = FilterBuilder.empty[User]
    .eq(Symbol("status"), "pending")
  
  // Main filter with AND and OR
  FilterBuilder.empty[User]
    .gte(Symbol("age"), 18)
    .or(builder1, builder2)
    .and()
  // Result: {
  //   "$and": [
  //     { "age": { "$gte": 18 } },
  //     { "$or": [{ "status": { "$eq": "active" } }, { "status": { "$eq": "pending" } }] }
  //   ]
  // }
  }
```

## Type Safety

The `FilterBuilder` uses compile-time type checking to ensure:

1. **Field Existence**: The compiler verifies that the field exists in the case class
2. **Type Compatibility**: The value type must be comparable with the field type
3. **Operator Validity**: Only valid operators can be used with specific types
   - Ordering operators (`$gt`, `$gte`, `$lt`, `$lte`) require `Ordered[T]` types (numbers, temporal values)
   - Array operators require `Iterable[A]` values

```scala
import reactivemongo.api.bson.builder.FilterBuilder

// This will compile:
FilterBuilder.empty[User]
  .gte(Symbol("age"), 18)
  .result()

// This will NOT compile (age is Int, not Iterable):
// FilterBuilder.empty[User]
//   .in(Symbol("age"), Seq(18, 21, 25))
//   .result()

// This will NOT compile (nonexistent field):
// FilterBuilder.empty[User]
//   .eq(Symbol("nonexistent"), "value")
//   .result()
```

## Building Filters

### Result Method

Convert the builder to a `BSONDocument`:

```scala
import reactivemongo.api.bson.builder.FilterBuilder

FilterBuilder.empty[User]
  .eq(Symbol("isActive"), true)
  .result()
```

### AND Combination

Combine filters with AND logic (default):

```scala
import reactivemongo.api.bson.builder.FilterBuilder

FilterBuilder.empty[User]
  .eq(Symbol("isActive"), true)
  .gte(Symbol("age"), 18)
  .and()
// If single condition, returns just that condition
// If multiple conditions, wraps with $and
```

### OR Combination

Combine filters with OR logic:

```scala
import reactivemongo.api.bson.builder.FilterBuilder

case class Profile(id: Int, status: String)

FilterBuilder.empty[Profile]
  .eq(Symbol("status"), "active")
  .eq(Symbol("status"), "pending")
  .or()
// If single condition, returns just that condition
// If multiple conditions, wraps with $or
```

## Mutable Builder Pattern

The `FilterBuilder` is mutable and should not be shared between contexts:

```scala
import reactivemongo.api.bson.builder.FilterBuilder

{
  // ✅ Safe: Each filter has its own builder
  val filter1 = FilterBuilder.empty[User]
    .eq(Symbol("name"), "John")
    .result()
  
  val filter2 = FilterBuilder.empty[User]
    .eq(Symbol("name"), "Jane")
    .result()
  
  // ❌ Avoid: Storing builder in variable for reuse
  val savedBuilder = FilterBuilder.empty[User]
    .eq(Symbol("name"), "John")
  // Don't reuse savedBuilder elsewhere as it's mutable
}
```

## Integration with MongoDB Operations

```scala
import reactivemongo.api.bson.builder.FilterBuilder

// Build filter
FilterBuilder.empty[User]
  .eq(Symbol("isActive"), true)
  .gte(Symbol("age"), 18)
  .and()

// Use in find operation:
// collection.find(filter).cursor[User]()
```

## Best Practices

1. **Type Safety First**: Let the compiler catch errors with field names and types
2. **Create New Builders**: Use `FilterBuilder.empty[T]` for each independent filter
3. **Use Method Chaining**: Add multiple conditions via chaining before calling `and()` or `or()`
4. **Combine Builders**: Use `or(builder1, builder2, ...)` to combine multiple builders
5. **Nested Structures**: Use `nestedField` or `nested` for accessing nested object properties
6. **Comments for Clarity**: Add `comment()` to document complex filter logic

## Supported Types

Operators work with the following type classes:

- **`BSONWriter[A]`**: Any type with a BSON writer implementation
- **`MongoComparable[T, Field, A]`**: Ensures type compatibility between field and value
- **`Ordered[A]`**: Required for comparison operators (`$gt`, `$gte`, `$lt`, `$lte`)
  - Implemented for `Numeric[T]` types (Int, Long, Double, BigDecimal, etc.)
  - Implemented for `Temporal` types (java.time types)

## See Also

- [BSON Documentation](../README.md) - Overview of BSON handling
- [Builder Documentation](./README.md) - General builder patterns in ReactiveMongo
