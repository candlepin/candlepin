# Roadmap

## Features
* Re-orient Candlepin to be "JSON-first" in API definition.  As it stands, the
  `swagger.json` file must be downloaded from a deployed Candlepin instance and
  then used to generate the client classes.  It would be better to take an
  existing JSON file as a starting point, and then use it to generate resource
  interfaces that Candlepin implements.  All the OpenAPI annotations could then
  be removed.
* Support X509 client authentication in `ApiClientBuilder`
* Use Apache Cactus or perhaps Arquillian (which currently doesn't support JUnit
  5) to actually have the Boot application deploy a running Candlepin via an
  embedded Tomcat instance.  This would allow the test suite to be
  self-contained (useful for containers, for example) and in theory it would be
  possible to go from client code to server code in the same debugger.

## Quality of Life Improvements
* Link the two Spring contexts that are created when running the JAR directly.
  Right now, Spring Boot creates a context that's used by `JUnitBootstrap` and
  then that context is never referenced again.  Tests create their own context
  when they start through the use of the JUnit `SpringExtension`.
  Unfortunately, the initial `JUnitBootstrap` context is the context that has
  access to arguments given to the CLI.  It would be nice to link the contexts
  somehow so tests could, for example, put themselves into DEBUG via a command
  line argument.  Right now, that can only be done by editing the
  functional-tests.properties file or by using a `-D` JVM argument.  And `-D`
  arguments aren't supported by the Gradle Spring Boot plugin.
* Colored output
* Being able to filter tests based on class and method name

# Bugs
* When running via IntelliJ, the root logger is set to DEBUG mode resulting in
  noisy output.  I've tried altering the behavior through the
  `logback-spring.xml` file and through options given to Spring via the
  `application.yaml` or `functional-tests.properties files.`

