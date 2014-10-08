require './tasks/util'

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
      @destination || project.path_to(:po, "keys.pot")
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

          args = %w[-k -F -ktrc:1c,2 -ktrnc:1c,2,3 -ktr -kmarktr -ktrn:1,2]
          args << gettext.xgettext_args
          args << ['-o', gettext.keys_destination]
          args = args.flatten.compact.join(' ')
          sh("xgettext #{args} #{java_files.join(' ')}")
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
