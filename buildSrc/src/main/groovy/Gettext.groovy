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

import javax.inject.Inject
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.process.ExecOperations
import groovy.ant.FileNameFinder

class GettextExtension {
    String keys_project_dir
}

class Gettext implements Plugin<Project> {

    private final ExecOperations execOperations

    @Inject
    Gettext(ExecOperations execOperations) {
        this.execOperations = execOperations
    }

    void apply(Project project) {
        def extension = project.extensions.create('gettext', GettextExtension)

        //Set Default value for project directory
        extension.keys_project_dir = project.projectDir
        def gettext_task = project.task('gettext') {
            description = 'Extract strings for translation from source java files'
            group = 'Build'

            doLast {
                // Scan for source files...
                logger.lifecycle("Scanning for source files in project root: ${project.getRootDir().getCanonicalPath()}")
                List source_file_list = []

                int root_dir_length = project.getRootDir().getCanonicalPath().size()
                project.sourceSets.main.java.findAll().each { File file ->
                    file.toString().substring(root_dir_length + 1)
                    source_file_list.add(file.toString().substring(root_dir_length + 1))
                }

                if (source_file_list.size() < 1) {
                    // Given the age of this project, we should never hit this
                    String errmsg = "No source files found in project root: ${project.getRootDir().getCanonicalPath()}"
                    throw new GradleException(errmsg)
                }

                logger.lifecycle("Found ${source_file_list.size()} source files")

                // Ensure build directory has been created
                project.getBuildDir().mkdirs()

                // Write manifest
                File manifest = new File("${project.buildDir}/gettext_manifest.txt")

                logger.lifecycle("Writing manifest file: ${manifest}")
                manifest.write(source_file_list.join("\n"))

                // Find the keys file, if it already exists merge into the existing one.
                File keys_file = new File("${extension.keys_project_dir}/po/keys.pot")

                // Perform the extraction
                List gettext_args = [
                    "-k", "-F", "-ktrc:1c,2", "-ktrnc:1c,2,3", "-ktr",
                    "-kmarktr", "-ktrn:1,2", "--from-code=utf-8", "-o", "${keys_file}",
                    "-f", manifest
                ]

                logger.lifecycle("Building keys file: ${keys_file}")
                logger.debug("Executing command: xgettext ${gettext_args.join(" ")}")

                execOperations.exec {
                    executable "xgettext"
                    args gettext_args
                    workingDir project.getRootDir()
                }
            }
        }

        def msgmerge_task = project.task('msgmerge') {
            description = 'Merge updates from each locale specific translation po file pack into the primary keys.pot.'
            group = 'Build'
            doLast {
                def po_files = new FileNameFinder().getFileNames("${extension.keys_project_dir}/po/", '*.po')
                File keys_file = new File("${extension.keys_project_dir}/po/keys.pot")
                po_files.each {
                    def msgmerge_args = ['-N','--backup', 'none', '-U', it, "${extension.keys_project_dir}/po/keys.pot"]
                    execOperations.exec {
                        executable "msgmerge"
                        args msgmerge_args
                        workingDir project.getRootDir()
                    }
                }
            }
        }

        def validate_translation = project.task('validate_translation') {
            description = 'Validate translation PO files to check for unescaped single quotes'
            group = 'verification'
            doLast {
                def po_files = new FileNameFinder().getFileNames("${extension.keys_project_dir}/po/", '*.po')
                po_files.each {
                    def msgfmt_args = ['-o', '/dev/null', '-c', it]
                    execOperations.exec {
                        executable "msgfmt"
                        args msgfmt_args
                        workingDir project.getRootDir()
                    }
                }
            }
        }

        def msgattrib_task = project.task('msgattrib') {
            description = 'Use msgattrib to remove obsolete strings (that were already removed from the source code & template file) from translation files.'
            group = 'build'
            doLast {
                def po_files = new FileNameFinder().getFileNames("${extension.keys_project_dir}/po/", '*.po')
                po_files.each {
                    def msgattrib_args = ['--set-obsolete', '--ignore-file=po/keys.pot','-o', it, it]
                    execOperations.exec {
                        executable "msgattrib"
                        args msgattrib_args
                        workingDir project.getRootDir()
                    }

                    msgattrib_args = ['--no-obsolete', '-o', it, it]
                    execOperations.exec {
                        executable "msgattrib"
                        args msgattrib_args
                        workingDir project.getRootDir()
                    }
                }
            }
        }
    }
}
