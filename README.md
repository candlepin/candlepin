# Custom Buildr Tasks
Unless otherwise noted, these tasks are all recursive: they will run on
the project you are in and all subprojects contained within.

## Internationalization
* `buildr gettext:extract` runs `xgettext`
* `buildr gettext:merge` runs `msgmerge`
* `buildr msgfmt` runs `msgfmt`

The `msgfmt` task is run during compilation and it can take awhile to run on
every different locale we support.  To alleviate the slowness, the task looks
at the environment variable `nopo`.  If the variable is set to a locale or
comma separated list of locales, `msgfmt` will only run against those locales.
Setting `nopo` to anything else will prevent `msgfmt` from running at all.

If you keep forgetting to set `nopo` you can have Buildr do it for you
automatically by placing something like the following in `~/.buildr/buildr.rb`:

```ruby
#! /usr/bin/env ruby

ENV['nopo'] ||= 'de'
```

Buildr will automatically evaluate that file and set `nopo` to "de" unless
the variable is already set.

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
* `buildr checkstyle`

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
* `./gradlew rspec` runs RSpec tests serially
* `./gradlew rspec --spec <my_spec_name> -- test 'my_test_name'` 
runs `my_test_name` in the `my_spec_name` file. 

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

