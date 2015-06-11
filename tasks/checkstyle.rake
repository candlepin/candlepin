require 'buildr/checkstyle'
require 'rexml/document'

module AntTaskCheckstyle
  class CheckstyleTask < Rake::Task
    class << self
      def run_local(format)
        fail("Only 'html', 'xml', and 'plain' formats are supported") unless %w(plain xml html).include?(format)
        results = {}
        Project.local_projects do |local_project|
          projects = [local_project] + local_project.projects
          fail_on_error = local_project.checkstyle.fail_on_error?
          projects.each do |p|
            results[p] = p.task("checkstyle:#{format}").invoke if p.checkstyle.enabled?
          end
          results.each do |project, did_fail|
            warn("Checkstyle found errors in #{project}") if did_fail
          end
          failed = results.values.any?
          fail("Checkstyle failed") if failed && fail_on_error
        end
      end
    end

    attr_reader :project

    def dependencies
      # TODO - This is a pretty old version
      Buildr.transitive('com.puppycrawl.tools:checkstyle:jar:5.7')
    end

    def execute(*args)
      cs = project.checkstyle
      deps = (cs.extra_dependencies || []) + dependencies
      cp = Buildr.artifacts(deps).each { |a| a.invoke() if a.respond_to?(:invoke) }.map(&:to_s).join(File::PATH_SEPARATOR)

      format = self.name.split(':').last

      case format
      when "xml"
        output_file = cs.xml_output_file
      when "html"
        # The HTML report is really just the XML report run through
        # an XSLT
        output_file = cs.xml_output_file
        format = "xml"
        run_html = true
      else

        output_file = cs.plain_output_file
      end

      source_paths = cs.source_paths.flatten.compact

      properties = cs.properties || {}
      profiles = cs.eclipse_properties || []

      info("Running Checkstyle on #{project}")

      # See http://checkstyle.sourceforge.net/anttask.html
      failed = false
      Buildr.ant('checkstyle') do |ant|
        ant.taskdef(:classpath => cp, :resource => "checkstyletask.properties")
        profiles.each do |profile|
          next unless profile.enabled == "true"

          profile_properties = properties.merge(profile.properties)
          profile.properties.values.map! do |v|
            # basedir is set in the properties method of Config's superclass: Buildr::Checkstyle::Config
            v.gsub!("${basedir}", profile_properties[:basedir])
          end

          patterns = profile.patterns || Pattern.new(".*\.java", true)
          ant.checkstyle(:classpath => cp, :config => cs.configuration_file,
              :failOnViolation => false, :failureProperty => 'checkstyleFailed') do
            format_opts = { :type => format }
            format_opts[:toFile] = output_file unless output_file.nil?
            ant.formatter(format_opts)
            profile.properties.each do |k, v|
              ant.property(:key => k, :value => v)
            end
            source_paths.each do |source_path|
              ant.fileset(:dir => source_path) do
                patterns.each do |pattern|
                  ant.filename(:regex => pattern.pattern, :negate => !pattern.is_include)
                  if pattern.is_include
                    info("Checking #{File.join(source_path, pattern.pattern)}")
                  end
                end
              end
            end
          end
        end
        failed ||= true if ant.project.getProperty('checkstyleFailed')
      end

      if cs.html_enabled? && run_html
        info("Converting XML to HTML")
        mkdir_p(File.dirname(cs.html_output_file))
        if File.exist?(cs.xml_output_file)
          Buildr.ant('checkstyle') do |ant|
            ant.xslt(:in => cs.xml_output_file,
                     :out => cs.html_output_file,
                     :style => cs.style_file)
          end
          puts Buildr::Console.color("Report written to file://#{cs.html_output_file}", :green)
        end
      end

      return (cs.fail_on_error?) ? failed : false
    end

    protected

    def associate_with(project)
      @project = project
    end
  end

  class Profile < Struct.new(:name, :enabled, :properties, :patterns)
  end

  class Pattern < Struct.new(:pattern, :is_include)
  end

  class Config < Buildr::Checkstyle::Config
    attr_writer :eclipse_xml
    def eclipse_xml
      @eclipse_xml || project.path_to(".checkstyle")
    end

    attr_writer :format
    def format
      @format || "plain"
    end

    attr_writer :source_paths
    def source_paths
      # The filesets in the Eclipse checkstyle config will take care of the
      # particulars, so we just want to start at the top
      @source_paths || [project.base_dir]
    end

    attr_writer :fail_on_error
    def fail_on_error?
      (@fail_on_error.nil?) ? true : @fail_on_error
    end

    attr_writer :plain_output_file
    def plain_output_file
      # nil will write to stdout
      @plain_output_file || nil
    end

    # This overrides the super class which adds project.compile.dependencies
    # and project.test.compile.dependencies by default.  We don't need all that
    # stuff on the classpath because we aren't even compiling the project.
    def extra_dependencies
      @extra_dependencies || []
    end

    attr_writer :properties
    def properties
      unless @properties
        @properties = super
      end
      @properties
    end

    # Read properties to send to Checkstyle out of the Eclipse CS configuration file
    def eclipse_properties
      unless File.exist?(eclipse_xml)
        warn("Could not find #{eclipse_xml} for #{project}.  This is probably an error.") if enabled?
        return {}
      end

      return @eclipse_properties unless @eclipse_properties.nil?
      @eclipse_properties = []

      doc = REXML::Document.new(File.new(eclipse_xml))

      property_sets = {}
      configs = REXML::XPath.match(doc, "/fileset-config/local-check-config")
      configs.each do |c|
        props = {}
        c.get_elements('property').inject(props) do |h, el|
          # The .value() call is important.  It's what expands the XML entity we
          # use to point to the config directory.
          h[el.attribute('name').to_s] = el.attribute('value').value
          h
        end
        property_sets[c.attribute('name').to_s] = props
      end

      filesets = REXML::XPath.match(doc, "/fileset-config/fileset")
      filesets.each do |fs|
        profile = Profile.new
        profile.name = fs.attribute('name').to_s
        profile.enabled = fs.attribute('enabled').to_s
        profile.properties = property_sets[fs.attribute('check-config-name').to_s]
        patterns = []
        profile.patterns = fs.get_elements('file-match-pattern').inject(patterns) do |p, fmp|
          if fmp.attribute('include-pattern').to_s == "true"
            pattern_type = true
          else
            pattern_type = false
          end
          p << Pattern.new(fmp.attribute('match-pattern').to_s, pattern_type)
          p
        end
        @eclipse_properties << profile
      end

      return @eclipse_properties
    end
  end

  module ProjectExtension
    include Extension

    def checkstyle
      @checkstyle ||= AntTaskCheckstyle::Config.new(project)
    end

    first_time do
      task('checkstyle:xml').clear
      task('checkstyle:html').clear

      desc "Run Checkstyle"
      Project.local_task('checkstyle') do
        CheckstyleTask.run_local("plain")
      end

      desc "Create Checkstyle XML report"
      Project.local_task('checkstyle:xml') do
        CheckstyleTask.run_local("xml")
      end

      desc "Create Checkstyle HTML report"
      Project.local_task('checkstyle:html') do
        CheckstyleTask.run_local("html")
      end
    end

    before_define do |project|
      task('checkstyle:clear') do |task|
        reports_dir = project.path_to(:reports, :checkstyle)
        rm_rf(reports_dir)
        mkdir_p(reports_dir)
      end

      checkstyle_task = CheckstyleTask.define_task('checkstyle:plain' => 'checkstyle:clear')
      checkstyle_task.send(:associate_with, project)

      checkstyle_xml_task = CheckstyleTask.define_task('checkstyle:xml' => 'checkstyle:clear')
      checkstyle_xml_task.send(:associate_with, project)

      checkstyle_report_task = CheckstyleTask.define_task('checkstyle:html' => 'checkstyle:clear')
      checkstyle_report_task.send(:associate_with, project)
    end

    after_define do |project|
      cs = project.checkstyle
      ide = project.eclipse
      if cs.enabled?
        unless ide.natures.empty?
          ide.natures('net.sf.eclipsecs.core.CheckstyleNature')
          ide.builders('net.sf.eclipsecs.core.CheckstyleBuilder')
        end
      end
    end
  end
end

class Buildr::Project
  include AntTaskCheckstyle::ProjectExtension
end
