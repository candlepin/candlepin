/*
 *  Copyright (c) 2009 - 2019 Red Hat, Inc.
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
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option


class Rspec extends DefaultTask {

    Rspec() {
        description = 'Run ruby based spec tests'
        group = 'Verification'
    }

    @Input
    String test = ""

    @Input
    String spec = ""

    @Input
    boolean fileOutput = true

    @Option(option = 'spec', description = 'The name of the spec file to search. Example: "core_and_ram"')
    void setSpec(final String specfile_name) {
        this.spec = specfile_name
    }

    @Option(option = 'test', description = 'Set the name of the spec test to run')
    void setTest(final String test_name) {
        this.test = test_name
    }

    @Option(option = 'no-file', description = 'Do not write output to an html file (the output is saved to a file by default)')
    void setFileOutput(final boolean out) {
        this.fileOutput = false
    }

    @TaskAction
    runRspec() {
        def rspec_args = [
                "--format", "documentation",
                "--force-color",
                "--require", "${project.rootDir}/spec/failure_formatter",
                "--format", "ModifiedRSpec::FailuresFormatter",
                "-I", "${project.getProjectDir()}/client/ruby"
        ]

        if (fileOutput) {
            rspec_args.add("--format")
            rspec_args.add("h")
            rspec_args.add("--force-color")
            rspec_args.add("--out")
            rspec_args.add("build/reports/tests/rspec/index.html")
        }

        // Use --pattern to match the name of the spec file for the spec argument
        if (spec){
            rspec_args.add("--pattern")
            rspec_args.add("**/${spec}*.rb")
        }

        // use -e to match the name of a specific test within the matched spec files
        if (test){
            rspec_args.add("-e")
            rspec_args.add(test)
        }

        project.exec {
            executable 'rspec'
            args rspec_args
            workingDir project.getProjectDir()
        }
    }
}
