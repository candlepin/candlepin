# Candlepin Versioned Containers

This folder contains configurations that are primarily used for building Candlepin's versioned development and
production container images published to quay.io.

Candlepin container images come in two types:
- **Production Base Image**: quay.io/candlepin/candlepin:latest
- **Development Image**: quay.io/candlepin/candlepin:dev-latest

**Note**: These images are versioned and based on specific Candlepin tags. If you want to use containers for
active development (make changes and compile & build image from source),
see the [CONTRIBUTING.md#run-active-development-container](../CONTRIBUTING.md#run-active-development-container) section.

## Run Production Container

The Candlepin production base image is designed to be a base image for your own Candlepin image that can run
in a production environment. The reason for this is that the image does not include any default configurations
and it is expected that you provide configurations that are appropriate for your production environment.
The following are the configurations that you will need to provide:

- Candlepin configurations
- Tomcat server.xml configurations
- Certificate and key for TLS communication and Candlepin encryption

**Note:** When adding your own certificate, you must also update the Java trust store with this certificate.
This can be done by copying the certificate to the `/etc/pki/ca-trust/source/anchors` directory and running
`update-ca-trust`.

### Extend Candlepin Production Base Image

The following is an example of how to extend and run the Candlepin production image using your own
configurations and certificates.

Example:
```dockerfile
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

## Run Development Container

The Candlepin development image is designed to run right after pulling the image using default Candlepin
and Tomcat configurations as well as a default certificate and key for TLS communication and encryption.
Since this certificate and key is packaged in a publicly available container image, the use of the Candlepin
development image should **not** be used in a production environment to avoid security risks.

The compose file in the containers directory will pull the latest development Candlepin image and the latest
PostgreSQL image. The Candlepin configuration is generated from a single template using the
Gradle `generateConfig` task, ensuring consistency across all environments.

Before starting the dev container, build the WAR file and generate the configuration:

```bash
./gradlew war -Ptest_extensions=hostedtest,manifestgen
./gradlew generateConfig -Pdb_host=postgres -Phostedtest=true -Pmanifestgen=true -Pcpdb_password=candlepin
```

The generated `build/candlepin.conf` is copied into the image during the Docker build
(via `COPY build/candlepin.conf` in the Containerfile). When using `containers/release/docker-compose.yml`,
it is also mounted as a volume to allow runtime overrides without rebuilding.

Then start the docker container with:

```bash
docker compose up --build
```

and stop it with:

```bash
docker compose down
```

### Importing Test Data

The Candlepin development container does not start with initializing the database with predefined test data,
so additional steps will need to be taken after the Candlepin and database containers are running to create
test data. Currently there are two options for creating test data:

1. using the Candlepin API
2. using `test_data_importer.py`

#### Using the Candlepin API

An option of generating test data is to use the Candlepin API directly using curl or using some automated
process to send HTTP requests to the API. Information on the Candlepin API can be found in
the [Swagger API page.](https://www.candlepinproject.org/docs/candlepin/swaggerapi.html)

Below is an example on how to create an owner that has a product and content which is a common set up for
testing Candlepin. The intention is to help demonstrate how you can use the Candlepin API to setup data that
can be used for testing.

1. Create owner

In this step we create an owner with the key `test-owner` that is using `org_environment` content access mode.

```bash
curl -k -X POST https://localhost:8443/candlepin/owners \
    -u admin:admin \
    -H 'Content-Type: application/json' \
    -d '{"displayName":"test owner", "key":"test-owner", "contentAccessMode": "org_environment", "contentAccessModeList": "entitlement,org_environment"}'
```

2. Create content

Using the owner's key from the previous step, we can create content for our owner.

```bash
curl -k -X POST https://localhost:8443/candlepin/owners/test-owner/content \
    -u admin:admin \
    -H 'Content-Type: application/json' \
    -d '{"id":"5001", "type":"yum", "label":"test-label", "name":"test content", "vendor":"vendor", "contentUrl":"/test/path", "arches":"x86_64"}'
```

3. Create product

Now that we have content created for our owner, we can now create a product. Note that we are populating
the `productContent` field using our content information from the previous step. This is to create an
association between the product and the content.

```bash
curl -k -X POST https://localhost:8443/candlepin/owners/test-owner/products \
    -u admin:admin \
    -H 'Content-Type: application/json' \
    -d '{"id":"test-prod", "name":"test product", "productContent":[{"content":{"id":"5001", "type":"yum", "name":"test content", "contentUrl":"/test/path", "arches":"x86_64"}}], "href":"/products/123456"}'
```

#### Using test_data_importer.py

The `test_data_importer.py` script located in the `/bin/deployment` directory reads in predefined data from a
json file and makes HTTP requests to the Candlepin API to generate the data.

An example of how to run the script once the Candlepin development container and database container are
running:

```bash
python3 ./bin/deployment/test_data_importer.py ./bin/deployment/test_data.json
```

## Configure Candlepin Container

### Candlepin Configuration

Candlepin has configuration values to control functionality that includes JPA data access, logging levels,
OAuth, individual modules, etc. There are two ways you can set Candlepin-specific configuration values.

1. Configuration file
2. Environment variables

**Configuration File**: The Candlepin configuration file (`/etc/candlepin/candlepin.conf`) is a list of
properties and their values that is read by Candlepin.

**Environment Variables**: Candlepin uses Smallrye to read environment variables and use them for running
Candlepin. This means that we adhere to
[Smallrye's environment variable naming and conversion rules](https://github.com/smallrye/smallrye-config/blob/main/documentation/src/main/docs/config/environment-variables.md).

### Paths

The following are notable paths within the Candlepin images.

| Path | Description |
| ----------- | ----------- |
| /etc/candlepin/ | Directory for Candlepin configurations |
| /etc/candlepin/certs | Default directory for certificate for TLS communication and Candlepin encryption |
| /opt/tomcat/ | Tomcat installation root directory |
| /opt/tomcat/conf | Tomcat configuration directory |
| /opt/tomcat/bin | Directory that includes Tomcat startup, shutdown, and other scripts |
| /var/logs/candlepin | Candlepin log directory |

### Development Image Default Configurations

The Candlepin development image uses a `candlepin.conf` file generated by the Gradle `generateConfig` task
from the template at `config/candlepin/candlepin.conf.template`.

To generate the configuration for the dev container with default settings (PostgreSQL, hosted mode):

```bash
./gradlew generateConfig -Pdb_host=postgres -Phostedtest=true -Pmanifestgen=true -Pcpdb_password=candlepin
```

This produces a configuration that includes JPA/Hibernate settings, authentication, Quartz scheduler
configuration, and module settings. You can customize the output by changing the flags passed to
`generateConfig` (see [Custom Build Properties](../CONTRIBUTING.md#custom-build-properties)).

The Postgres container is expected to have the following environment variables set:

| Environment variable | Value |
| ----------- | ----------- |
| POSTGRES_USER | candlepin |
| POSTGRES_PASSWORD | candlepin |
| POSTGRES_DB | candlepin |

Default Tomcat server.xml connector configuration can be found in [server.xml](server.xml).