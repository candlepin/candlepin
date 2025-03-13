package org.candlepin.gradle;

import org.gradle.api.GradleException;
import org.gradle.api.GradleScriptException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtraPropertiesExtension;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
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

        // Read the spec file from (projectRoot)/candlepin.spec.tmpl
        String absPath = project.getRootProject().getProjectDir().getAbsolutePath();
        Path specFilePath = Paths.get(absPath, "candlepin.spec.tmpl");

        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(specFilePath.toFile()))) {
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
            throw new GradleException("Unable to find a version in the spec file: " + specFilePath);
        }
        if (releaseFromSpec != null) {
            project.getLogger().debug("Setting the project release to: " + releaseFromSpec);
            for (Project p : project.getAllprojects()) {
                ExtraPropertiesExtension ep = p.getExtensions().getByType(ExtraPropertiesExtension.class);
                ep.set("release", releaseFromSpec);
            }
        }
        else {
            throw new GradleException("Unable to find a version in the spec file: " + specFilePath);
        }
    }
}
