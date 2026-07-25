Ah, understood. You mean you want the **API contract defined in OpenAPI/Swagger format**, so that you can use **Swagger UI as the single source of truth** for the API specification.

For a Spring Boot project, I recommend using **OpenAPI 3.1/3.0-style documentation via `springdoc-openapi`** and designing the contract before implementing the controllers.

The architecture should be:

```text
OpenAPI Specification
        │
        ▼
Swagger UI
        │
        ▼
REST Controller
        │
        ▼
Service
        │
        ▼
Repository
        │
        ▼
PostgreSQL
```

Below is a complete OpenAPI contract you can use as the basis of the project.

---

# 1. OpenAPI Structure

I would define the API specification roughly like this:

```yaml
openapi: 3.0.3

info:
  title: Student Management API
  description: REST API for managing students, books, and courses
  version: 1.0.0

servers:
  - url: http://localhost:8080/api/v1
    description: Local development server

tags:
  - name: Students
  - name: Books
  - name: Courses
  - name: Student Books
  - name: Student Courses

paths: ...

components:
  schemas: ...

  responses: ...
```

The Swagger UI should expose the following resources:

```text
Students
Books
Courses
Student Books
Student Courses
```

---

# 2. Student API

## `POST /students`

Create a student.

### Request

```yaml
requestBody:
  required: true
  content:
    application/json:
      schema:
        $ref: "#/components/schemas/StudentCreateRequest"
```

Schema:

```yaml
StudentCreateRequest:
  type: object
  required:
    - studentCode
    - firstName
    - lastName
    - email
    - dateOfBirth

  properties:
    studentCode:
      type: string
      example: STU001

    firstName:
      type: string
      minLength: 1
      example: John

    lastName:
      type: string
      minLength: 1
      example: Smith

    email:
      type: string
      format: email
      example: john.smith@example.com

    dateOfBirth:
      type: string
      format: date
      example: 2002-05-10
```

### Response

```yaml
responses:
  "201":
    description: Student created successfully
    headers:
      Location:
        schema:
          type: string
    content:
      application/json:
        schema:
          $ref: "#/components/schemas/StudentResponse"

  "400":
    $ref: "#/components/responses/ValidationError"

  "409":
    description: Student code or email already exists
```

---

## `GET /students`

Retrieve a paginated list of students.

Parameters:

```yaml
parameters:
  - name: page
    in: query
    schema:
      type: integer
      minimum: 0
      default: 0

  - name: size
    in: query
    schema:
      type: integer
      minimum: 1
      maximum: 100
      default: 20

  - name: sort
    in: query
    schema:
      type: string
      example: lastName,asc

  - name: search
    in: query
    schema:
      type: string
      example: john
```

Response:

```yaml
"200":
  description: List of students
  content:
    application/json:
      schema:
        $ref: "#/components/schemas/PageStudentResponse"
```

---

## `GET /students/{studentId}`

```yaml
parameters:
  - name: studentId
    in: path
    required: true
    schema:
      type: integer
      format: int64
      example: 1
```

Response:

```yaml
"200":
  description: Student found
  content:
    application/json:
      schema:
        $ref: "#/components/schemas/StudentResponse"

"404":
  $ref: "#/components/responses/StudentNotFound"
```

---

## `PUT /students/{studentId}`

Request:

```yaml
requestBody:
  required: true
  content:
    application/json:
      schema:
        $ref: "#/components/schemas/StudentUpdateRequest"
```

Response:

```yaml
"200":
  description: Student updated
  content:
    application/json:
      schema:
        $ref: "#/components/schemas/StudentResponse"

"400":
  $ref: "#/components/responses/ValidationError"

"404":
  $ref: "#/components/responses/StudentNotFound"

"409":
  description: Student code or email already exists
```

---

## `DELETE /students/{studentId}`

Response:

```yaml
"204":
  description: Student deleted successfully

"404":
  $ref: "#/components/responses/StudentNotFound"
```

Behavior:

```text
DELETE Student
       │
       ├── Unassign all Books
       │
       ├── Delete Student-Course relationships
       │
       └── Delete Student
```

---

# 3. Book API

## `POST /books`

```yaml
requestBody:
  required: true
  content:
    application/json:
      schema:
        $ref: "#/components/schemas/BookCreateRequest"
```

Schema:

```yaml
BookCreateRequest:
  type: object
  required:
    - isbn
    - title
    - author
    - publishedDate

  properties:
    isbn:
      type: string
      example: 9780134685991

    title:
      type: string
      example: Effective Java

    author:
      type: string
      example: Joshua Bloch

    publishedDate:
      type: string
      format: date
      example: 2018-01-06
```

Response:

```yaml
"201":
  description: Book created
  content:
    application/json:
      schema:
        $ref: "#/components/schemas/BookResponse"
```

A newly created book has:

```json
{
  "studentId": null
}
```

---

## `GET /books`

Supports:

```text
page
size
sort
search
```

