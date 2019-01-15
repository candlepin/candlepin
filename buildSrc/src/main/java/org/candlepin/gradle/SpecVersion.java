package org.candlepin.gradle;

import org.gradle.api.GradleException;
import org.gradle.api.GradleScriptException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtraPropertiesExtension;

import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpecVersion implements Plugin<Project> {

    public void apply(Project project) throws GradleScriptException {
        // Read the version from the spec file
        String versionSearch = "\\s*Version:\\s*(.*?)\\s*$";
        Pattern versionPattern = Pattern.compile(versionSearch);
        String releaseSearch = "\\s*Release:\\s*(.*?)%.*\\s*$";
        Pattern releasePattern = Pattern.compile(releaseSearch);

        String versionFromSpec = null;
        String releaseFromSpec = null;
        String specFileName = project.getRootDir() + "/server/candlepin.spec.tmpl";
        try {
            File file = new File(specFileName);
            FileReader fileReader = new FileReader(file);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                Matcher matcher = versionPattern.matcher(line);
                if (matcher.find()){
                    versionFromSpec = matcher.group(1);
                }
                matcher = releasePattern.matcher(line);
                if (matcher.find()){
                    releaseFromSpec = matcher.group(1);
                }
                if (versionFromSpec != null && releaseFromSpec != null)
                    break;
            }
            fileReader.close();

        } catch (IOException e) {
            throw new GradleException("Error reading Spec File: " + specFileName, e);
        }

        if (versionFromSpec != null) {
            project.getLogger().debug("Setting the project version to: " + versionFromSpec);
            for (Project p : project.getAllprojects()){
                p.setVersion(versionFromSpec);
            }
        } else {
            throw new GradleException("Unable to find a version in the spec file: " + specFileName);
        }
        if (releaseFromSpec != null) {
            project.getLogger().debug("Setting the project release to: " + releaseFromSpec);
            for (Project p : project.getAllprojects()){
                ExtraPropertiesExtension ep = p.getExtensions().getByType(ExtraPropertiesExtension.class);
                ep.set("release", releaseFromSpec);
            }
        } else {
            throw new GradleException("Unable to find a version in the spec file: " + specFileName);
        }
    }
}
