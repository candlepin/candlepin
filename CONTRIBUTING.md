# Contributing guide

**Want to contribute? Great!**  We will try to make it easy. All contributions, even the smaller ones are more than welcome.
* [Legal](#legal)
* [Before you contribute](#before-you-contribute)
  + [Code reviews](#code-reviews)
  + [Coding Guidelines](#coding-guidelines)
  + [Continuous Integration](#continuous-integration)
  + [Testing and documentation](#testing-and-documentation)
* [Setup](#setup)
  + [IDE Config and Code Style](#ide-config-and-code-style)
    - [Eclipse Setup](#eclipse-setup)
    - [IDEA Setup](#idea-setup)
* [Building Candlepin](#building-candlepin)
  + [Building with Gradle](#building-with-gradle)
  + [Building with Maven](#building-with-maven)
* [JSS Candlepin Crypto Extension](#jss-candlepin-crypto-extension)
* [Miscellaneous](#miscellaneous)
* [Frequently Asked Questions](#frequently-asked-questions)

## Legal
TODO

## Before you contribute

To contribute, use GitHub Pull Requests, from your  **own**  fork.
Also, make sure you have set up your Git authorship correctly:
```
git config --global user.name "Your Full Name"
git config --global user.email your.email@example.com

```
### Code reviews
All submissions, including submissions by project members, need to be reviewed before being merged.

### Coding Guidelines
In the interest of supporting Red Hat's Conscious Language Initiative, we are discouraging the use of the  
following terms in the code, messages, or comments. Note the alternatives we will accept:

 - Whitelist -> allowlist
 - Blacklist -> blocklist
 - Master/Slave terminology suggestions:
   * primary / secondary
   * source / replica
   * initiator, requester / responder
   * controller, host / device, worker, proxy
   * director / performer

### Continuous Integration
TODO

### Testing and documentation
TODO

## Setup
If you have not done so on this machine, you need to:

-   Install Git and configure your GitHub access
-   Install Java SDK 8 or 11+ (OpenJDK recommended)
-   Install JSS (see [here](#jss-candlepin-crypto-extension) for details)

Docker is not strictly necessary: it is used to run the MariaDB and PostgreSQL tests which are not enabled by default.

### IDE Config and Code Style
TODO
#### Eclipse Setup
TODO
#### IDEA Setup
TODO

## Building Candlepin

### Building with Gradle
Candlepin uses gradle & gradle wrapper for building & running unit tests.  
To build Candlepin run `./gradlew war` from the root of the project.

### Custom Build Properties
A number of build flags can be passed to Gradle in order to rurn on or off  
certain features. The available flags are as follows:
* `-Pdatabase_server=(mariadb|postgres)` Specify Mariadb or postgres as the database  
  server to target. This defaults to postgres.
* `-Pdb_host="hostname"` Specify the hostname for the databse server. This  
  defaults to localhost
* `-Papp_db_name="db_name"` Specify the name of the db schema to use. This defaults  
  to `candlepin`
* `-Phostedtest=true` Enable the hosted test suite

### Internationalization
* `./gradlew gettext` runs `xgettext` to extract strings from source files.
* `./gradlew msgmerge` runs `msgmerge` to merge translation updates back into the  
  primary keys.pot file.
* `./gradlew msgfmt` runs `msgfmt` to convert the keys .po & .pot files into the  
  generated java source files for compilation into the build. This task is run as  
  a prerequisite of the compileJava task so it is run automatically every time  
  compilation is done.

### Check for Dependencies with CVEs
* `./gradlew dependencyCheckAnalyze`

The `dependencyCheckAnalyze` task will check a project using the [OWASP Dependency Check](https://www.owasp.org/index.php/OWASP_Dependency_Check) 
to see if any dependencies have CVEs reported against them.
The maximum allowable CVSS  score can be modified by setting the `max_allowed_cvss` to a float value 
between 1.0 and 10.0.  Any CVEs above the maximum allowed CVSS score will cause the build to fail. 
The reports will be generated automatically under build/reports folder.

### Lint
* `./gradlew spotlessCheck` Runs spotless to verify that the codebase meets our code style requirements.
* `./gradlew spotlessApply` Runs spotless to apply our code style.

Gradle provides spotless tasks. We are using the eclipse formatter and the code style is
defined in `eclipse-formatter.xml`.  

### Unit Tests
* `./gradlew test` runs all of the unit tests.
* `./gradlew test --tests org.candlepin.controller.Cdn*` runs only the unit  
   tests matched by the given package/class and wildcard(s).

#### Unit Test Coverage
We use JaCoCo for unit test coverage, by means of the gradle jacoco plugin.
* `./gradlew test coverage` will run the unit tests and then generate a coverage report  
  based on the unit test report. If you only run a subset of the tests, then the non-exercised  
  classes/methods/lines will look uncovered in the report.

### Spec Tests
Spec tests are written in Java which are running with the aid of an auto-generated 
OpenApi client.

To run Java spec tests:
* `./gradlew spec` runs all Java tests serially
* `./gradlew spec --tests org.candlepin.spec.StatusSpec*` runs only the spec  
   tests matched by the given package/class and wildcard(s).

[More information on Java spec tests](/spec-tests/README.md#Contributing)

### Liquibase
* `buildr "changeset:my changeset name"`  
  The `changeset` task is followed by a  
  colon and an argument.  In this case the argument is a brief description of  
  the nature of the changeset.  Be sure to quote the task name to prevent the  
  shell from interpreting the spaces.

### OpenApi / Swagger

* We use an `openapi-generator` plugin that generates our REST API along with  
  JAX-RS and swagger annotations based on the spec at `api/candlepin-api-spec.yaml`,  
  which is OpenApi Specification 3.0 standard. That yaml file is rendered by  
  Swagger UI at `https://<server_ip>:8443/candlepin/docs`, or it can be retrieved  
  in a raw format from  
  `https://<server_ip>:8443/candlepin/docs/candlepin-api-spec.yaml`.

## Building with Maven
TODO

## JSS Candlepin Crypto Extension

Our crypto functions are provided by [JSS](https://github.com/dogtagpki/jss)
which is not available in the normal Maven repositories, and is only available as an
RPM dependency. It is both a compile time and runtime dependency. You can install it using
`dnf/yum install jss` in Fedora and RHEL. If you want to install it in RHEL 8 specifically,
will need to first enable the pki-core module by running `dnf module enable pki-core`.

## Miscellaneous
* `buildr pom` creates a `pom.xml` file with the project dependencies in it
* `buildr rpmlint` runs `rpmlint` on all `*.spec` files

## Frequently Asked Questions
TODO
