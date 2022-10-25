# mariadb/initdb.d

These files are mounted into the mariadb container's `/docker-entrypoint-initdb.d/` directory. Every .sh and .sql file is run to initialize the database; see [the documentation](https://hub.docker.com/_/mariadb). Note that the database is restarted after these files are applied.

## 0\_encoding.sql

This sets the database encoding to utf8. Without this, we will fail to define foreign keys, among possibly other errors.

## 1\_cp4.0.18-dump.sql

This (partially) initializes the database from an old snapshot of the production DB after updating to v4.0.18. Particularly important is that this loads data which is otherwise missing from the liquibase migration scripts, to include entries in the cp\_consumer\_type table that we expect to exist in the hosted environment.

To create this file yourself:

    mysqldump --single-transaction --compact --no-data --column-statistics=0 -u candlepin-debug -p --port=6507 --host=dbproxy01.dba-001.prod.iad2.dc.redhat.com candlepin | tee candlepin-schema.sql
    mysqldump --single-transaction --compact --column-statistics=0 -u candlepin-debug -p --port=6507 --host=dbproxy01.dba-001.prod.iad2.dc.redhat.com candlepin DATABASECHANGELOG DATABASECHANGELOGLOCK QRTZ_LOCKS cp_consumer_type cp_dist_version cp_dist_version_capability | tee candlepin-data.sql
    rm 1_*-dump.sql
    cat candlepin-schema.sql candlepin-data.sql mariadb/initdb.d/1_cp$CPVERSION-dump.sql
(replace $CPVERSION with whatever the candlepin version is in prod when you grabbed it. Example: 4.2.4)


# candlepin

## entrypoint.sh

This file starts tomcat and tails multiple log files to stdout. We do this because by default, `docker-compose up` streams stdout from all containers to the console, and would miss events only logged to log files otherwise.
