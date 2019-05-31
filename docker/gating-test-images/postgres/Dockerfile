# upgrade to 9.6 requires client version 9.4.1211
# https://stackoverflow.com/questions/38427585/postgresql-error-column-am-amcanorder-doesnt-exist
FROM postgres:9.5.9

MAINTAINER Nikos Moumoulidis <nmoumoul@redhat.com>

# Copy dump.sql to the postgres init scripts dir. Any scripts under there
# are run during the start up of postgresql.
COPY dump.sql /docker-entrypoint-initdb.d/