Response:

```yaml
"200":
  description: Paginated books
  content:
    application/json:
      schema:
        $ref: "#/components/schemas/PageBookResponse"
```

---

## `GET /books/{bookId}`

```yaml
"200":
  description: Book found

"404":
  $ref: "#/components/responses/BookNotFound"
```

---

## `PUT /books/{bookId}`

The request should update only book information.

It should **not** change ownership.

```yaml
BookUpdateRequest:
  type: object
  required:
    - isbn
    - title
    - author
    - publishedDate

  properties:
    isbn:
      type: string

    title:
      type: string

    author:
      type: string

    publishedDate:
      type: string
      format: date
```

Ownership is controlled separately through:

```text
POST /students/{studentId}/books/{bookId}
DELETE /students/{studentId}/books/{bookId}
```

---

## `DELETE /books/{bookId}`

```yaml
"204":
  description: Book deleted

"404":
  $ref: "#/components/responses/BookNotFound"
```

Deleting the book also removes its relationship with the student.

---

# 4. Student–Book Relationship

This is the `One-to-Many` relationship:

```text
Student 1 ───────── N Book
```

---

## `POST /students/{studentId}/books/{bookId}`

Assign a book to a student.

Response:

```yaml
"200":
  description: Book assigned successfully
  content:
    application/json:
      schema:
        $ref: "#/components/schemas/BookResponse"

"404":
  $ref: "#/components/responses/ResourceNotFound"

"409":
  description: Book already assigned to another student
```

Business rule:

```text
A Book can have only ONE Student owner.
```

Therefore:

```text
Book 1 → Student 1
```

Then:

```http
POST /students/2/books/1
```

should return:

```http
409 Conflict
```

rather than silently moving the book.

---

## `GET /students/{studentId}/books`

Response:

```yaml
"200":
  description: Books owned by student
  content:
    application/json:
      schema:
        type: array
        items:
          $ref: "#/components/schemas/BookResponse"
```

If there are no books:

```json
[]
```

---

## `GET /books/{bookId}/owner`

Response:

```yaml
"200":
  description: Book owner
  content:
    application/json:
      schema:
        $ref: "#/components/schemas/StudentSummaryResponse"

"404":
  description: Book does not exist or has no owner
```

---

## `DELETE /students/{studentId}/books/{bookId}`

Remove the ownership relationship.

Response:

```yaml
"204":
  description: Book unassigned successfully

"404":
  description: Student-book relationship not found
```

Important:

```text
This does NOT delete the Book.
```

The result is:

```text
Book.studentId = null
```

---

# 5. Course API

## `POST /courses`

Request:

```yaml
CourseCreateRequest:
  type: object
  required:
    - courseCode
    - name
    - credits

  properties:
    courseCode:
      type: string
      example: CS101

    name:
      type: string
      example: Introduction to Programming

    description:
      type: string
      example: Fundamentals of programming

    credits:
      type: integer
      minimum: 1
      example: 3
```

Response:

```yaml
"201":
  description: Course created
```

---

## `GET /courses`

Supports:

```text
page
size
sort
search
```

---

## `GET /courses/{courseId}`

```yaml
"200":
  description: Course found

"404":
  $ref: "#/components/responses/CourseNotFound"
```

---

## `PUT /courses/{courseId}`

Updates:

```text
courseCode
name
description
credits
```

---

## `DELETE /courses/{courseId}`

Response:

```yaml
"204":
  description: Course deleted
```

Behavior:

```text
Delete Course
      │
      ▼
Delete all student_course records
      │
      ▼
Delete Course
```

Students remain.

---

# 6. Student–Course Relationship

The initial implementation uses:

```text
Student N ───── N Course
```

with:

```text
student_course
```

---

## `POST /students/{studentId}/courses/{courseId}`

Enroll a student.

Response:

```yaml
"201":
  description: Student enrolled successfully
  content:
    application/json:
      schema:
        $ref: "#/components/schemas/EnrollmentResponse"

"404":
  $ref: "#/components/responses/ResourceNotFound"

"409":
  description: Student already enrolled
```

Schema:

```yaml
EnrollmentResponse:
  type: object
  properties:
    studentId:
      type: integer
      format: int64
      example: 1

    courseId:
      type: integer
      format: int64
      example: 101
```

---

## `GET /students/{studentId}/courses`

Response:

```yaml
"200":
  description: Courses enrolled by student
  content:
    application/json:
      schema:
        type: array
        items:
          $ref: "#/components/schemas/CourseResponse"
```

---

## `GET /courses/{courseId}/students`

Response:

```yaml
"200":
  description: Students enrolled in course
  content:
    application/json:
      schema:
        type: array
        items:
          $ref: "#/components/schemas/StudentSummaryResponse"
```

---

## `DELETE /students/{studentId}/courses/{courseId}`

Response:

```yaml
"204":
  description: Student unenrolled successfully

"404":
  description: Enrollment not found
```

