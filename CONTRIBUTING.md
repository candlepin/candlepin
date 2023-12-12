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
  + [Run Production Contaienr](#run-production-container)
    - [Extend Candlepin Production Base Image](#extend-candlepin-production-base-image)
  + [Run Development Container](#run-development-container)
  + [Configure Candlepin Container](#configure-candlepin-container)
    - [Candlepin Configuration](#candlepin-configuration)
    - [Development Image Default Configurations](#development-image-default-configurations)
    - [Paths](#paths)
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

### Checkstyle
* `./gradlew checkstyle` Runs checkstyle for both production, test, and spec test code.
* `./gradlew checkstyleMain` Runs checkstyle only for production code.
* `./gradlew checkstyleTest` Runs checkstyle only for test code.
* `./gradlew checkstyleSpec` Runs checkstyle only for spec test code.

Buildr provides a Checkstyle task, but we have our own that reads from the  
Eclipse Checkstyle Plugin configuration.  The Eclipse configuration defines  
several variables that are then passed in to the `project_conf/checks.xml`  
(which is the actual Checkstyle configuration).  This practice allows us to  
have slightly different style requirements for tests versus production code.  
The Eclipse Checkstyle Plugin defaults to reading from a file named  
`.checkstyle` in the root of the Eclipse project and that file points to the  
location of `checks.xml`.  Unfortunately, `checks.xml` isn't in the Eclipse  
project root and the plugin doesn't know how to look outside of the Eclipse  
project directory except by using an absolute path.

To solve this problem, we generate the `.checkstyle` file programmatically when  
running the `buildr eclipse` task.  The template is located at  
`project_conf/.checkstyle` and uses an XML entity to represent the location of  
`checks.xml`.  When you run `buildr eclipse`, we set the value of the  
`conf_dir` entity in `project_conf/eclipse-checkstyle.xml` to the absolute  
path to `checks.xml` and drop the result into `.checkstyle` in your Eclipse  
project directory.  

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

The following is an example on how to extend the Candlepin production image using your own configurations and certificates to build a production Candlepin image that you can run.

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

The following is an example on how to run the Candlepin development container using the docker compose file.

The compose file is in the dev-container directory that will pull that development image and the most
current PostgreSQL image. The configuration settings are in that compose file as environment variables. Refer to the [default configuration](#development-image-default-configurations) section for details on the Candlepin development container's default configurations. Once configured
you can start and stop(remove) the container with

``` docker compose up/down```


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
candlepin.refresh.orphan_entity_grace_period=0
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
