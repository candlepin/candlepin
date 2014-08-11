module MsgFmt
  class Config
    attr_writer :resource
    def resource
      short_name = project.to_s.split(':').last
      @resource || "#{project.group}.#{short_name}.i18n.Messages"
    end

    attr_writer :msgfmt_args
    def msgfmt_args
      @msgfmt_args || []
    end

    def enabled?
      File.exist?(po_directory)
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
      desc 'Run msgfmt on source files'
      Project.local_task('msgfmt')
    end

    after_define(:msgfmt) do |project|
      msgfmt = project.msgfmt
      if msgfmt.enabled?
        task(:compile => :msgfmt)

        nopo = ENV['nopo']

        project.recursive_task('msgfmt') do |task|
          mkdir_p(msgfmt.destination)

          po_files = Dir[File.join(msgfmt.po_directory, '*.po')]
          info("Running msgfmt on #{project}")

          # msgfmt has some code in it to compile with -target 1.1 and -source 1.3.
          # These compiler options cause javac to throw a warning since we are not
          # pointing to the correct rt.jar and there is nothing we can do about it.
          #
          # To use javac from JDK N to cross-compiler to an older platform version,
          # the correct practice is to:
          #     Use the older -source setting.
          #     Set the bootclasspath to compile against the rt.jar (or equivalent) for the
          #     older platform.
          #
          # If the second step is not taken, javac will dutifully use the old language rules
          # combined with new libraries, which can result in class files that do not work on
          # the older platform since references to non-existent methods can get included.
          # From http://stackoverflow.com/questions/7816423
          #
          # Luckily we can tell javac not to print a message about this.  (Msgfmt will use
          # whatever is in the JAVAC environment variable but it puts its arguments after
          # so we can't specify a correct -source and -target)
          ENV['JAVAC'] = "javac -Xlint:-options"
          po_files.each do |po_file|
            locale = File.basename(po_file).chomp('.po')
            args = ["--java2",
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
  end
end

class Buildr::Project
  include MsgFmt::ProjectExtension
end
