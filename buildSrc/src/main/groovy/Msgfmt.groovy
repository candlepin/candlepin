/*
 *  Copyright (c) 2009 - 2026 Red Hat, Inc.
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

import java.nio.file.Files
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.process.ExecOperations



class MsgfmtExtension {
    String resource = "foo"
}

abstract class MsgfmtTask extends DefaultTask {

    private DirectoryProperty po_directory = project.objects.directoryProperty()
        .convention(project.layout.projectDirectory.dir("po"))

    private DirectoryProperty output_directory = project.objects.directoryProperty()
        .convention(project.layout.buildDirectory.dir("msgfmt/generated_source"))

    @Inject
    abstract ExecOperations getExecOperations()

    @InputDirectory
    public DirectoryProperty getInputDirectory() {
        return this.po_directory
    }

    @Option(option = "input", description = "Sets the input directory containing gettext .po files; defaults to <project root>/po")
    public void setInputDirectory(String input_path) {
        File input_dir = new File(input_path)

        if (!input_dir.isDirectory() || !input_dir.canRead()) {
            throw new GradleException("directory does not exist, is not a directory, or cannot be read: ${input_path}")
        }

        this.po_directory.set(input_dir)
    }

    @OutputDirectory
    public DirectoryProperty getOutputDirectory() {
        return this.output_directory
    }

    @Option(option = "output", description = "Sets the output directory for generated i18n source files; defaults to <project root>/msgfmt/generated_source")
    public void setOutputDirectory(String output_path) {
        File output_dir = new File(output_path)

        if (output_dir.exists() && (!output_dir.isDirectory() || !output_dir.canWrite())) {
            throw new GradleException("output location cannot be written to, or is not a directory: ${output_path}")
        }

        this.output_directory.set(output_dir)
    }

    @TaskAction
    void execute() {
        File po_dir = this.getInputDirectory()
            .getAsFile()
            .get()

        File output_dir = this.getOutputDirectory()
            .getAsFile()
            .get()

        // Create a temporary directory that we can freely munge with temporary build artifacts
        File build_dir = Files.createTempDirectory("msgfmt").toFile()
        build_dir.deleteOnExit()

        // Ensure our output directory exists
        output_dir.mkdirs()
        logger.lifecycle("Writing i18n catalog source files to: ${output_dir}")

        // Impl note:
        // msgfmt does not allow us to convert more than one .po file at a time due to the need to
        // specify the locale directly for the Java command. For this reason we do the initial
        // generation in the build directory and then copy the artifact the output directory.
        def extension = project.extensions.getByName("msgfmt")
        po_dir.eachFileMatch(~/.*.po/) {
            File po_file ->
                logger.lifecycle("Processing file: ${po_file}")

                // Ensure the the build directory is empty, as msgfmt complains if any artifacts
                // a previous build already exist when building sources
                project.delete(build_dir.listFiles())

                def locale = po_file.name[0..<po_file.name.lastIndexOf('.')]
                List msgfmt_args = [
                    "--java2", "--source",
                    "--resource", extension.resource,
                    "-d", build_dir.canonicalPath,
                    "-l", locale,
                    po_file.canonicalPath
                ]

                logger.debug("Executing command: msgfmt ${msgfmt_args.join(" ")}")

                getExecOperations().exec {
                    executable "msgfmt"
                    args msgfmt_args
                }

                project.copy {
                    from "${build_dir}"
                    into "${output_dir}"
                }
        }
    }
}

class Msgfmt implements Plugin<Project> {
    void apply(Project project) {
        project.extensions.create('msgfmt', MsgfmtExtension)
        Task msgfmt_task = project.task('msgfmt', type: MsgfmtTask)

        // Add the generated sources for all the i18n files to the main java source dirs so that
        // they will be compiled during the normal compileJava step
        project.sourceSets {
            main.java.srcDir msgfmt_task.getOutputDirectory()
        }

        project.compileJava.dependsOn msgfmt_task
    }
}
