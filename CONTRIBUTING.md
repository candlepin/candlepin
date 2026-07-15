# Contributing guide

**Want to contribute? Great!**  We will try to make it easy. All contributions, even the smaller ones are more than welcome.
* [Before you contribute](#before-you-contribute)
  + [AI-generated code policy](#ai-generated-code-policy)
  + [Code reviews](#code-reviews)
  + [Coding Guidelines](#coding-guidelines)
  + [Continuous Integration](#continuous-integration)
  + [Code Style](#code-style)
* [Setup](#setup)
  + [Run Active Development Container](#run-active-development-container)
* [Building Candlepin](#building-candlepin)
  + [Building with Gradle](#building-with-gradle)
* [Liquibase](#liquibase)
* [Spec-First API: OpenApi 3.0](#spec-first-api-openapi-30)
* [Versioned Candlepin Containers](#versioned-candlepin-containers)

## Before you contribute

To contribute, use GitHub Pull Requests, from your  **own**  fork.
Also, make sure you have set up your Git authorship correctly:
```
git config --global user.name "Your Full Name"
git config --global user.email your.email@example.com
```

### AI-generated code policy
- All code (including production, tests, documentation) *must* be reviewed & refined by the author for correctness,
completeness, efficiency and style before submitting.
- Contributor accountability - the author must be able to explain every change they submit, and are accountable for it, not the AI.

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

See [CLAUDE.md#git-commits](CLAUDE.md#git-commits)

Example:
```
CANDLEPIN-1234: short summary

- Updated X
- Added Y because Z
```

### Continuous Integration

We use [GitHub Actions](https://github.com/features/actions) for Continuous Integration.
The workflows are defined in `.github/workflows`.

The following checks are run on every Pull Request:

* **Unit Tests**: Runs the unit test suite (`./gradlew test`) and generates coverage reports.
* **Spec Tests**: Runs the specification tests against different database backends (PostgreSQL, MariaDB) and modes (Standalone, Hosted).
  * This involves spinning up the necessary containers (Candlepin, Database) and executing the tests.
* **Checkstyle**: Enforces code style guidelines using Checkstyle (`./gradlew checkstyle`).
* **Woke**: Detects non-inclusive language in the source code.
* **Validate Translations**: Validates translation files (`./gradlew validate_translation`).
* **Jira Check**: Validates that the Jira ticket associated with the PR has a valid Target Version.

Other workflows include:
* **Sonar Analysis**: Runs SonarQube analysis for code coverage and security on long-lived branches.
* **I18n**: Periodically runs internationalization scripts to sync translations.

You can view the status of these checks at the bottom of your Pull Request.

### Code Style

For code style, see: [CLAUDE.md#code-style](CLAUDE.md#code-style)

## Setup
If you have not done so on this machine, you need to:

- Install Git and configure your GitHub access
- Install the following packages: podman, docker-compose, java-25-openjdk-devel gettext
- Make sure java 25 is the default with 'alternatives': `sudo alternatives --config java` & `sudo alternatives --config javac`
- The `./bin/deployment/deploy-container` script is used for development (see [Active Development Container](#run-active-development-container)).

### Deprecated/Legacy setup (vagrant/ansible)
For working with older candlepin versions (before 5.0.0), you must use the legacy vagrant/ansible setup which is now
deprecated. Instructions for setting that up can be found in [candlepinproject.org](https://www.candlepinproject.org/docs/candlepin/developer_deployment.html#vagrant)

### Run Active Development Container
Assuming `podman` & `docker-compose` are installed, the following script can be
used to deploy a Candlepin and MariaDB/Postgres container. It uses the Containerfile and compose files found
in the `containers/active-dev` directory.

```bash
./bin/deployment/deploy-container -HMa          # redeploy Candlepin in Hosted mode
./bin/deployment/deploy-container -Ma           # redeploy Candlepin in Standalone mode
./bin/deployment/deploy-container -HMag         # redeploy Candlepin & Postgres in Hosted mode if the schema changed
./bin/deployment/deploy-container -Mag          # redeploy Candlepin & Postgres in Standalone mode if the schema changed
./bin/deployment/deploy-container -f            # redeploy Candlepin in Standalone mode and regenerate the certificates
./bin/deployment/deploy-container down          # tear down containers and volumes

# in case of query changes that need to be
# tested in MariaDB as well, add the -m flag:
./bin/deployment/deploy-container -HMagm        # redeploy Candlepin & Mariadb in Hosted mode if the schema changed

# run a second instance in parallel (from a different checkout directory),
# using -p to offset host ports (8443, 8000, 5432/3306):
./bin/deployment/deploy-container -HMag -p 2    # ports: 8445, 8002, 5434
```

## Building Candlepin

### Building with Gradle
Candlepin uses gradle & gradle wrapper for building & running unit tests.
To build Candlepin run `./gradlew war` from the root of the project.

#### Custom Build Properties
A number of build flags can be passed to Gradle in order to turn on or off
certain features. The available flags are as follows:
* `-Pdatabase_server=(mysql|postgres)` Specify MariaDB/MySQL or PostgreSQL as the
  database server to target. This defaults to postgres.
* `-Pdb_host="hostname"` Specify the hostname for the database server. This
  defaults to localhost.
* `-Papp_db_name="db_name"` Specify the name of the db schema to use. This defaults
  to `candlepin`.
* `-Pcpdb_username="user"` Specify the database username. This defaults to `candlepin`.
* `-Pcpdb_password="pass"` Specify the database password. This defaults to empty.
* `-Phostedtest=true` Enable the hosted test suite.
* `-Pmanifestgen=true` Enable the manifest generator extension.
* `-Pexternal_broker=true` Use an external Artemis message broker.

#### Generating Candlepin Configuration
The `generateConfig` Gradle task generates a `candlepin.conf` file from the
template at `config/candlepin/candlepin.conf.template`. This is the single
source of truth for Candlepin configuration and is used by all environments
(local deploy, GitHub Actions spec tests, and dev containers).

```bash
./gradlew generateConfig -Pdb_host=localhost -Pmanifestgen=true -Phostedtest=true -Pcpdb_password=candlepin
```

The generated file is written to `build/candlepin.conf`.

#### Internationalization
* `./gradlew gettext` runs `xgettext` to extract strings from source files.
* `./gradlew msgmerge` runs `msgmerge` to merge translation updates back into the
  primary keys.pot file.
* `./gradlew msgfmt` runs `msgfmt` to convert the keys .po & .pot files into the
  generated java source files for compilation into the build. This task is run as
  a prerequisite of the compileJava task so it is run automatically every time
  compilation is done.

#### Check for Dependencies with CVEs
* `./gradlew dependencyCheckAnalyze`

The `dependencyCheckAnalyze` task will check a project using the [OWASP Dependency Check](https://www.owasp.org/index.php/OWASP_Dependency_Check)
to see if any dependencies have CVEs reported against them.
The maximum allowable CVSS  score can be modified by setting the `max_allowed_cvss` to a float value
between 1.0 and 10.0.  Any CVEs above the maximum allowed CVSS score will cause the build to fail.
The reports will be generated automatically under the build/reports folder.

#### Checkstyle
* `./gradlew checkstyle` Runs checkstyle for both production, test, and spec test code.
* `./gradlew checkstyleMain` Runs checkstyle only for production code.
* `./gradlew checkstyleTest` Runs checkstyle only for test code.
* `./gradlew checkstyleSpec` Runs checkstyle only for spec test code.

#### Unit Tests
* `./gradlew test` runs all of the unit tests.
* `./gradlew test --tests org.candlepin.controller.Cdn*` runs only the unit
   tests matched by the given package/class and wildcard(s).

#### Unit Test Coverage
We use JaCoCo for unit test coverage, by means of the gradle jacoco plugin.
* `./gradlew test coverage` will run the unit tests and then generate a coverage report
  based on the unit test report. If you only run a subset of the tests, then the non-exercised
  classes/methods/lines will look uncovered in the report.

#### Spec Tests
Spec tests are written in Java which are running with the aid of an auto-generated
OpenApi client.

To run Java spec tests:
* `./gradlew spec` runs all Java tests serially
* `./gradlew spec --tests org.candlepin.spec.StatusSpec*` runs only the spec
   tests matched by the given package/class and wildcard(s).

[More information on Java spec tests](/spec-tests/README.md#Contributing)

## Liquibase

### Adding a Changeset

Adding a changeset to be run during the Liquibase database migration is a two step process.

1. Create changeset file in the `src/main/resource/db` directory.
2. Include the new changeset file in the `src/main/resources/db/changelog/changelog-update.xml` file.

#### Changeset filename format

The date and time prefix for the filename is the year, month, day, second, and millisecond that the changeset was created.
You concatenate the numeric values in that order. For example, a date time prefix for a changeset created in 2024 on August 2nd, might look like: `20240802109000`

You then append a title of your changes to the date time prefix to complete the filename.

Example changeset filename:

`20240802101300-test-change.xml`

## Spec-First API: OpenApi 3.0

* We use an `openapi-generator` plugin that generates our REST API along with
  JAX-RS and swagger annotations under `build/generated/api/` based on the spec at `api/candlepin-api-spec.yaml`,
  which is OpenApi Specification 3.0 standard. That yaml file is rendered by
  Swagger UI at `https://<server_ip>:8443/candlepin/docs`, or it can be retrieved
  in a raw format from
  `https://<server_ip>:8443/candlepin/docs/candlepin-api-spec.yaml`.

## Versioned Candlepin Containers

See [containers/README.md](containers/README.md) for full documentation on configuring/running/extending versioned/tagged
Candlepin container images (production, development).
