# Candlepin Containers

The folder contains container definitions for building, running, and releasing Candlepin containers.

## Directory Layout

| Directory   | Purpose |
|-------------|---------|
| `common/`   | Shared config files used by various container images |
| `active-dev/`| Containerfile and Compose files for local development (PostgreSQL and MariaDB variants) — see [active-dev/README.md](active-dev/README.md) |
| `release/`  | Containerfile for production and development release images published to `quay.io/candlepin/candlepin` — see [release/README.md](release/README.md) |

