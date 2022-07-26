# Candlepin - Java base spec tests
Java spec tests for candlepin. 
  
# Running Tests
To run spec tests use the following command.
```
./gradlew clean spec
```
By default it expects candlepin running on **localhost:8443**. Defaults can be changed in **settings.properties** located in test resources. You can also override separate defaults by passing them as **-Dsome.key=value** to gradle

> Tests can be run from IDEA or any other IDE as well

# Contributing
## Standards
- **SpecTest annotation** - The test class should use the SpecTest annotation.
- **Test Display Name** - Test should include the DisplayName annotation with a descriptive summary of the test. This should start either with the verb **should** or **should not**.
- **Test Name** - The name of the test should be the display name in camel case .

Example:
``` java
@SpecTest
class testClass {
    @Test
    @DisplayName("should retrieve content")
    public void shouldRetrieveContent() {
        // Test logic
    }

    @Test
    @DisplayName("should not retrieve content")
    public void shouldNotRetrieveContent() {
        // Test logic
    }
}
```