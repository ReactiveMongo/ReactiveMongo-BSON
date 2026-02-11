# Projections

The `ProjectionBuilder` provides a type-safe way to control which fields are returned in MongoDB query results. It ensures at compile-time that fields exist and helps optimize performance by reducing network traffic and memory usage.

## Basic Setup

```scala
import reactivemongo.api.bson.builder.ProjectionBuilder

case class User(
  id: String,
  name: String,
  age: Int,
  email: String,
  isActive: Boolean)

val builder = ProjectionBuilder.empty[User]
```

## Why Use Projections

Projections are essential for:

- **Performance**: Reducing network traffic and memory usage by fetching only needed fields
- **Security**: Hiding sensitive fields like passwords or internal IDs from query results
- **API Design**: Returning only relevant data to clients and consumers

## Basic Projections

### Including Fields

Specify which fields to include in the result:

```scala
import reactivemongo.api.bson.builder.ProjectionBuilder

ProjectionBuilder.empty[User]
  .includes(Symbol("name"))
  .includes(Symbol("email"))
  .result()
// Result: { "name": 1, "email": 1 }
```

### Excluding Fields

Specify which fields to exclude (all others will be included):

```scala
import reactivemongo.api.bson.builder.ProjectionBuilder

ProjectionBuilder.empty[User]
  .excludes(Symbol("email"))
  .excludes(Symbol("isActive"))
  .result()
// Result: { "email": 0, "isActive": 0 }
```

### Mixed Include/Exclude

Combine includes and excludes in a single projection:

```scala
import reactivemongo.api.bson.builder.ProjectionBuilder

ProjectionBuilder.empty[User]
  .includes(Symbol("name"))
  .includes(Symbol("age"))
  .excludes(Symbol("email"))
  .result()
// Result: { "name": 1, "age": 1, "email": 0 }
```

> **Note**: MongoDB allows mixing includes and excludes, but with the `_id` field as an exception. You can exclude `_id` while including other fields, or include `_id` while excluding other fields.

## Nested Field Projections

For complex document structures with nested objects, the `ProjectionBuilder` provides methods to handle nested field projections while maintaining type safety.

### Single Nested Field

Project specific fields from nested objects:

```scala
import reactivemongo.api.bson.builder.ProjectionBuilder

case class Address(
  street: String,
  city: String,
  zipCode: String,
  country: String)

case class UserWithAddress(
  name: String,
  age: Int,
  email: String,
  homeAddress: Address,
  workAddress: Address)

ProjectionBuilder.empty[UserWithAddress]
  .includes(Symbol("name"))
  .nestedField(Symbol("homeAddress")).at { addressBuilder =>
    addressBuilder.includes(Symbol("city"))
  }
  .result()
// Result: { "name": 1, "homeAddress.city": 1 }
```

### Multiple Path Segments

Navigate through multiple nesting levels:

```scala
import reactivemongo.api.bson.builder.ProjectionBuilder

case class Coordinates(lat: Double, lon: Double)
case class Location(name: String, coords: Coordinates)
case class Place(id: String, description: String, location: Location)

ProjectionBuilder.empty[Place]
  .includes(Symbol("description"))
  .nested(Symbol("location"), Symbol("coords")).at { coordsBuilder =>
    coordsBuilder.includes(Symbol("lat"))
    coordsBuilder.includes(Symbol("lon"))
  }
  .result()
// Result: { "description": 1, "location.coords.lat": 1, "location.coords.lon": 1 }
```

### Multiple Nested Projections

Project from multiple nested objects:

```scala
import reactivemongo.api.bson.builder.ProjectionBuilder

ProjectionBuilder.empty[UserWithAddress]
  .includes(Symbol("name"))
  .includes(Symbol("email"))
  .nestedField(Symbol("homeAddress")).at { addressBuilder =>
    addressBuilder.includes(Symbol("city"))
  }
  .nestedField(Symbol("workAddress")).at { addressBuilder =>
    addressBuilder.includes(Symbol("street"))
  }
  .result()
// Result: { 
//   "name": 1, 
//   "email": 1,
//   "homeAddress.city": 1, 
//   "workAddress.street": 1 
// }
```

### Positional Operator

Project the first matching element from an array:

