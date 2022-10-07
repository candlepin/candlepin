# Candlepin - Java base spec tests
Java spec tests for Candlepin

# Running Tests
To run spec tests use the following command:
```
./gradlew clean spec
```

By default it expects Candlepin running on **localhost:8443**. Defaults can be changed in
**settings.properties** located in test resources. You can also override separate defaults
by passing them as **-Dsome.key=value** to gradle

> Tests can be run from IDEA or any other IDE as well

# Contributing
When contributing new spec tests, certain guidelines should be followed to ensure they are
easy to read, review, and maintain; and that they behave as expected when run in our various
CI environments and test suites.

There may be cases where a given convention or standard should be ignored, but these are
expected to be exceptional cases that should be explicitly called out and approved during
the review process.


## Standards
### @SpecTest annotation
The test class should use the `@SpecTest` annotation. This custom annotation encapsulates
the necessary bits and config for JUnit to recognize and run the tests.

```java
@SpecTest
class testClass {
  // individual tests here
}

```

### Test Isolation
Isolation is critical for test maintenance and stability. In general, this refers to a
test’s ability to run in any context without affecting other tests or being affected by
other tests. The more a test relies on a predefined or shared state, the less isolated it is
and more likely it is to be influenced by parallel operations or changes made by other tests.

Tests should be written to be as isolated as possible, with shared or global data/state
being reserved specifically for cases where creating the data is always going to affect the
global state (such as rules changes), or where creating data on a per-test basis is not
viable for some reason. Even in such cases, the tests where such shared state is necessary
should be compartmentalized in a nested test, or its own isolated test suite. Test data
being tedious to setup is NOT a good reason to forego test isolation practices.

### Test Parallelization
Each test should be designed with the intent to be run in parallel, in potentially any
order, without respect to the containing suite. There will be exceptions for some cases, but
to minimize the number of tests which need to be run out-of-band, tests should strive to be
as isolated as possible, following the test isolation guidelines below.

### Test Coupling
Test coupling is a consequence of two or more tests relying on the same initial state or
data, and/or having the behavior of one test affect another. Generally speaking, this should
avoided wherever possible, as it can make test maintenance difficult as test suites grow and
change over time.

In general, one test’s behavior should not affect another test, unless such behavior is
entirely unavoidable; as is the case with some class tests surrounding global state
manipulation (i.e. rules, jobs, status). In these cases, the test in question should be
isolated and pulled out of the parallel tests to be run in serial, after all other parallel
tests complete.

For initial data and state sharing, the tests should avoid performing operations which
modify the state, as this allows performing the setup a single time, rather than needing to
generate it for each test.

### Operating Mode-Aware Testing
Candlepin currently has, at the time of writing, two operating modes: "hosted" and
"standalone," which affect the behavior of a handful of operations. In general, Candlepin
behaves the same way regardless of operating mode, but some tests may only be valid within a
given operating mode. In such cases, the test should be written entirely for the target
operating mode, and annotated with either `@OnlyInHosted` or `@OnlyInStandalone` as
appropriate.

There are methods for checking Candlepin's operating mode at runtime, but their use should
be extremely rare and selective, as they have a tendency to make tests more brittle and
difficult to maintain.


## Testing Guidelines
To summarize some of the requirements and design goals, the following guidelines should be
followed wherever possible.

### Naming Conventions and Documentation
- Test method names should be terse camel-cased describing the target and test
- Omit usage of the @displayname annotation; use Javadoc blocks for instances where the test
  method name alone cannot describe the test
- Use consistent verbiage and subject/predicate ordering in test and variable naming

### Test Isolation and Design
- Avoid beforeEach and beforeAll for trivial data creation
- Data created “globally” (beforeAll/beforeEach) should only be used in cases where the
  tests sharing the data will be read-only
- Shared data creation should be narrowly scoped, and encapsulated in a nested suite or
  separate suite
