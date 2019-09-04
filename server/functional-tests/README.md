# The Functional Test Harness

## How Does It Work?
This application is a little different from a normal Spring Boot application.
The BootApplication class does not do a component scan nor does it enable
auto-configuration like a standard Spring Boot application does.  Instead, it
just creates a single ApplicationRunner bean, JUnitBootstrap, that invokes
JUnit.

The JUnit tests should all be annotated with @FunctionalTestCase.  That
annotation will create a Spring ApplicationContext defined by
FunctionalTestConfiguration.  FunctionalTestIntegration also has component-scan
enabled, so other classes annotated with @Component (or its kin) will also be
loaded into the BeanFactory.

The main point to remember is this: This test harness has **two**
ApplicationContexts.  One is defined by this class and should only be the beans
necessary to get JUnit up off the ground.  The other is created for each test by
virtue of the FunctionalTestCase class and consists of beans defined in
FunctionalTestConfiguration **and every class annotated with @Component
(or equivalent)**.

## Setting Arguments

Arguments can be sent in to the tests that will set values in the
various Properties classes. Just use the "-D" JVM syntax. For example
`-Dfunctional-tests.client.debug=true` will display debug information
detailing the HTTP requests and responses that tests are sending.

## Cleaning Up

The test framework has a bean of type TestManifest that is
thread-scoped. Throughout the run of a test, if you ask for a
TestManifest, you will get the same instance. When you create owners,
users, etc. during a test, they should be added to the TestManifest.
Using the TestUtil methods for creation is a simple way to ensure that
the objects are tracked. When the test completes, a Spring test
execution listener will load the TestManifest and execute a command
telling it to delete every test object. The `DirtiesContext` annotation
which is on our `FunctionalTest` annotation then instructs Spring to
reload the application context, thus giving you a new TestManifest
instance for the next test.

If you don't want the clean-up to be run, you can set
`functional-tests.cleanup=false` using either the `-D` syntax or by
editing `functional-tests.properties`.
