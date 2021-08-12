/*
 *  Copyright (c) 2009 - ${YEAR} Red Hat, Inc.
 *
 *  This software is licensed to you under the GNU General Public License,
 *  version 2 (GPLv2). There is NO WARRANTY for this software, express or
 *  implied, including the implied warranties of MERCHANTABILITY or FITNESS
 *  FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 *  along with this software; if not, see
 *  http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 *  Red Hat trademarks are not licensed under GPLv2. No permission is
 *  granted to use or replicate Red Hat trademarks that are incorporated
 *  in this software or its documentation.
 */


import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

class MsgfmtExtension {
    String resource = "foo"
}

class MsgfmtTask extends DefaultTask {
    @InputDirectory
    File getInputDir() {
        return project.file("po")
    }

    @OutputDirectory
    File getOutputDir() {
        return project.file("${project.buildDir}/msgfmt/generated_source")
    }

    @TaskAction
    void execute() {
        def po_dir = project.file("po")
        def msgfmt_target_directory = project.file("${project.buildDir}/msgfmt/generated_source")
        def msgfmt_root_directory = new File("${project.buildDir}/msgfmt")
        if (!msgfmt_root_directory.exists()) {
            msgfmt_root_directory.mkdirs()
        }

        def msgfmt_directory = new File("${project.buildDir}/msgfmt/source-1")
        if (!msgfmt_directory.exists()) {
            msgfmt_directory.mkdirs()
        }
        if (!msgfmt_target_directory.exists()) {
            msgfmt_target_directory.mkdirs()
        }

        // msgfmt does not allow us to convert more than one PO file at a time due to the need
        // to specify the locale directly for the java command.
        // For this reason we do the initial generation in source1 & then copy over to source2
        // Loop over all the .po files and run msgfmt for them to generate compiled message bundles form the po files
        def extension = project.extensions.getByName("msgfmt")
        po_dir.eachFileMatch(~/.*.po/) {
            File po_file ->
                // Remove the org directory from generated-source since the
                // --source argument complains if it already exists
                project.delete("${project.buildDir}/msgfmt/source-1/org")

                def locale = po_file.name[0..<po_file.name.lastIndexOf('.')]
                List msgfmt_args = ["--java2", "--source",
                                    "--resource", extension.resource,
                                    "-d", "${project.buildDir}/msgfmt/source-1",
                                    "-l", locale,
                                    "${po_file.canonicalPath}"
                ]

                project.exec {
                    executable "msgfmt"
                    args msgfmt_args
                }
                project.copy {
                    from "${msgfmt_directory}"
                    into "${msgfmt_target_directory}"
                }
        }
    }
}

class Msgfmt implements Plugin<Project> {
    void apply(Project project) {
        project.extensions.create('msgfmt', MsgfmtExtension)
        // Add the generated sources for all the i18n files to the main java source dirs so that they will be compiled
        // during the normal compileJava step
        def msgfmt_target_directory = project.file("${project.buildDir}/msgfmt/generated_source")
        project.sourceSets {
            main.java.srcDir msgfmt_target_directory
        }
        def msgfmt_task = project.tasks.register("msgfmt", MsgfmtTask)
        project.compileJava.dependsOn msgfmt_task
    }
}