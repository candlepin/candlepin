require './tasks/util'
require 'tempfile'

module Gettext
  class Config
    attr_writer :xgettext_args
    def xgettext_args
      @xgettext_args || []
    end

    attr_writer :msgmerge_args
    def msgmerge_args
      @msgmerge_args || []
    end

    attr_writer :keys_destination
    def keys_destination
      @keys_destination || project.path_to(:po, "keys.pot")
    end

    attr_writer :po_directory
    def po_directory
      @po_directory || project.path_to(:po)
    end

    def extract_enabled?
      !project.compile.sources.empty?
    end

    def merge_enabled?
      File.exist?(po_directory)
    end

    protected
    def initialize(project)
      @project = project
    end

    attr_reader :project
  end


  module ProjectExtension
    include Extension
    include ::Candlepin::Util

    def gettext
      @gettext ||= Gettext::Config.new(project)
    end

    first_time do
      desc 'Run xgettext on source files'
      Project.local_task('gettext:extract')

      desc 'Run msgmerge on keys file'
      Project.local_task('gettext:merge')
    end

    after_define do |project|
      gettext = project.gettext

      if gettext.extract_enabled?
        project.recursive_task('gettext:extract') do |task|
          info("Extracting keys for #{project}")
          mkdir_p(File.dirname(gettext.keys_destination))

          java_files = []
          top_dir = Pathname.new(top_project(project).base_dir)

          project.compile.sources.each do |src|
            src = Pathname.new(src).relative_path_from(top_dir).to_s
            java_files << Dir[File.join(src, '**', '*.java')]
          end

          args = %w[-k -F -ktrc:1c,2 -ktrnc:1c,2,3 -ktr -kmarktr -ktrn:1,2 --from-code=utf-8]
          args << gettext.xgettext_args
          args << ['-o', gettext.keys_destination]

          # If the keys file is in some other project then just append to it.
          # Buildr will take care of running dependent projects after
          # their dependencies.
          #
          # This code is meant to allow us to extract keys from multiple projects
          # and put them in a pot file under the project lowest in the dependency tree.
          # E.g. common will run and create keys.pot.  Then server will run and append to
          # common's keys.pot.  If you don't have a dependency on the keys file you are
          # writing to, it is possible for xgettext to be run out of order!
          unless in_project(project, gettext.keys_destination)
            args << %w[-j]
          end

          args = args.flatten.compact.join(' ')
          begin
            # We're writing the source files out to another file to avoid
            # problems with sending too many arguments to the shell command.
            file_list = Tempfile.new('xgettext_files')
            file_list.write(java_files.join("\n"))
            file_list.close

            sh("xgettext #{args} -f #{file_list.path}")
          ensure
            file_list.unlink
          end
        end
      end

      if gettext.merge_enabled?
        project.recursive_task('gettext:merge') do |task|
          info("Merging keys for #{project}")
          po_files = Dir[project.path_to(gettext.po_directory, '*.po')]
          po_files.each do |po_file|
            args = %w[-N --backup none]
            args << gettext.msgmerge_args
            args << ['-U', po_file]
            args = args.flatten.compact.join(' ')
            sh("msgmerge #{args} #{gettext.keys_destination}")
          end
        end
      end
    end
  end
end

class Buildr::Project
  include Gettext::ProjectExtension
end
