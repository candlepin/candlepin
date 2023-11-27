# Utility Scripts for Candlepin

## Organization Migrator - org_migrator/org_migrator.py

### Purpose

This application is for transferring organizational entity data from one Candlepin database to another. It deals with a  
single organization at a time. It is important that both databases are running the same version of the database schema  
or the import will fail.

### Usage

`Usage: org_migrator.py ORG`

An export run against the source database will result in an archive of json files which contains the entity data needed  
to fully recreate the org in the target database. The archive is then imported as is to the target database. This  
application is paired with the refresh_data_extractor.sh script which will establish an in memory instance of the  
upstream data for the org.  With the upstream data and the entity data imported, a local Candlepin instance in hosted  
mode can execute an org refresh to mirror one in the portal.

### Options
| Option                                                                 | Description           |
|------------------------------------------------------------------------|-----------------------|
| --debug                                                                | Enables debug output  |
| --trace                                                                | Enables trace output; implies --debug |
| --dbtype=DBTYPE | The type of database to target. Can target MySQL, MariaDB or PostgreSQL; defaults to PostgreSQL |
| --username=USERNAME | The username to use when connecting to the database; defaults to 'candlepin |
| --password=PASSWORD | The password to use when connecting to the database; defaults to no password |
| --ask_pass | Ask for the database password as a prompt. Overwrites any password specified with the --password option |
| --host=HOST | The hostname/address of the database server; defaults to 'localhost' |
| --port=PORT | The port to use when connecting to the database server |
| --db=DB | The database to use; defaults to 'candlepin' |
| --file=FILE | The name of the file to export to or import from; defaults to 'export.zip' during import, and 'export-{org_key}-{yyyymmdd}.zip' during export |
| --import | Sets the operating mode to IMPORT; cannot be used with --export or --list |
| --export | Sets the operating mode to EXPORT; cannot be used with --import or --list |
| --list | Sets the operating mode to LIST; cannot be used with --import or --export |
| --ignore_dupes | Ignores duplicate entities during import |

### Output

`python org_migrator/org_migrator.py --import ../../export-2797911-20230302.zip
2023-03-17 15:35:13,271 INFO    org_migrator -- Using database: PostgreSQL @ localhost
2023-03-17 15:35:13,274 INFO    org_migrator -- Importing data from file: ../../export-2797911-20230302.zip
2023-03-17 15:35:13,275 INFO    org_migrator -- Beginning import task: OwnerManager
2023-03-17 15:35:13,276 INFO    org_migrator -- Import task completed: OwnerManager
2023-03-17 15:35:13,276 INFO    org_migrator -- Beginning import task: ContentManager
2023-03-17 15:35:14,300 INFO    org_migrator -- Import task completed: ContentManager
2023-03-17 15:35:14,300 INFO    org_migrator -- Beginning import task: ProductManager
2023-03-17 15:35:14,863 INFO    org_migrator -- Import task completed: ProductManager
2023-03-17 15:35:14,863 INFO    org_migrator -- Beginning import task: EnvironmentManager
2023-03-17 15:35:14,864 INFO    org_migrator -- Import task completed: EnvironmentManager
2023-03-17 15:35:14,864 INFO    org_migrator -- Beginning import task: ConsumerManager
2023-03-17 15:35:15,522 INFO    org_migrator -- Import task completed: ConsumerManager
2023-03-17 15:35:15,522 INFO    org_migrator -- Beginning import task: PoolManager
2023-03-17 15:35:15,571 INFO    org_migrator -- Import task completed: PoolManager
2023-03-17 15:35:15,571 INFO    org_migrator -- Beginning import task: UeberCertManager
2023-03-17 15:35:15,572 INFO    org_migrator -- Import task completed: UeberCertManager
2023-03-17 15:35:15,572 INFO    org_migrator -- Beginning import task: ActivationKeyManager
2023-03-17 15:35:15,572 INFO    org_migrator -- Import task completed: ActivationKeyManager
2023-03-17 15:35:15,577 INFO    org_migrator -- Import from file '../../export-2797911-20230302.zip' completed successfully
`
