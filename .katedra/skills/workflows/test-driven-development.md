---
name: test-driven-development
description: Automated TDD workflow and backend testing strategy
---

# Test-Driven Development (TDD) & Backend Testing Strategy

## Overview

Write the test first. Watch it fail. Write minimal code to pass.

**Core principle:** If you didn't watch the test fail, you don't know if it tests the right thing.

**Violating the letter of the rules is violating the spirit of the rules.**

## When to Use

**Always:**
- New features
- Bug fixes
- Refactoring
- Behavior changes

**Exceptions (ask your human partner):**
- Generated code
- Configuration files

## The Iron Law

```
NO PRODUCTION CODE WITHOUT A FAILING TEST FIRST
```

Write code before the test? Delete it. Start over.

## Red-Green-Refactor

1. **RED - Write Failing Test:** Write one minimal test showing what should happen.
2. **Verify RED - Watch It Fail:** Execute the test and see it fail (with `./mvnw test -Dtest=MyTestClass`).
3. **GREEN - Write Minimal Code:** Write the simplest code to make the test pass.
4. **Verify GREEN - Watch It Pass:** Run tests again and verify success.
5. **REFACTOR - Clean Up:** Refactor the code while keeping the tests green.

## STRICT ARCHITECTURAL RULES (T_σ)
- **Frameworks:** JUnit 5 (Jupiter), Mockito for mocking, AssertJ for assertions, and Spring Boot Test for context loading.
- **Unit Testing:** 
  - Isolate logic. Services should be tested by mocking Repositories (`@ExtendWith(MockitoExtension.class)`).
  - Fast execution. Do not load the Spring Context for unit tests.
- **Integration Testing:**
  - Test Controller endpoints using `@WebMvcTest` (mocking the service layer) or full `@SpringBootTest` with Testcontainers if verifying database interactions.
- **Naming Conventions:**
  - Test classes must end in `Test` (e.g., `CourseServiceTest`).
  - Test methods must clearly describe the scenario and expected outcome (e.g., `shouldThrowExceptionWhenCourseNotFound()`).

## COMPACT RECIPE (FEW-SHOT)
Input: "Write a test for course retrieval"
Output Expected:
```java
@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock
    private CourseRepository repository;

    @InjectMocks
    private CourseService service;

    @Test
    void shouldReturnCourseWhenFound() {
        // Arrange
        Course course = new Course("123", "Math");
        given(repository.findById("123")).willReturn(Optional.of(course));

        // Act
        CourseResponseDTO result = service.getCourseById("123");

        // Assert
        assertThat(result.title()).isEqualTo("Math");
        verify(repository).findById("123");
    }
}
```

## Verification Checklist

Before marking work complete:
- [ ] Every new function/method has a test.
- [ ] Watched each test fail before implementing.
- [ ] Each test failed for the expected reason.
- [ ] Wrote minimal code to pass each test.
- [ ] All tests pass.
- [ ] Tests use real code (mocks only if unavoidable).
