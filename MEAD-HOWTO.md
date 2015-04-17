# Intro
MEAD is a Maven-based build system for Brew.  It works by building your
project via Maven, rendering a RPM spec file from a template, and then packaging
everything as an RPM with the rendered spec file.

## Getting Started
Clone the RCM utility-scripts repository.  All the scripts you'll need are in
the `mead` directory.

## Maven Build
MEAD will not just download JAR files willy-nilly.  You must first import them
into the buildroot.  You must have the maven-import and regen-repo permissions
in Brew.  You can see your permissions with `brew list-permissions --mine`

To import just one artifact

```
$ ./get-maven-artifacts group:artifactId:packaging:version
$ ./import-maven --tag YOUR_DEPENDENCIES_TAG ARTIFACT_FILES*
$ ./brew regen-repo YOUR_BUILDROOT
```

For example:
```
$ get-maven-artifacts org.apache:apache:pom:11
$ ./import-maven --tag candlepin-0-rhel-6-deps apache-11*
$ ./brew regen-repo candlepin-0-rhel-6-build
```

Importing everything one at a time is slow, so the fastest way is to run your
build against an empty local repository and send the build log to
`mead-load-build-dependencies`.

```
$ cat > my_settings.xml <<SETTINGS
<settings>
  <localRepository>/tmp/m2_repo/</localRepository>
</settings>
SETTINGS
$ mvn -B dependency:resolve-plugins deploy -Dmaven.test.skip=true -s settings.xml -DaltDeploymentRepository=local-output::default::file:///tmp/output | tee /tmp/build.log
$ ./mead-load-build-dependencies --tag YOUR_DEPENDENCIES_TAG /tmp/build.log
```

It is important to use an empty local repository so that all dependencies will
show up in the build log.  If you use a repository that already has some of your
dependencies, `mead-load-build-dependencies` won't pick them up and you'll be
stuck importing them one at a time.

## Template Rendering
Templates are written in Cheetah.

* Dollar signs are meaningful in Cheetah.  If your spec file contains shell
  variables, you will need to surround the relevant block with `#raw` and `#end
  raw` tags.

  For example
  ```
  #raw
  for selinuxvariant in %{selinux_variants}
  do
    make NAME=$selinuxvariant -f /usr/share/selinux/devel/Makefile
    mv %{modulename}.pp %{modulename}.pp.$selinuxvariant
    make NAME=$selinuxvariant -f /usr/share/selinux/devel/Makefile clean
  done
  #end raw
  ```

* Always surround the entire `%changelog` section in `#raw` and `#end raw` tags
  so that people's changelog entries won't break the template.
