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
* [Candlepin Container](#candlepin-container)
  + [Run Production Container](#run-production-container)
    - [Extend Candlepin Production Base Image](#extend-candlepin-production-base-image)
  + [Run Development Container](#run-development-container)
  + [Configure Candlepin Container](#configure-candlepin-container)
    - [Candlepin Configuration](#candlepin-configuration)
    - [Development Image Default Configurations](#development-image-default-configurations)
    - [Paths](#paths)
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

#### Language
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

#### Git Commits

##### Rules for Git Commit Message Style
- Separate subject from the body with a blank line
- Use past tense verbs
- Wrap the lines at 72 characters
- Use the body to explain what changed
  - In the few cases it makes sense, please include reason the change was needed, or why it was implemented in a certain way
- Prefix the subject to include the Jira card number if it exists (CANDLEPIN-1234:)
- Prefix the subject to include the Bugzilla number if it exists (12345678:)

Example:
```
CANDLEPIN-1234: short summary

- detail 1
- detail 2
```

### Continuous Integration

We use [GitHub Actions](https://github.com/features/actions) for Continuous Integration.
The workflows are defined in `.github/workflows`.

The following checks are run on every Pull Request:

* **Unit Tests**: Runs the unit test suite (`./gradlew test`) and generates coverage reports.
* **Spec Tests**: Runs the specification tests against different database backends (PostgreSQL, MariaDB) and modes (Standalone, Hosted). This involves spinning up the necessary containers (Candlepin, Database) and executing the tests.
* **Checkstyle**: Enforces code style guidelines using Checkstyle (`./gradlew checkstyle`).
* **Woke**: Detects non-inclusive language in the source code.
* **Validate Translations**: Validates translation files (`./gradlew validate_translation`).
* **Jira Check**: Validates that the Jira ticket associated with the PR has a valid Target Version.

Other workflows include:
* **Sonar Analysis**: Runs SonarQube analysis for code coverage and security on long-lived branches.
* **I18n**: Periodically runs internationalization scripts to sync translations.

You can view the status of these checks at the bottom of your Pull Request.

### Testing and documentation
#### Javadoc

##### Formatting

###### General form

The basic formatting of Javadoc blocks is as seen in this example:

```java
/**
 * Multiple lines of Javadoc text are written here,
 * wrapped normally...
 */
public int method(String p1) { ... }
```

... or in this single-line example:

```java
/** An especially short bit of Javadoc. */
```

The basic form is always acceptable. The single-line form may be substituted when the entirety of the Javadoc block (including comment markers) can fit on a single line. Note that this only applies when there are no block tags such as `@param`.

###### Paragraphs

Javadoc paragraphs should be separated by a single line containing only an unclosed paragraph tag (`<p>`) at the same indentation of the section in which it occurs. Each subsequent paragraph in a given section should use the same level of indentation. Lines containing only a paragraph tag should not be preceded or followed by a blank line.

When additional HTML is used in a Javadoc block, logical paragraphs beginning with, or immediately following, block-level HTML elements, such as `<ul>` or `<table>`, are not preceded with a paragraph tag.

Example:

```java
/**
 * A Javadoc block with many paragraphs
 * <p>
 * Although it may violate HTML best practices, the paragraph tag
 * should not be closed in either form (<p/> or <p></p>).
 *
 * <ul>
 *   <li>lists, tables, and block-level elements or structures need
 *   not be separated with paragraph tags.</li>
 * </ul>
 *
 * The paragraph following a block-level HTML element should not be
 * explicitly separated by paragraph tags. Blank lines are optional
 * if it improves readability.
 * <p>
 * <strong>Note that</strong> paragraphs beginning with inline
 * elements should still be preceded by a paragraph tag.
 */
```

###### HTML

HTML within Javadoc should attempt to follow conventions for well-formed HTML. This is largely outside the scope of this style guide, but some common basics will be covered here. In cases where HTML best practices are at odds with the style defined by this document, those defined in this document take precedence.

Unfortunately, Javadoc's format imposes a three-character prefix (space-asterisk-space) on each line of its content, which makes aligning with the normal tab stops defined in code difficult, unwieldy, and/or inconsistent. As such, block-level indentation in Javadoc will deviate from the indentation rules established for Java code.

When nesting tags, such as when building a table or a list, each set of block-level tags should add an additional level of indentation of +2 spaces from the containing paragraph or block.

Example:

```java
/**
 * Javadoc using HTML tags should attempt to keep them well-formed
 * by following block-level indentation rules for block tags.
 * <p>
 * This is a list of things:
 * <ul>
 *   <li>item 1</li>
 *   <li>item 2</li>
 *   <li>
 *     a list item, but one with enough stuff in it to justify
 *     treating it as a <em>new</em> block.
 *   </li>
 *   <li>inline list items with enough content to require continuation
 *   are also acceptable, and do not require additional indentation</li>
 * </ul>
 */
```

###### Block tags

Any of the standard "block tags" that are used appear in the order `@deprecated`, `@param`, `@throws`, `@return`, and these four types never appear with an empty description.

Block tags with a parameter, such as `@param` and `@throws`, should define the parameter on the same line as the tag itself, followed by a line break before the description.

The description accompanying all block tags should begin on the following line, indented four spaces from the indentation of the Javadoc block (not the tag declaration). If the description does not fit on a single line, following the column length limits, it should be separated with a line break, and the continuation line should be indented at the same level as the previous line of the description.

Example:

```java
/**
 * A well-formed Javadoc description. Long lines should be wrapped
 * following normal column length limits; but continuations should
 * not have additional indentation.
 *
 * @deprecated
 *  An explanation as to why the method is deprecated
 *
 * @param myParam
 *  A description of myParam and any additional context. If
 *  the description spans multiple lines, the lines should be
 *  indented at the same level.
 *
 * @throws IllegalArgumentException
 *  when an argument is illegal
 *
 * @throws AnotherException
 *  when some other thing goes horribly wrong
 *
 * @return
 *  a description of the value returned by this method
 */
```

##### The summary fragment

Each Javadoc block begins with a brief summary fragment. This fragment is very important: it is the only part of the text that appears in certain contexts such as class and method indexes.

This is a fragment—a noun phrase or verb phrase, not a complete sentence. It does not begin with `A {@code Foo} is a...`, or `This method returns...`, nor does it form a complete imperative sentence like `Save the record.`. However, the fragment is capitalized and punctuated as if it were a complete sentence.

Tip: A common mistake is to write simple Javadoc in the form `/** @return the customer ID */`. This is incorrect, and should be changed to `/** Returns the customer ID */` or `/** {@return the customer ID} */`.

##### Where Javadoc is used

At a minimum, Javadoc is present for every visible class, member, or record component, with a few exceptions noted below. A top-level class is visible if it is public; a member is visible if it is public or protected and its containing class is visible; and a record component is visible if its containing record is visible.

Additional Javadoc content may also be present, as explained in the [Non-required Javadoc](#non-required-javadoc) section.

###### Exception: self-explanatory members

Javadoc is optional for "simple, obvious" members and record components, such as a `getFoo()` method, if there really and truly is nothing else worthwhile to say but "the foo".

It is important to note that this rule should not be used as justification for omitting documentation in cases where the method or its value contain information or require additional context that a typical reader may not have. Some examples:

A record component named `canonicalName` should be documented if a typical reader may not know what the term "canonical name" means.

A method named `getCanonicalName` should be documented if its output could deviate with changes to the object's state (e.g. the default state is null, an empty string, or some sentinel value)

###### Exception: overrides

Javadoc is not required on a method that overrides a supertype method, unless its implementation adds additional edge cases, details, or restrictions on the method's original contract.

###### Non-required Javadoc

Other classes, members, and record components have Javadoc as needed or desired.

Whenever an implementation comment would be used to define the overall purpose or behavior of a class or member, that comment is written as Javadoc instead (using `/**`).

Note that non-required Javadoc is still required to follow the formatting rules of [Formatting](#formatting) and [The summary fragment](#the-summary-fragment).

## Setup
For full developer deployment instructions, see
https://www.candlepinproject.org/docs/candlepin/developer_deployment.html#developer-deployment.

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
A number of build flags can be passed to Gradle in order to turn on or off
certain features. The available flags are as follows:
* `-Pdatabase_server=(mariadb|postgres)` Specify Mariadb or postgres as the database
  server to target. This defaults to postgres.
* `-Pdb_host="hostname"` Specify the hostname for the database server. This
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
The reports will be generated automatically under the build/reports folder.

### Checkstyle
* `./gradlew checkstyle` Runs checkstyle for both production, test, and spec test code.
* `./gradlew checkstyleMain` Runs checkstyle only for production code.
* `./gradlew checkstyleTest` Runs checkstyle only for test code.
* `./gradlew checkstyleSpec` Runs checkstyle only for spec test code.

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

#### Adding a Changeset

Adding a changeset to be run during the liquibase database migration is a two step process.

1. Create changeset file in the `src/main/resource/db` directory.
2. Include the new changeset file in the `src/main/resources/db/changelog/changelog-update.xml` file.

##### Changeset filename format

The date and time prefix for the filename is the year, month, day, second, and millisecond that the changeset was created.
You concatenate the numeric values in that order. For example, a date time prefix for a changeset created in 2024 on August 2nd, might look like: `20240802109000`

You then append a title of your changes to the date time prefix to complete the filename.

Example changeset filename:

`20240802101300-test-change.xml`

### OpenApi / Swagger

* We use an `openapi-generator` plugin that generates our REST API along with
  JAX-RS and swagger annotations based on the spec at `api/candlepin-api-spec.yaml`,
  which is OpenApi Specification 3.0 standard. That yaml file is rendered by
  Swagger UI at `https://<server_ip>:8443/candlepin/docs`, or it can be retrieved
  in a raw format from
  `https://<server_ip>:8443/candlepin/docs/candlepin-api-spec.yaml`.

## Candlepin Container

This topic guides you through configuring and running Candlepin using the official container images.

Candlepin container images come in two types:
- **Production Base Image**: quay.io/candlepin/candlepin:latest
- **Development Image**: quay.io/candlepin/candlepin:dev-latest

### Run Production Container

The Candlepin production base image is designed to be a base image for your own Candlepin image that can run in a production environment. The reason for this is that the image does not include any default configurations and it is expected that you provide configurations that are appropriate for your production environment. The following are the configurations that you will need to provide:

- Candlepin configurations
- Tomcat server.xml configurations
- Certificate and key for TLS communication and Candlepin encryption

**Note:** When adding your own certificate, you must also update the Java trust store with this certificate. This can be done by copying the certificate to the `/etc/pki/ca-trust/source/anchors` directory and running `update-ca-trust`.

#### Extend Candlepin Production Base Image

The following is an example on how to extend and run the Candlepin production image using your own configurations and certificates.

Example:
``` dockerfile
FROM quay.io/candlepin/candlepin:latest

USER root

COPY ./candlepin.conf /etc/candlepin/
COPY ./server.xml /opt/tomcat/conf
COPY ./certs /etc/candlepin/certs

# Add the certificate to the Java trust store
RUN ln -s /etc/candlepin/certs/*.crt /etc/pki/ca-trust/source/anchors --force; \
  update-ca-trust;

USER tomcat

EXPOSE 8080 8443 5432 3306

ENTRYPOINT ["/opt/tomcat/bin/catalina.sh", "run"]
```

### Run Development Container

The Candlepin development image is designed to run right after pulling the image using default Candlepin and Tomcat configurations as well as a default certificate and key for TLS communication and encryption. Since this certificate and key is packaged in a publicly available container image, the use of the Candlepin development image should **not** be used in a production environment to avoid security risks.

The following is an example on how to run the Candlepin development container using a docker compose file or a kubernetes file via podman.

Two compose files are in the dev-container directory. They will pull the latest development Candlepin image and the latest PostgreSQL image. The configuration settings are in those compose files as environment variables.
Refer to the [default configuration](#development-image-default-configurations) section for details on the Candlepin development container's default configurations.

Once configured you can start and stop(remove) the docker container with

`docker compose up/down`

Or the kubernetes container with

`podman play kube candlepin-deployment.yaml []/--down`

### Configure Candlepin Container

This topic provides information on how to configure the Candlepin container.

#### Candlepin Configuration

Candlepin has configuration values to control functionality that includes JPA data access, logging levels, OAuth, individual modules, etc. There are two ways you can set Candlepin specific configuration values.

1. Configuration file
2. Environment variables

**Configuration File**: The Candlepin configuration file (/etc/candlepin/candlepin.conf) is a list of properties and their values that is read by Candlepin.

**Environment Variables**: Candlepin uses Smallrye to read environment variables and use them for running Candlepin. This means that we adhere to [Smallrye's environment variable naming and conversion rules](https://github.com/smallrye/smallrye-config/blob/main/documentation/src/main/docs/config/environment-variables.md).

#### Paths

The following are notable paths within the Candlepin images.

| Path | Description |
| ----------- | ----------- |
| /etc/candlepin/ | Directory for Candlepin configurations |
| /etc/candlepin/certs | Default directory for certificate for TLS communication and Candlepin encryption |
| /opt/tomcat/ | Tomcat installation root directory |
| /opt/tomcat/conf | Tomcat configuration directory |
| /opt/tomcat/bin | Directory that includes Tomcat startup, shutdown, and other scripts |
| /var/logs/candlepin | Candlepin log directory |

#### Development Image Default Configurations

By default the Candlepin development image is configured to run using a Postgres database container on the host network. This section displays the default configuration that are included in the development image and the expected configuration values to be set in the Postgres container.

Default candlepin.conf file:
```
jpa.config.hibernate.dialect=org.hibernate.dialect.PostgreSQL92Dialect
jpa.config.hibernate.connection.driver_class=org.postgresql.Driver
jpa.config.hibernate.connection.url=jdbc:postgresql://localhost/candlepin
jpa.config.hibernate.connection.username=candlepin
jpa.config.hibernate.connection.password=candlepin
candlepin.auth.trusted.enable=true
candlepin.auth.oauth.enable=true
candlepin.auth.oauth.consumer.rspec.secret=rspec-oauth-secret
candlepin.db.database_manage_on_startup=Manage
candlepin.standalone=true
```

Based on the default candlepin.conf configurations, the Postgres container is expected to have the following environment variables set and the container should be available on localhost.

Expected Postgres container configurations:

| Environment variable | Value |
| ----------- | ----------- |
| POSTGRES_USER | candlepin |
| POSTGRES_PASSWORD | candlepin |
| POSTGRES_DB | candlepin |

Default Tomcat server.xml connector configuration:
```xml
<Connector
  port="8443"
  protocol="HTTP/1.1"
  scheme="https"
  secure="true"
  SSLEnabled="true"
  maxThreads="150">

  <SSLHostConfig
    certificateVerification="optional"
    protocols="+TLSv1,+TLSv1.1,+TLSv1.2"
    sslProtocol="TLS">
    <Certificate
      certificateFile="/etc/candlepin/certs/candlepin-ca.crt"
      certificateKeyFile="/etc/candlepin/certs/candlepin-ca.key"
      type="RSA" />
  </SSLHostConfig>
</Connector>
```
## Frequently Asked Questions
TODO
