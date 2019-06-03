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