- Don’t focus on data cleanup; write more focused assertions on data specific to the test
- Write cleanup routines only in exceptional cases where needed (truly global data/state)

### Writing Mode-Aware Tests
- Avoid differentiating between operating modes wherever possible; tests should strive to be
  mode-agnostic
- Use the annotation-based mode specifier to either run or skip the test entirely when the
  functionality under test is dependent on the operating mode
- Only use the in-test check for short state initialization that differs between modes and
  cannot be performed in a mode-agnostic way

## Example Tests

### Fully Isolated Test
This test creates its clients and data local to the test, and does not perform any
count-based verification which could be affected by any global state changes.

```java
@Test
public void shouldAllowOrgAdminsToFetchContent() throws Exception {
    ApiClient adminClient = ApiClients.admin();
    OwnerDTO owner = this.createOwner(adminClient);

    ContentDTO created = adminClient.ownerContent().createContent(owner.getKey(), Content.random());
    assertNotNull(created);

    ApiClient orgAdminClient = this.createOrgAdminClient(adminClient, owner);
    ContentDTO fetched = orgAdminClient.ownerContent().getOwnerContent(owner.getKey(), created.getId());
    assertNotNull(fetched);
    assertEquals(created, fetched);
}
```

### Isolated Tests with Shared Initialization/Data
The following tests use shared data to perform read-only operations (when functioning
correctly) against the data. These tests and their shared setup are encapsulated in a nested
class which has a lifecycle of `PER_CLASS` set, to ensure the setup is only performed a
single time.

The tests are written such that even if additional data were to leak in from other tests,
they would be unaffected due to explicit use of the IDs of the data created during the setup
step, and the omission of count-based verifications.

An optional cleanup step could be performed here as well if needed with a method annotated
with the `@AfterAll` annotation, but that is not done here.


```java
@Nested
@OnlyInHosted
@TestInstance(Lifecycle.PER_CLASS)
public class LockedEntityTests {

    private OwnerDTO owner;
    private ContentDTO targetEntity;

    @BeforeAll
    public void setup() throws Exception {
        // Create an upstream product, attach some content, attach it to a subscription, and
        // then refresh to pull it down as a "locked" content.
        ApiClient adminClient = ApiClients.admin();

        OwnerDTO owner = Owners.random();

        ContentDTO content = Content.random();

        ProductContentDTO pc = new ProductContentDTO()
            .content(content)
            .enabled(true);

        ProductDTO eng = Products.randomEng()
            .addProductContentItem(pc);

        ProductDTO sku = Products.randomSKU()
            .addProvidedProductsItem(eng);

        SubscriptionDTO subscription = Subscriptions.random(owner, sku);

        adminClient.hosted().createOwner(owner);
        adminClient.hosted().createSubscription(subscription, true);

        this.owner = adminClient.owners().createOwner(owner);
        assertNotNull(this.owner);
        assertEquals(owner.getKey(), this.owner.getKey());

        AsyncJobStatusDTO job = adminClient.owners().refreshPools(owner.getKey(), false);
        job = adminClient.jobs().waitForJob(job);
        assertEquals("FINISHED", job.getState());

        this.targetEntity = adminClient.ownerContent().getOwnerContent(owner.getKey(), content.getId());
        assertNotNull(this.targetEntity);
        assertEquals(content.getId(), this.targetEntity.getId());
    }

    @Test
    public void shouldNotAllowUpdatingLockedContent() throws Exception {
        ApiClient adminClient = ApiClients.admin();

        ContentDTO update = Content.copy(this.targetEntity)
            .name("updated content name")
            .label("updated label");

        assertForbidden(() -> adminClient.ownerContent()
            .updateContent(this.owner.getKey(), update.getId(), update));
    }

    @Test
    public void shouldNotAllowDeletingLockedContent() throws Exception {
        ApiClient adminClient = ApiClients.admin();

        assertForbidden(() -> adminClient.ownerContent()
            .remove(this.owner.getKey(), this.targetEntity.getId()));
    }
}

```