```scala
import reactivemongo.api.bson.builder.ProjectionBuilder

case class Enrollment(courseId: String, grade: String)
case class StudentRecord(
  name: String,
  enrollments: Seq[Enrollment])

// When filtering by enrollments.courseId, project matching element
ProjectionBuilder.empty[StudentRecord]
  .includes(Symbol("name"))
  .positional(Symbol("enrollments"))
  .result()
// Result: { "name": 1, "enrollments.$": 1 }
// Use with filter: { "enrollments.courseId": "CS101" }
```

## Conditional Projections

### Building Projections Dynamically

Construct projections based on runtime parameters:

```scala
import reactivemongo.api.bson.builder.ProjectionBuilder

def buildUserProjection(
  includeName: Boolean,
  includeEmail: Boolean,
  includeAge: Boolean
): ProjectionBuilder[User] = {
  
  val builder = ProjectionBuilder.empty[User]
  
  if (includeName) builder.includes(Symbol("name"))
  if (includeEmail) builder.includes(Symbol("email"))
  if (includeAge) builder.includes(Symbol("age"))
  
  builder
}

// Usage
val projection = buildUserProjection(
  includeName = true,
  includeEmail = false,
  includeAge = true
)
// Result: { "name": 1, "age": 1 }
```

### Role-Based Projections

Create different projections based on user roles:

```scala
import reactivemongo.api.bson.builder.ProjectionBuilder

sealed trait UserRole
case object Admin extends UserRole
case object Public extends UserRole

def userProjectionForRole(role: UserRole): ProjectionBuilder[User] = {
  val builder = ProjectionBuilder.empty[User]
    .includes(Symbol("name"))
    .includes(Symbol("age"))
  
  role match {
    case Admin =>
      builder
        .includes(Symbol("email"))
        .includes(Symbol("isActive"))
    
    case Public =>
      builder
        .excludes(Symbol("email"))
        .excludes(Symbol("isActive"))
  }
  
  builder
}

{
  // Usage
  val publicProjection = userProjectionForRole(Public)
  // Result: { "name": 1, "age": 1, "email": 0, "isActive": 0 }

  val adminProjection = userProjectionForRole(Admin)
  // Result: { "name": 1, "age": 1, "email": 1, "isActive": 1 }
}
```

## Integration with MongoDB Operations

### Find with Projection

```scala
import reactivemongo.api.bson.builder.{ FilterBuilder, ProjectionBuilder }

{
  // Build filter
  val filter = FilterBuilder.empty[User]
    .eq(Symbol("isActive"), true)
    .gte(Symbol("age"), 18)
    .and()
  
  // Build projection
  val projection = ProjectionBuilder.empty[User]
    .includes(Symbol("name"))
    .includes(Symbol("age"))
    .excludes(Symbol("email"))
    .result()
  
  // Use in find operation
  // collection.find(filter, Some(projection)).cursor[User]()
}
```

### Aggregation Pipeline with Projection

```scala
import reactivemongo.api.bson.builder.ProjectionBuilder
import reactivemongo.api.bson.{ BSONDocument, BSONArray }

def aggregateWithProjection(): BSONArray = {
  // Build projection for $project stage
  val projection = ProjectionBuilder.empty[User]
    .includes(Symbol("name"))
    .includes(Symbol("age"))
    .result()
  
  // Use in aggregation pipeline
  BSONArray(
    BSONDocument("$match" -> BSONDocument("isActive" -> true)),
    BSONDocument("$project" -> projection),
    BSONDocument("$sort" -> BSONDocument("name" -> 1))
  )
}
```

## Type Safety

The `ProjectionBuilder` uses compile-time type checking to ensure:

1. **Field Existence**: The compiler verifies that fields exist in the case class
2. **Type Consistency**: Nested projections maintain type information
3. **Proper Navigation**: Nested field access is validated at compile time

```scala
import reactivemongo.api.bson.builder.ProjectionBuilder

// This will compile:
ProjectionBuilder.empty[User]
  .includes(Symbol("name"))
  .result()

// This will NOT compile (nonexistent field):
// ProjectionBuilder.empty[User]
//   .includes(Symbol("nonexistent"))
//   .result()

// This will NOT compile (wrong nested type):
// case class InvalidNested(value: String)
// ProjectionBuilder.empty[UserWithAddress]
//   .nestedField[InvalidNested](Symbol("homeAddress"))
//   .result()
```

## Building Projections

### Result Method

Convert the builder to a `BSONDocument`:

