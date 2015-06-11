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

## Miscellaneous
* `buildr syntastic` creates `.syntastic_class_path` for the Vim Syntastic plugin
* `buildr pom` creates a `pom.xml` file with the project dependencies in it
* `buildr rpmlint` runs `rpmlint` on all `*.spec` files

