# Maven lockfile investigation

## Recommendation

**Do not treat a standalone `maven-lockfile:generate` invocation as a complete lock of the current Candlepin build.**
The generated Maven application's direct/transitive dependency coordinates are captured correctly, but six
annotation-processor JARs resolved by the compiler plugin are absent without execution recording. Opt-in hermetic
recording captures those JARs, but does not cover the entire Gradle workflow or all observed parent POM metadata.
Local-checksum generation also returned success with an empty checksum for one packaged library.

This is a bounded investigation, not a Maven migration or CI integration. No application or build-source changes
were made. Failures inside the investigated tool are findings, not errors suppressed by changing Candlepin dependencies.

## Tested configuration

| Item | Value |
| --- | --- |
| Date | 2026-10-21 |
| Candlepin branch | `sbakaj/mvn-lockfile` |
| Candlepin commit | `7a84830c53342158e88f97c947dfad423fe8322e` |
| Candlepin version | `5.0.4` |
| Tool | `io.github.chains-project:maven-lockfile:5.18.6` |
| Tool source | `chains-project/maven-lockfile`, tag `v5.18.6`, commit `a52aca382ffdf77c6fc03766f097dea97067d798` |
| Environment | macOS arm64, JDK `25.0.4.1`, Maven `3.9.16`, Gradle `9.7.1` |
| Baseline settings | `checksumMode=local`, `reduced=false`; plugins, parent POMs, BOMs and extensions enabled |
| Default checksum mode probe | Separate `checksumMode=remote` attempt, capped at 180 seconds; no lockfile produced within that limit |
| Maven cache | Initially empty, isolated from the user's Maven repository; shared by the subsequent probes |

**Concurrency limitation:** full-reactor baseline generation started first. The regular WAR compilation and then
the hermetic clean-compilation/generation probe ran while that baseline process was still active, using the same
isolated cache. The remote-checksum probe ran afterward. Timings are not isolated performance measurements, and
the local-checksum gap was not reproduced in an isolated serial run. Cache/concurrency effects on checksum
behavior and repeatability have not been ruled out. The recorded coordinate sets and independently calculated
JAR hashes remain observations of these runs, not a claim of identical results for every execution schedule.

The checkout uses Gradle, but already contains `buildSrc/src/main/groovy/PomGenerator.groovy`. Its `generatePom`
task produces a parent POM and `maven/war` / `maven/api` modules. The existing generator was used unchanged,
with its `outputDir` extension pointing outside the checkout. This was not a hand-written replacement POM.

The generator intentionally excludes Gradle test configurations and the `client` / `spec-tests` projects, sets
`maven.test.skip=true`, and does not translate the Gradle plugin/build-tool classpaths. These are limitations of
the input presented to the Maven tool, not evidence that Maven project test dependencies are inherently unsupported.

## How dependency collection works

Release source was inspected, rather than relying only on the README's completeness claim:

- `GenerateLockFileMojo` calls `LockFileFacade.generateLockFileFromProject` once per Maven module.
- The application graph comes from Maven's `DependencyCollectorBuilder`; recursive nodes record coordinates,
  scope, conflict selection, repository URL and checksum. `reduced=false` retains conflict/duplicate information;
  comparisons below deduplicate exact artifact coordinates and distinguish selected nodes.
- Plugins are enabled by default. `getAllPlugins` enumerates `MavenProject.getPluginArtifacts`, builds each plugin's
  Maven model, merges explicitly declared plugin dependencies and collects its compile/runtime graph. This is
  **not** execution of each plugin, nor interpretation of arbitrary plugin configuration as dependencies.
- `hermetic=true` merges successful resolver events recorded by `DynamicResolutionSpy`, loaded as a Maven core
  extension. It must be loaded before the build and generation must follow the goals that perform resolution.
  Artifacts are attached to the plugin executing when resolution occurs.
- Collection/checksum failures can be logged and converted into empty results/checksums instead of failing the
  Maven command. Consequently, `BUILD SUCCESS` alone is not a completeness or integrity assertion.

Primary sources: [release README][readme], [collection and dynamic merge][facade], [recorder][spy],
[local checksum implementation][local-checksum].

## Plugin inventory and custom resolution

