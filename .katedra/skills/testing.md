# SKILL: Backend Testing Strategy

## 1. CONTEXT OF ACTIVATION (C_σ)
- **Trigger:** Writing automated tests for backend components, fixing broken builds, or verifying business logic before merging.
- **Exclusion:** Does not apply to frontend testing or infrastructure deployments.

## 2. STRICT ARCHITECTURAL RULES (T_σ)
- **Frameworks:** JUnit 5 (Jupiter), Mockito for mocking, AssertJ for assertions, and Spring Boot Test for context loading.
- **Unit Testing:** 
  - Isolate logic. Services should be tested by mocking Repositories (`@ExtendWith(MockitoExtension.class)`).
  - Fast execution. Do not load the Spring Context for unit tests.
- **Integration Testing:**
  - Test Controller endpoints using `@WebMvcTest` (mocking the service layer) or full `@SpringBootTest` with Testcontainers if verifying database interactions.
- **Naming Conventions:**
  - Test classes must end in `Test` (e.g., `CourseServiceTest`).
  - Test methods must clearly describe the scenario and expected outcome (e.g., `shouldThrowExceptionWhenCourseNotFound()`).

## 3. STANDARD OPERATING PROCEDURE (π_σ)
1. **Identify Scope:** Determine if the logic requires a unit test (business logic in Service) or an integration test (API routing in Controller).
2. **Setup Mocks:** Use `@Mock` and `@InjectMocks` appropriately. Define behavior using `given().willReturn()`.
3. **Execution & Assertion:** Call the method under test and verify results using AssertJ (`assertThat()`). Verify interactions using `verify()`.

## 4. COMPACT RECIPE (FEW-SHOT)
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
