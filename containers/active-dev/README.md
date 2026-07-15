# Containers For Active Development

The following directory contains a Containerfile and compose definitions used to run Candlepin for development.
Separate compose files are used for deploying with MariaDB or PostgreSQL.

| File   | Description |
|-------------|---------|
| cs10.Containerfile | Containerfile for building a Candlepin image using a CentOS Stream 10 base image. This containerfile uses the `build/candlepin.conf` configurations for Candlepin. |
| mariadb.docker-compose/yml | Compose file for running Candlepin with MariaDB |
| postgres.docker-compose/yml | Compose file for running Candlepin with PostgreSQL |