| Plugin | Version | Candlepin configuration / assessment |
| --- | --- | --- |
| `maven-compiler-plugin` | `3.16.0` | **Confirmed custom resolution:** `annotationProcessorPaths` contains `org.hibernate.orm:hibernate-jpamodelgen:7.4.13.Final`, relocated to `hibernate-processor`. The compiler creates its own Aether dependency request. Processor transitive dependency management is off by default, so versions differ from the main application graph. |
| `openapi-generator-maven-plugin` | `7.26.0` | Uses local YAML/config/templates and built-in `jaxrs-spec`. Its configured generation ran successfully. No additional missing JAR mechanism was identified for this configuration; this is not a guarantee for remote specifications/custom generators. |
| `exec-maven-plugin` | `3.6.4` | Runs `bin/scripts/generate_translations.sh`, which invokes external `msgfmt`. The script contains no dependency downloads. The host executable/toolchain is outside a Maven artifact lock. |
| `build-helper-maven-plugin` | `3.6.2` | Configured `add-source` executions register generated source directories, not additional artifact coordinates. |
| `maven-resources-plugin` | WAR `3.5.0`, API implicit `3.4.0` | File/resource copying; no custom artifact path configured. WAR execution was observed. |
| `maven-war-plugin` / `maven-jar-plugin` | `3.5.1` | Packaging configuration; no extra artifact path identified. Maven packaging goals were not execution-traced in this spike. |
| `maven-surefire-plugin` | implicit `3.5.4` | Present in the static lockfiles, but tests are disabled by the generated parent POM. Provider resolution was therefore not exercised. Enabling Maven tests would require capturing the test phase as well. |
| clean / install / site / deploy | `3.2.0` / `3.1.4` / `3.12.1` / `3.1.4` | Inherited lifecycle plugins appear in the lockfiles. No Candlepin-specific custom resolution is configured; inclusion does not mean every goal was executed. |

The compiler's [release implementation][compiler] explicitly calls `repositorySystem.resolveDependencies` in
`resolveProcessorPathEntries`; `annotationProcessorPathsUseDepMgmt` defaults to `false`.

## Experimental results

### Application coordinates and packaged libraries

| Reference | Expected unique artifacts | In corresponding static lockfile | Missing direct/transitive coordinates |
| --- | ---: | ---: | ---: |
| Parent Maven dependency tree | 0 | 0 | 0 |
| WAR Maven dependency tree | 174 | 174 | 0 |
| API Maven dependency tree | 171 | 171 | 0 |

Coordinates were compared as `(groupId, artifactId, version, artifact type, classifier)`, not by count alone.
The WAR Maven tree also exactly matches the union of Gradle's main compile/runtime artifacts: **174, zero set
differences**. This includes the `jakarta` classifier of Ehcache and the forced security versions.

An actual `./gradlew war` succeeded. Its `WEB-INF/lib` contains **173 JARs**, all present by coordinate in the
WAR lockfile. Comparing the actual packaged bytes to the WAR-local checksum records verified **172 SHA-256
hashes**; Ehcache's checksum is empty, rather than a different hash. The additional compile/provided artifact
need not be packaged. The API lockfile does contain Ehcache's hash, but another module's record is not a
substitute for a complete WAR lockfile.

The baseline WAR/API lockfiles each contain 11 plugins, with respectively 334/306 distinct transitive plugin
dependency coordinates. These counts establish inclusion, not independent validation of every plugin execution.

### Confirmed missing compiler dependencies

A real generated WAR `mvn -pl maven/war compile` compiled 1,033 source files successfully with Hibernate tooling.
The core-extension recording and an independent processor-only dependency tree identify the following JARs as
absent **everywhere in all three static lockfiles**, including plugin dependency graphs:

```text
io.smallrye:jandex:3.3.2
jakarta.xml.bind:jakarta.xml.bind-api:4.0.4
org.glassfish.jaxb:jaxb-core:4.0.7
org.glassfish.jaxb:jaxb-runtime:4.0.7
org.glassfish.jaxb:txw2:4.0.7
org.jboss.logging:jboss-logging:3.6.1.Final
```

A fresh recording followed by **same-session** `clean compile ...:generate -Dhermetic=true` succeeded. All six
JARs were added under the compiler plugin; their SHA-256 values were independently verified against the actual
repository files. Eight additional POM coordinates were also added. No observed compiler JAR remains absent.

For the other executed plugins, all observed plugin-attributed JAR coordinates are present in the hermetic
lockfile as well: OpenAPI 76, build-helper 3, exec 7 and resources 4. Counts include ordinary resolver activity,
not only newly discovered dynamic dependencies, and do not certify unexecuted goals.