```scala
import reactivemongo.api.bson.builder.ProjectionBuilder

ProjectionBuilder.empty[User]
  .includes(Symbol("name"))
  .includes(Symbol("email"))
  .result()
```

### Chaining Operations

Add multiple field operations via method chaining:

```scala
import reactivemongo.api.bson.builder.ProjectionBuilder

ProjectionBuilder.empty[User]
  .includes(Symbol("name"))
  .includes(Symbol("age"))
  .excludes(Symbol("email"))
  .excludes(Symbol("isActive"))
  .result()
```

## Mutable Builder Pattern

The `ProjectionBuilder` is mutable and should not be shared between contexts:

```scala
import reactivemongo.api.bson.builder.ProjectionBuilder

{
  // ✅ Safe: Each projection has its own builder
  val projection1 = ProjectionBuilder.empty[User]
    .includes(Symbol("name"))
    .result()
  
  val projection2 = ProjectionBuilder.empty[User]
    .includes(Symbol("email"))
    .result()
  
  // ❌ Avoid: Storing builder in variable for reuse
  val savedBuilder = ProjectionBuilder.empty[User]
    .includes(Symbol("name"))
  // Don't reuse savedBuilder elsewhere as it's mutable
}
```

## Common Use Cases

### API Response Projection

Hide sensitive data in public API responses:

```scala
import reactivemongo.api.bson.builder.ProjectionBuilder

case class UserAccount(
  id: String,
  username: String,
  email: String,
  passwordHash: String,
  internalNotes: String,
  createdAt: Long)

// Public API projection
ProjectionBuilder.empty[UserAccount]
  .includes(Symbol("id"))
  .includes(Symbol("username"))
  .excludes(Symbol("email"))
  .excludes(Symbol("passwordHash"))
  .excludes(Symbol("internalNotes"))
  .result()
// Result: { "id": 1, "username": 1, "email": 0, "passwordHash": 0, "internalNotes": 0 }
```

### Performance Optimization

Fetch only required fields for lists:

```scala
import reactivemongo.api.bson.builder.ProjectionBuilder

case class BlogPost(
  id: String,
  title: String,
  summary: String,
  content: String,  // Large field
  author: String,
  tags: Seq[String],
  comments: Seq[String])  // Large array

// List view projection (without large fields)
val listViewProjection = ProjectionBuilder.empty[BlogPost]
  .includes(Symbol("id"))
  .includes(Symbol("title"))
  .includes(Symbol("summary"))
  .includes(Symbol("author"))
  .includes(Symbol("tags"))
  .result()
// Result: { "id": 1, "title": 1, "summary": 1, "author": 1, "tags": 1 }

// Detail view projection (everything)
val detailViewProjection = ProjectionBuilder.empty[BlogPost]
  .includes(Symbol("id"))
  .includes(Symbol("title"))
  .includes(Symbol("content"))
  .includes(Symbol("author"))
  .includes(Symbol("tags"))
  .result()
```

### Nested Data Selection

Extract specific nested information:

```scala
import reactivemongo.api.bson.builder.ProjectionBuilder

case class OrderItem(productId: String, quantity: Int, price: Double, tax: Double)

case class ShippingAddress(
  street: String,
  city: String,
  state: String,
  zipCode: String,
  country: String)

case class Order(
  orderId: String,
  userId: String,
  items: Seq[OrderItem],
  id: String,
  quantity: Int,
  price: Double,
  tax: Double,
  shippingAddress: ShippingAddress,
  billingAddress: ShippingAddress,
  totalAmount: Double)

// Shipping label projection (minimal info)
val shippingLabelProjection = ProjectionBuilder.empty[Order]
  .includes(Symbol("orderId"))
  .nestedField(Symbol("shippingAddress")).at { addressBuilder =>
    addressBuilder.includes(Symbol("street"))
    addressBuilder.includes(Symbol("city"))
    addressBuilder.includes(Symbol("state"))
    addressBuilder.includes(Symbol("zipCode"))
    addressBuilder.includes(Symbol("country"))
  }
  .result()
// Result: { 
//   "orderId": 1,
//   "shippingAddress.street": 1,
//   "shippingAddress.city": 1,
//   "shippingAddress.state": 1,
//   "shippingAddress.zipCode": 1,
//   "shippingAddress.country": 1
// }
```

## Best Practices

