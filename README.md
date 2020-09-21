# Custom Build Tasks
Unless otherwise noted, these tasks are all recursive: they will run on
the project you are in and all subprojects contained within.

## Building with Gradle
Candlepin uses gradle & gradle wrapper for building & running unit tests. 
To build Candlepin run `./gradlew war` from the root of the project. 

## Custom Build Properties
A number of build flags can be passed to Gradle in order to rurn on or off 
certain features. The available flags are as follows:
* `-Pdatabase_server=(mariadb|postgres)` Specify Mariadb or postgres as the database
server to target. This defaults to postgres. 
* `-Pdb_host="hostname"` Specify the hostname for the databse server. This 
defaults to localhost
* `-Papp_db_name="db_name"` Specify the name of the db schema to use. This defaults
to `candlepin`
* `-Plogdriver=true` Enable Logdriver support in config file generation & the 
generated war file. 
* `-Pqpid=true` Enable qpid configuration when generating a config file
* `-Phostedtest=true` Enable the hosted test suite

## Internationalization
* `./gradlew gettext` runs `xgettext` to extract strings from source files. 
* `./gradlew msgmerge` runs `msgmerge` to merge translation updates back into the 
primary keys.pot file. 
* `./gradlew msgfmt` runs `msgfmt` to convert the keys .po & .pot files into the
generated java source files for compilation into the build. This task is run as 
a prerequisite of the compileJava task so it is run automatically every time 
compilation is done. 

## Check for Dependencies with CVEs
* `buildr dependency:check`

The `dependency:check` task will check a project (and all sub-projects) using
the [OWASP Dependency
Check](https://www.owasp.org/index.php/OWASP_Dependency_Check) to see if any
dependencies have CVEs reported against them.  The maximum allowable CVSS
score can be modified by setting the `max_allowed_cvss` to a float value
between 1.0 and 10.0.  Any CVEs above the maximum allowed CVSS score will
cause the build to fail.

## Checkstyle
* `./gradlew checkstyleMain`

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
`conf_dir` entity in `project_conf/eclipse-checkstyle.xml`  to the absolute
path to `checks.xml` and drop the result into `.checkstyle` in your Eclipse
project directory.

## Unit Tests
* `./gradlew tasks` runs Unit tests

## Spec Tests
* `./gradlew rspec` runs RSpec tests serially
* `./gradlew rspec --spec my_file_name --test 'my test name'`
runs `my test name` in the `my_file_name_spec.rb` file
(note that the `_spec.rb` suffix *must* be excluded).

## Liquibase
* `buildr "changeset:my changeset name"`
  Much like the `rspec` task, the `changeset` task is followed by a
  colon and an argument.  In this case the argument is a brief description of
  the nature of the changeset.  Be sure to quote the task name to prevent the
  shell from interpreting the spaces.

## Swagger

* `buildr swagger` connects to a Candlepin deployment defined in `buildfile`
  and downloads the `swagger.json` file.  It then sends this JSON file to
  swagger-codegen to automatically generate client bindings.  The task can
  be subdivided with `buildr swagger:json` and `buildr swagger:client`.

## JSS

Our crypto functions are provided by [JSS](https://github.com/dogtagpki/jss)
which is not available in the normal Maven repositories.  I put it in a Maven
repository we control on fedorapeople.org, but here is the process I use in case
anyone needs to replicate it.  The example commands use version 4.5.0 and
Fedora 28.  Change those values as appropriate.

* Download the latest SRPM [from
  Fedora](https://src.fedoraproject.org/rpms/jss/releases).
* Build the SRPM in mock for that version of Fedora.
  ```
  mock -r fedora-28-x86_64 jss-4.5.0-1.fc28.src.rpm
  ```
* Copy the rebuilt RPM from `/var/lib/mock/fedora-28-x86_64/result` (where Mock
  drops the results of its build)
* Explode the RPM using rpm2cpio.
  ```
  rpm2cpio jss-4.5.0-1.fc28.src.rpm | cpio -idmv
  ```
* Install the JAR file from the exploded RPM into your Maven repository. Make
  sure the version and path are correct.
  ```
  mvn install:install-file -Dpackaging=jar -DgroupId=org.mozilla
  -Dversion=4.5.0 -DartifactId=jss -Dfile=/tmp/jss/usr/lib/java/jss4.jar
  ```
* Explode the SRPM into `/tmp/jss`. This will give you a source tarball.
  ```
  rpm2cpio jss-4.5.0-1.fc28.src.rpm |  cpio -idmv
  ```
* Extract the source tarball.
  ```
  tar xzvf jss-4.5.0.tar.gz
  ```
* Go into the source directory and create a source jar.
  ```
  cd jss-4.5.0 && jar cvf jss-4.5.0-sources.jar org
  ```
* Install the sources jar
  ```
  mvn install:install-file -Dpackaging=jar -DgroupId=org.mozilla -Dversion=4.5.0 -DartifactId=jss -Dfile=jss-4.5.0-sources.jar -Dclassifier=sources
  ```
* Now rsync everything under `~/.m2/repository/org/mozilla/jss` to the Maven
  repository.
  ```
  rsync -avz ~/.m2/repository/org/mozilla/jss/ fedorapeople.org:public_html/ivy/candlepin/org/mozilla/jss
  ```

## Miscellaneous
* `buildr pom` creates a `pom.xml` file with the project dependencies in it
* `buildr rpmlint` runs `rpmlint` on all `*.spec` files


## Candlepin-Spring
* Deploying application via script:
  ```
  ./server/bin/deploy
  ```
  
* Deploying with -g flag will drop the existing database and generate a new one:
  ```
  ./server/bin/deploy -g
  ```
  
* For running application with liquibase update database: 
  ```
  java -jar server/build/libs/candlepin-3.1.11.war
  ```
  
* For liquibase changes only (exiting application after updates): 
  ```
  java -jar -Dspring.profiles.active=liquibase-only server/build/libs/candlepin-3.1.11.war 
  ```
  
* To change the mode to create database, override the candlepin.create_database property to true. (See example below):
  ```
  java -jar -Dcandlepin.create_database=true server/build/libs/candlepin-3.1.11.war
  ```
  ```
  java -jar -Dspring.profiles.active=liquibase-only -Dcandlepin.create_database=true server/build/libs/candlepin-3.1.11.war 
  ```