As an additional check, all **283 distinct successful JAR coordinates** observed in the recorded WAR compile
session were compared against the WAR lockfile. Static generation omitted 6; the hermetic lockfile omitted **0**.
This includes 179 distinct JAR coordinates whose resolver event had no Mojo attribution; it is an observed
resolver-event comparison, not proof that every possible Maven goal or non-Maven executable was exercised.

The processor artifact itself is already an ordinary application dependency in this checkout. Checking only
that direct processor artifact would therefore miss the problem: the **processor's separately resolved transitive
versions** are the gap. Gradle's separate annotation-processor graph has four missing coordinates in the static
lockfiles; Maven's processor resolution has six, because its mediation differs from Gradle's.

### Successful generation with incomplete integrity data

Both non-hermetic and hermetic local-checksum WAR generation reported success but recorded:

```text
org.ehcache:ehcache:3.12.0 (classifier jakarta)
checksum: ""
```

The log contains `Could not resolve artifact: ehcache` and `Artifact has no file`. Its stack trace enters
`FileSystemChecksumCalculator.resolveDependency`, which re-resolves the artifact's dependency graph and follows
a legacy JAXB chain (`jaxb-runtime:2.3.0-b170127.1453`), rather than just hashing the already usable managed
application artifact. Old JAXB POMs also report invalid `${tools.jar}` system paths. Both normal Maven compilation
and the primary Gradle WAR build succeed with the application graph.

This is an observed **local checksum mode** reliability issue. It must not be generalized to every checksum mode
or to every run; the API module's record has a nonempty checksum. Generation warnings and empty fields must be
treated as verification failures by an adopter, irrespective of the command exit code.

A separate non-hermetic WAR generation using the tool's default `checksumMode=remote` was stopped at the
predefined **180-second** limit (exit `124`). Its log shows repeated remote SHA-256 misses and fallback to
downloading/calculating checksums, while still processing plugin graphs. It had not written a lockfile.
**Whether remote mode avoids the Ehcache checksum gap is therefore unverified**, not a demonstrated failure
of that mode. The timebox was not extended to debug or optimize upstream networking/checksum collection.

### Missing Gradle workflow inputs

The table compares exported Gradle artifact sets with **all** static application/plugin lockfile entries, so an
artifact incidentally present in a Maven plugin graph is counted as present. Rows overlap and must not be summed.

| Gradle configuration | Resolved artifacts | Absent from all static lockfiles |
| --- | ---: | ---: |
| Root `runtimeClasspath` | 174 | 0 |
| Root `testRuntimeClasspath` | 193 | 19 |
| Root `annotationProcessor` | 19 | 4 |
| `client:runtimeClasspath` | 15 | 9 |
| `spec-tests:testRuntimeClasspath` | 45 | 20, including the local `client` project JAR |
| Root `buildscriptClasspath` | 148 | 75 |
| Root `checkstyle` | 21 | 10 |
| Root `jacocoAgent` / `jacocoAnt` | 1 / 7 | 1 / 7 |
| `buildSrc:runtimeClasspath` | 75 | 6 |

Examples include JUnit `6.1.3`, Mockito `5.24.0`, `org.hsqldb:hsqldb:2.7.4`,
`com.mattbertolini:liquibase-slf4j:5.1.0`, Gson `2.14.0`, OkHttp `5.5.0`, and Gson Fire `1.9.0`.
These omissions come from the Gradle-to-Maven scope boundary. Capturing the generated Maven WAR's execution
cannot discover Gradle-only configurations or tests that the generated POM disables.

## Remaining limitations and confidence boundary

1. **Hermetic JAR capture is not complete POM locking.** The recorder observed, but the hermetic lockfile omitted,
   `org.eclipse.ee4j:project:2.0.0-M1` and `org.jboss:jboss-parent:42`. Local POM inspection confirms these are
   parents of captured JAXB/logging metadata. The dynamic merge limits standalone POMs to group IDs with newly
   discovered JARs; cross-group parents can be filtered out. Ordinary library descriptor POMs are also not stored
   individually by this schema; distinguish that design choice from missing parent-chain metadata.
2. **Execution coverage matters.** Only the generated WAR's compile lifecycle was traced; Maven package/verify,
   tests, alternative profiles and Linux-specific paths were not. `msgfmt` and the JDK are not Maven artifacts.
   Arbitrary downloads outside Maven's resolver are outside `RepositoryEvent` capture.
3. **Reactor ordering needs care.** Source inspection shows the recorder pauses at the first lockfile
   generate/validate goal and does not resume. A parent goal can precede child compilation. This experiment used
   `-pl maven/war` to avoid that source-level risk; it did not certify a whole-reactor hermetic recipe.
