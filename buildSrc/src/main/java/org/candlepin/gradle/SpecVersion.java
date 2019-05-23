package org.candlepin.gradle;

import org.gradle.api.GradleException;
import org.gradle.api.GradleScriptException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtraPropertiesExtension;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class to pull the version number out of a spec file template
 */
public class SpecVersion implements Plugin<Project> {
    public void apply(Project project) throws GradleScriptException {
        // Read the version from the spec file
        String versionSearch = "\\s*Version:\\s*(.*?)\\s*$";
        Pattern versionPattern = Pattern.compile(versionSearch);
        String releaseSearch = "\\s*Release:\\s*(.*?)%.*\\s*$";
        Pattern releasePattern = Pattern.compile(releaseSearch);

        String versionFromSpec = null;
        String releaseFromSpec = null;
        final Path[] specFile = new Path[1];

        PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:*.spec.tmpl");

        try {
            Files.walkFileTree(project.getProjectDir().toPath(), Collections.emptySet(), 1,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (pathMatcher.matches(file.getFileName())) {
                            specFile[0] = file;
                            return FileVisitResult.TERMINATE;
                        }
                        return FileVisitResult.CONTINUE;
                    }
                }
            );

            if (specFile[0] == null) {
                Files.walkFileTree(project.getProjectDir().toPath().resolveSibling("server"),
                    Collections.emptySet(), 1,
                    new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            if (pathMatcher.matches(file.getFileName())) {
                                specFile[0] = file;
                                return FileVisitResult.TERMINATE;
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    }
                );
            }
            if (specFile[0] == null) {
                throw new IOException("Could not file spec file in " + project.getProjectDir());
            }

            File file = specFile[0].toFile();
            FileReader fileReader = new FileReader(file);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                Matcher matcher = versionPattern.matcher(line);
                if (matcher.find()) {
                    versionFromSpec = matcher.group(1);
                }
                matcher = releasePattern.matcher(line);
                if (matcher.find()) {
                    releaseFromSpec = matcher.group(1);
                }
                if (versionFromSpec != null && releaseFromSpec != null) {
                    break;
                }
            }
            fileReader.close();
        }
        catch (IOException e) {
            throw new GradleException("Error reading spec file", e);
        }

        if (versionFromSpec != null) {
            project.getLogger().debug("Setting the project version to: " + versionFromSpec);
            for (Project p : project.getAllprojects()) {
                p.setVersion(versionFromSpec);
            }
        }
        else {
            throw new GradleException("Unable to find a version in the spec file: " + specFile[0].toString());
        }
        if (releaseFromSpec != null) {
            project.getLogger().debug("Setting the project release to: " + releaseFromSpec);
            for (Project p : project.getAllprojects()) {
                ExtraPropertiesExtension ep = p.getExtensions().getByType(ExtraPropertiesExtension.class);
                ep.set("release", releaseFromSpec);
            }
        }
        else {
            throw new GradleException("Unable to find a version in the spec file: " + specFile[0].toString());
        }
    }
}
