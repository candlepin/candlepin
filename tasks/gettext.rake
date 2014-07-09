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

    def gettext
      @gettext ||= Gettext::Config.new(project)
    end

    first_time do
      desc 'Run xgettext on source files'
      Project.local_task('gettext:extract')

      desc 'Run msgmerge on keys file'
      Project.local_task('gettext:merge')
    end

    before_define do |project|
      gettext = project.gettext
      project.recursive_task('gettext:extract') do |task|
        mkdir_p(File.dirname(gettext.keys_destination))
        args = %w[-ktrc:1c,2 -ktrnc:1c,2,3 -ktr -kmarktr -ktrn:1,2]
        args << gettext.xgettext_args
        args << ['-o', gettext.keys_destination]
        args = args.flatten.compact.join(' ')
        sh("xgettext #{args} #{task.prerequisites.join(' ')}")
      end

      if gettext.merge_enabled?
        project.recursive_task('gettext:merge') do |task|
          task.prerequisites.each do |po_file|
            args = %w[-N --backup none]
            args << gettext.msgmerge_args
            args << ['-U', po_file]
            args = args.flatten.compact.join(' ')
            sh("msgmerge #{args} #{gettext.keys_destination}")
          end
        end
      end
    end

    after_define do |project|
      prerequisites = []
      project.compile.sources.each do |src|
        prerequisites << FileList[File.join(src, '**', '*.java')]
      end
      prerequisites.flatten!
      project.recursive_task('gettext:extract' => prerequisites)

      gettext = project.gettext
      project.recursive_task('gettext:merge' => FileList[project.path_to(gettext.po_directory, '*.po')])
    end
  end
end

class Buildr::Project
  include Gettext::ProjectExtension
end