1. **Performance First**: Always project only the fields you need to reduce network traffic
2. **Security Conscious**: Explicitly exclude sensitive fields in projections for external APIs
3. **Role-Based Access**: Create different projections based on user permissions
4. **List vs Detail**: Use minimal projections for list views, full projections for detail views
5. **Type Safety**: Leverage compile-time checking to catch field name errors early
6. **Nested Navigation**: Use `nestedField` and `nested` for type-safe nested field access
7. **Create New Builders**: Use `ProjectionBuilder.empty[T]` for each independent projection
9-8. **Immutable Results**: Call `result()` to get the final `BSONDocument` projection

## Supported Operations

| Operation | Method | Description |
|-----------|--------|-------------|
| **Include** | `.includes()` | Include specific fields in the result (value: 1) |
| **Exclude** | `.excludes()` | Exclude specific fields from the result (value: 0) |
| **Positional** | `.positional()` | Project first matching array element (`$`) |
| **Nested Field** | `.nestedField()` | Navigate into single nested object |
| **Nested Path** | `.nested()` | Navigate through multiple nesting levels |

## Using with ExprBuilder

The `ProjectionBuilder` integrates with `ExprBuilder` to create computed or derived fields in query results using MongoDB aggregation expressions. This allows you to transform, calculate, and combine field values within the projection stage.

```scala
import reactivemongo.api.bson.builder.{ ExprBuilder, ProjectionBuilder }

{
  val exprBuilder = ExprBuilder.empty[Order]
  
  // Calculate total: quantity * price
  val quantity = exprBuilder.select(Symbol("quantity"))
  val price = exprBuilder.select(Symbol("price"))
  val total = exprBuilder.multiply(quantity, price)
  
  // Calculate tax amount: total * tax
  val tax = exprBuilder.select(Symbol("tax"))
  val taxAmount = exprBuilder.multiply(total, tax)
  
  // Calculate grand total: total + taxAmount
  val grandTotal = exprBuilder.add(total, taxAmount)
  
  // Create projection with calculated fields
  val projection = ProjectionBuilder.empty[Order]
    .includes(Symbol("id"))
    .project("total", total)
    .project("taxAmount", taxAmount)
    .project("grandTotal", grandTotal)
    .result()
  // Result: { 
  //   "id": 1,
  //   "total": { "$multiply": ["$quantity", "$price"] },
  //   "taxAmount": { "$multiply": [{ "$multiply": ["$quantity", "$price"] }, "$tax"] },
  //   "grandTotal": { "$add": [{ "$multiply": ["$quantity", "$price"] }, { "$multiply": [{ "$multiply": ["$quantity", "$price"] }, "$tax"] }] }
  // }
}
```

This demonstrates adding computed fields to projections. The `project()` method accepts a field name and any expression built with `ExprBuilder`, enabling field transformations including arithmetic operations, conditionals, string manipulations, array operations, and nested field calculations. This allows server-side computation and reduced network traffic by calculating derived values in MongoDB before returning results.

For detailed documentation on all available expression operations, see [Expressions Documentation](./expr.md)

## Performance Considerations

### Network Bandwidth

```scala
import reactivemongo.api.bson.builder.ProjectionBuilder

// ❌ Bad: Fetching large unnecessary fields
val badProjection = ProjectionBuilder.empty[BlogPost]
  // Returns entire documents including large content field

// ✅ Good: Fetch only what's needed
val goodProjection = ProjectionBuilder.empty[BlogPost]
  .includes(Symbol("title"))
  .includes(Symbol("summary"))
  .result()
```

### Index Coverage

```scala
import reactivemongo.api.bson.builder.ProjectionBuilder

// When fields in projection are covered by an index,
// MongoDB can return results without examining documents

case class Product(sku: String, name: String, price: Double, description: String)

// If index exists on { sku: 1, name: 1, price: 1 }
val coveredProjection = ProjectionBuilder.empty[Product]
  .includes(Symbol("sku"))
  .includes(Symbol("name"))
  .includes(Symbol("price"))
  .result()
// MongoDB can satisfy this query using only the index
```

## See Also

- [Filters Documentation](./filters.md) - Building type-safe MongoDB query filters
- [BSON Documentation](../README.md) - Overview of BSON handling in ReactiveMongo
- [Builder Documentation](./README.md) - General builder patterns in ReactiveMongo