---

# 7. OpenAPI Schemas

The most important part is to define reusable schemas.

## StudentResponse

```yaml
StudentResponse:
  type: object
  required:
    - id
    - studentCode
    - firstName
    - lastName
    - email
    - dateOfBirth

  properties:
    id:
      type: integer
      format: int64
      readOnly: true

    studentCode:
      type: string

    firstName:
      type: string

    lastName:
      type: string

    email:
      type: string
      format: email

    dateOfBirth:
      type: string
      format: date

    createdAt:
      type: string
      format: date-time
      readOnly: true

    updatedAt:
      type: string
      format: date-time
      readOnly: true
```

---

## StudentSummaryResponse

Used when another resource references a student.

```yaml
StudentSummaryResponse:
  type: object
  properties:
    id:
      type: integer
      format: int64

    studentCode:
      type: string

    firstName:
      type: string

    lastName:
      type: string

    email:
      type: string
      format: email
```

This avoids returning:

```text
Student
  └── Books
       └── Student
            └── Books
                 └── ...
```

which can cause recursive JSON serialization.

---

## BookResponse

```yaml
BookResponse:
  type: object
  properties:
    id:
      type: integer
      format: int64

    isbn:
      type: string

    title:
      type: string

    author:
      type: string

    publishedDate:
      type: string
      format: date

    studentId:
      type: integer
      format: int64
      nullable: true

    createdAt:
      type: string
      format: date-time

    updatedAt:
      type: string
      format: date-time
```

---

## CourseResponse

```yaml
CourseResponse:
  type: object
  properties:
    id:
      type: integer
      format: int64

    courseCode:
      type: string

    name:
      type: string

    description:
      type: string

    credits:
      type: integer

    createdAt:
      type: string
      format: date-time

    updatedAt:
      type: string
      format: date-time
```

---

# 8. Error Schema

Define one reusable error schema.

```yaml
ErrorResponse:
  type: object
  required:
    - timestamp
    - status
    - error
    - code
    - message
    - path

  properties:
    timestamp:
      type: string
      format: date-time

    status:
      type: integer
      example: 404

    error:
      type: string
      example: Not Found

    code:
      type: string
      example: STUDENT_NOT_FOUND

    message:
      type: string
      example: Student with id 100 was not found

    path:
      type: string
      example: /api/v1/students/100
```

Validation errors can extend this with:

```yaml
fieldErrors:
  type: object
  additionalProperties:
    type: string
```

---

# 9. Recommended OpenAPI Endpoint Structure

Your Swagger UI should ultimately look approximately like this:

```text
Student Management API

▼ Students
   POST   /students
   GET    /students
   GET    /students/{studentId}
   PUT    /students/{studentId}
   DELETE /students/{studentId}

▼ Books
   POST   /books
   GET    /books
   GET    /books/{bookId}
   PUT    /books/{bookId}
   DELETE /books/{bookId}

▼ Student Books
   POST   /students/{studentId}/books/{bookId}
   GET    /students/{studentId}/books
   GET    /books/{bookId}/owner
   DELETE /students/{studentId}/books/{bookId}

▼ Courses
   POST   /courses
   GET    /courses
   GET    /courses/{courseId}
   PUT    /courses/{courseId}
   DELETE /courses/{courseId}

▼ Student Courses
   POST   /students/{studentId}/courses/{courseId}
   GET    /students/{studentId}/courses
   GET    /courses/{courseId}/students
   DELETE /students/{studentId}/courses/{courseId}

▼ Schemas
   StudentCreateRequest
   StudentUpdateRequest
   StudentResponse
   StudentSummaryResponse

   BookCreateRequest
   BookUpdateRequest
   BookResponse

   CourseCreateRequest
   CourseUpdateRequest
   CourseResponse

   EnrollmentResponse
   ErrorResponse
```

---

## My recommendation for your project

I would actually make the **OpenAPI YAML file the formal API contract**, and then implement Spring Boot from it.

The development workflow becomes:

```text
                    API DESIGN
                        │
                        ▼
                  openapi.yaml
                        │
              ┌─────────┴─────────┐
              ▼                   ▼
         Swagger UI          API Contract
              │                   │
              └─────────┬─────────┘
                        ▼
                Spring Controllers
                        │
                        ▼
                    Services
                        │
                        ▼
                    JPA / DB
```

For your internship project, this is a good approach because you can show the interviewer:

> "I first defined the REST API contract using OpenAPI. The contract specifies resources, request/response schemas, validation constraints, HTTP status codes, and relationship operations. I then implemented the Spring Boot controllers and services according to this contract."

That demonstrates a more professional workflow than simply writing controllers first and adding Swagger annotations afterward.

If you want, the **next step I recommend is to create a complete `openapi.yaml` file** containing all 22 endpoints, reusable schemas, parameters, pagination, and error responses, ready to import directly into **Swagger Editor/Postman** and then use as the contract for your Spring Boot implementation.
