module MsgFmt
  class Config
    attr_writer :resource
    def resource
      @resource || "#{project.group}.i18n.Messages"
    end

    attr_writer :msgfmt_args
    def msgfmt_args
      @msgfmt_args || []
    end

    def enabled?
      File.exist?(self.po_directory)
    end

    attr_writer :po_directory
    def po_directory
      @po_directory || project.path_to(:po)
    end

    attr_writer :destination
    def destination
      @destination || project.path_to(:target, 'generated-source')
    end

    protected
    def initialize(project)
      @project = project
    end

    attr_reader :project
  end


  module ProjectExtension
    include Extension

    def msgfmt
      @msgfmt ||= MsgFmt::Config.new(project)
    end

    first_time do
      # Define task not specific to any projet.
      desc 'Run msgfmt on source files'
      Project.local_task('msgfmt')
    end

    before_define(:msgfmt) do |project|
      msgfmt = project.msgfmt
      if msgfmt.enabled?
        nopo = ENV['nopo']
        project.recursive_task('msgfmt') do |task|
          mkdir_p(msgfmt.destination)
          task.prerequisites.each do |po_file|
            locale = File.basename(po_file).chomp('.po')
            args = ["--java",
                    "--resource", msgfmt.resource,
                    msgfmt.msgfmt_args,
                    "-d", msgfmt.destination,
                    "-l", locale].flatten.compact.join(' ')
            if nopo.nil? || nopo.split(/,s*/).include?(locale)
              sh("msgfmt #{args} #{po_file}")
            end
          end
          project.compile.from(msgfmt.destination)
          project.test.compile.from(msgfmt.destination)
        end
      end
    end

    after_define(:msgfmt) do |project|
      msgfmt = project.msgfmt
      project.recursive_task('msgfmt' => FileList[project.path_to(msgfmt.po_directory, '*.po')])
      task(:compile => :msgfmt)
    end

    after_define(:compile => :msgfmt)
  end
end

class Buildr::Project
  include MsgFmt::ProjectExtension
end
