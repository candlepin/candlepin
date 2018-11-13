# Custom Build Tasks
Unless otherwise noted, these tasks are all recursive: they will run on
the project you are in and all subprojects contained within.

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

## Spec Tests
* `buildr rspec` runs RSpec tests serially
* `buildr rspec:parallel` runs RSpec tests in parallel when possible
* `buildr rspec:failures` runs the tests that failed on the last run
* `buildr rspec:my_spec_name:my_test_name` runs `my_test_name` in the
  `my_spec_name` file

The spec tests are our integration tests.  You can run them serially with
`buildr rspec`.  If you want to speed things up use `buildr rspec:parallel`.
That task will run *most* of the tests in parallel.  A few must still be run
serially to prevent errors (generally import tests are run serially).

You can run specific tests by appending items to the `rspec` task name.  For
example, `buildr rspec:vcpu,consumer` will run any spec file that begins with
"vcpu" or "consumer".  You can exclude tests with a minus sign in front of the
identifier.  E.g. `buildr rspec:-vcpu` will run all spec files that do not
begin with "vcpu".

Additionally, you can provide either line numbers or test names to the task.
For example, `buildr rspec:vcpu:62,41` will run the tests on line 62 and 41 of
the vcpu spec file.  Likewise, `buildr rspec:vcpu,consumer:consumer` will run
all tests in the vcpu and consumer specs that have the word "consumer" in the
test name.

The general syntax is

```
rspec:test_name[,test_name ...][:signifier[,signifier ...]]
```

where the signifier is either a string or an integer.

Please note that if you need to use a phrase to single out a test, you will
need to quote the task name: `buildr "rspec:vcpu:should be valid"` to prevent
the shell from interfering.  Also note that any phrase or line number you
specify will be applied to *all* tests.  So `buildr rspec:vcpu,consumer:62`
will only run tests that begin on line 62 in either the vcpu or consumer specs.
This is a limitation of RSpec itself.

When you run RSpec, failed tests are recorded in `target/rspec.failures`.  You
can then use the `rspec:failures` task to just run failed tests which will then
update the list of failures again.  Thus, you can keep running `rspec:failures`
until the list is empty.

## Liquibase
* `buildr "changeset:my changeset name"`
  Much like the `rspec` task, the `changeset` task is followed by a
  colon and an argument.  In this case the argument is a brief description of
  the nature of the changeset.  Be sure to quote the task name to prevent the
  shell from interpreting the spaces.

## ERB
* `buildr erb` renders any templates found under the `erb` directory

This plugin is discussed in detail at
<http://www.candlepinproject.org/docs/candlepin/auto_conf.html>

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
* `buildr syntastic` creates `.syntastic_class_path` for the Vim Syntastic plugin
* `buildr pom` creates a `pom.xml` file with the project dependencies in it
* `buildr rpmlint` runs `rpmlint` on all `*.spec` files

