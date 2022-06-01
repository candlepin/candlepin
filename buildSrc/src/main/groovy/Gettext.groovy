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


import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

class GettextExtension {
    String keys_project_dir
}

class Gettext implements Plugin<Project> {
    void apply(Project project) {
        def extension = project.extensions.create('gettext', GettextExtension)

        //Set Default value for project directory
        extension.keys_project_dir = project.projectDir
        def gettext_task = project.task('gettext') {
            description = 'Extract strings for translation from source java files'
            group = 'Build'

            doLast {
                File source_files = new File("${project.buildDir}/gettext_file_list.tmp")
                def root_dir_length = project.getRootDir().getCanonicalPath().size()
                println("Root dir length: ${root_dir_length}")
                def source_file_list = []
                project.sourceSets.main.java.findAll().each { File file ->
                    file.toString().substring(root_dir_length + 1)
                    source_file_list.add(file.toString().substring(root_dir_length + 1))
                }
                source_files.write source_file_list.join("\n")

                // Find the keys file, if it already exists merge into the existing one.

                File keys_file = new File("${extension.keys_project_dir}/po/keys.pot")
                // Perform the extraction
                List gettext_args = ["-k", "-F", "-ktrc:1c,2", "-ktrnc:1c,2,3","-ktr",
                                     "-kmarktr", "-ktrn:1,2", "--from-code=utf-8", "-o", "${keys_file}"
                ]

                gettext_args.add("-f")
                gettext_args.add("${source_files}")
                println(gettext_args.join(" "))

                project.exec {
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
                    project.exec {
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
                    project.exec {
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
                    project.exec {
                        executable "msgattrib"
                        args msgattrib_args
                        workingDir project.getRootDir()
                    }
                    msgattrib_args = ['--no-obsolete', '-o', it, it]
                    project.exec {
                        executable "msgattrib"
                        args msgattrib_args
                        workingDir project.getRootDir()
                    }
                }
            }
        }
    }
}