4. **Known upstream limitations remain relevant.** [#1544][issue1544] discusses partial graphs returned after
   warnings; [#1550][issue1550] / [#1561][issue1561] describe checksum gaps; [#1640][issue1640] documents the
   build-phase coverage trade-off; [#1650][issue1650] concerns release-versioned reactor checksums. These are
   upstream reports, not all reproduced here. [#1568][issue1568], the dynamic-plugin problem, is closed; current
   hermetic support must not be overlooked by repeating only the original report.
5. `freeze`, offline rebuild enforcement, `validate`, reproducibility across platforms and complete SBOM/POM
   integrity were not certified. Artifact inventory coverage is not the same as enforced dependency locking.

## Generation reproduction and retained verification evidence

**The commands below reproduce generation and Gradle reference exports, not the complete numerical verification
pipeline.** Recreating all reported comparisons additionally requires the processor-only reference POM/tree,
the original `compile-recording.json`, packaged-WAR coordinate/hash mapping, the additional Gradle/buildSrc
exports, and `compare.py`. These inputs and the analysis helper are retained in the **30-file `evidence.tar.gz`
bundle** in the directory below. They are not checked into this repository or uploaded anywhere; preserve that
bundle separately if the full verification must remain reproducible after temporary storage is cleaned up.

All scratch scripts, generated POMs, dependency trees, lockfiles, Gradle exports, packaged-JAR hashes, resolver
recordings and command logs are retained locally at:

```text
/private/var/folders/4_/f08tzx5n7hg16_b_cv5ntb8h0000gn/T/opencode/candlepin-lockfile.Z0gKbc/
```

The key analysis output is `comparison.json`, generated by the bundle's `compare.py`. With the bundle unpacked,
run `python3 compare.py /absolute/path/to/unpacked/bundle` to recompute the set/checksum comparisons from its
retained inputs. Creating those inputs from scratch is not fully automated by the generation-only recipe below.
`probe.init.gradle` configures
`pomGenerator.outputDir` from `probe.output` and registers `exportLockfileProbe` to serialize actual resolved
Gradle artifact coordinates, including type/classifier. It does not change repository build configuration.

To recreate the reference exporter without relying on temporary files, save this as `probe.init.gradle` in a
new scratch directory. Set `PROBE` to that directory; leave its generated POM/output paths outside the checkout.

```groovy
import groovy.json.JsonOutput

gradle.projectsEvaluated {
    def root = gradle.rootProject
    if (root.extensions.findByName('pomGenerator') != null) {
        root.extensions.getByName('pomGenerator').outputDir = System.getProperty('probe.output')
    }
    root.tasks.register('exportLockfileProbe') {
        doLast {
            def report = [:]
            root.allprojects.each { p ->
                def configs = [:]
                ['compileClasspath', 'runtimeClasspath', 'testCompileClasspath',
                 'testRuntimeClasspath', 'annotationProcessor', 'checkstyle',
                 'jacocoAgent', 'jacocoAnt'].each { name ->
                    def c = p.configurations.findByName(name)
                    if (c != null && c.canBeResolved) {
                        configs[name] = c.resolvedConfiguration.resolvedArtifacts.collect { a ->
                            [groupId: a.moduleVersion.id.group, artifactId: a.name,
                             version: a.moduleVersion.id.version, type: a.extension,
                             classifier: a.classifier ?: '']
                        }
                    }
                }
                def c = p.buildscript.configurations.findByName('classpath')
                if (c != null && c.canBeResolved) {
                    configs['buildscriptClasspath'] = c.resolvedConfiguration.resolvedArtifacts.collect { a ->
                        [groupId: a.moduleVersion.id.group, artifactId: a.name,
                         version: a.moduleVersion.id.version, type: a.extension,
                         classifier: a.classifier ?: '']
                    }
                }
                report[p.path] = configs
            }
            new File(System.getProperty('probe.reference')).text =
                JsonOutput.prettyPrint(JsonOutput.toJson(report))
        }
    }
}
```

From this checkout, with `PROBE` set to the evidence directory:

```bash
./gradlew --no-daemon --console=plain -I "$PROBE/probe.init.gradle" \
    -Dprobe.output="$PROBE/maven-project" \
    -Dprobe.reference="$PROBE/gradle-artifacts.json" generatePom exportLockfileProbe
./gradlew --no-daemon --console=plain war
```

For the additional tool/buildSrc references, run `exportLockfileProbe` again with a distinct `probe.reference`
file, and run it from `buildSrc` using `../gradlew`. No `generatePom` invocation is needed for those exports.

For a new scratch directory, use the retained init script and create read-only input links named `src`, `api`,
`buildSrc`, `bin`, `po`, `LICENSE` inside `maven-project`, pointing at this checkout. Do not copy over its generated
POMs with custom POM definitions. Run the following from `maven-project`, before adding the core extension:

```bash
mvn -B -ntp -Dmaven.repo.local="$PROBE/repository" \
    org.apache.maven.plugins:maven-dependency-plugin:3.8.1:tree \
    -DoutputType=json -DoutputFile=dependency-tree.json \
    io.github.chains-project:maven-lockfile:5.18.6:generate \
    -DchecksumMode=local -Dreduced=false
```

Register `io.github.chains-project:maven-lockfile:5.18.6` in the scratch root's `.mvn/extensions.xml` before the
next Maven invocation. Start with no `target/maven-lockfile/dynamic-resolutions.json` from previous experiments;
the recorder merges existing recordings, so stale data could otherwise make coverage look better than it is.

```xml
<extensions xmlns="http://maven.apache.org/EXTENSIONS/1.2.0">
    <extension>
        <groupId>io.github.chains-project</groupId>
        <artifactId>maven-lockfile</artifactId>
        <version>5.18.6</version>
    </extension>
</extensions>
```

```bash
mvn -B -ntp -Dmaven.repo.local="$PROBE/repository" -pl maven/war \
    clean compile io.github.chains-project:maven-lockfile:5.18.6:generate \
    -DchecksumMode=local -Dreduced=false -Dhermetic=true \
    -DlockfileName=lockfile-hermetic.json
```

The additional remote-mode command was the following, run with a 180-second process timeout:

```bash
mvn -B -ntp -Dmaven.repo.local="$PROBE/repository" -pl maven/war \
    io.github.chains-project:maven-lockfile:5.18.6:generate \
    -DchecksumMode=remote -Dreduced=false -DlockfileName=lockfile-remote.json
```

`maven-generation.log` records full-reactor success in 9m37s; `maven-compile.log` records successful compilation
in 1m33s; `maven-hermetic.log` records clean compilation plus capture in 2m49s; `gradle-war.log` records success
in 16s. There were existing deprecation/unchecked warnings. Unit/spec suites, deployment and checkstyle were not
run: this investigation changes documentation only, not application code, and the generated Maven build disables tests.

## Decision

For **the current complete Gradle workflow**, the answer is **no**: the existing generated Maven input omits
entire configurations/modules, and the tool does not execute or lock a Gradle build.

For **the generated Maven application's dependency inventory**, coverage is good: both direct and transitive
coordinates match independent references. For **all Maven build inputs**, plain generation is insufficient;
hermetic execution recording is necessary for this compiler configuration, and parent-metadata/checksum gaps
still prevent a blanket completeness guarantee.

A follow-up adoption task should first define the desired boundary (packaged runtime, Maven build, or whole
Gradle development workflow), require nonempty checksums, and independently compare actual build artifacts.
No upstream fixes or permanent CI changes were made as part of this spike.

[readme]: https://github.com/chains-project/maven-lockfile/tree/v5.18.6#readme
[facade]: https://github.com/chains-project/maven-lockfile/blob/a52aca382ffdf77c6fc03766f097dea97067d798/src/main/java/io/github/chains_project/maven_lockfile/LockFileFacade.java
[spy]: https://github.com/chains-project/maven-lockfile/blob/a52aca382ffdf77c6fc03766f097dea97067d798/src/main/java/io/github/chains_project/maven_lockfile/recorder/DynamicResolutionSpy.java
[local-checksum]: https://github.com/chains-project/maven-lockfile/blob/a52aca382ffdf77c6fc03766f097dea97067d798/src/main/java/io/github/chains_project/maven_lockfile/checksum/FileSystemChecksumCalculator.java
[compiler]: https://github.com/apache/maven-compiler-plugin/blob/maven-compiler-plugin-3.16.0/src/main/java/org/apache/maven/plugin/compiler/AbstractCompilerMojo.java
[issue1544]: https://github.com/chains-project/maven-lockfile/issues/1544
[issue1550]: https://github.com/chains-project/maven-lockfile/issues/1550
[issue1561]: https://github.com/chains-project/maven-lockfile/issues/1561
[issue1640]: https://github.com/chains-project/maven-lockfile/issues/1640
[issue1650]: https://github.com/chains-project/maven-lockfile/issues/1650
[issue1568]: https://github.com/chains-project/maven-lockfile/issues/1568
