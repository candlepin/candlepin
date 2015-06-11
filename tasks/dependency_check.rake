require 'rexml/document'

# Plugin to run the OWASP Dependency Checker (https://www.owasp.org/index.php/OWASP_Dependency_Check)
# to check dependencies for reported security vulnerabilities.
#
# Vulnerabilities are given a score using the Common Vulnerability Scoring System (CVSS).
# The score can range from 1 to 10 with anything over 7 being considered a critical
# vulnerability.  Users can set this in their project by setting dependency_check.max_allowed_cvss
# to a float between 1.0 and 10.0 on the project object.
module DependencyCheck
  include Candlepin::Util

  class DependencyCheckReportTask < Rake::Task
    attr_reader :project

    def task_artifacts
      Buildr.transitive('org.owasp:dependency-check-ant:jar:1.2.11')
    end

    def execute(*args)
      config = project.dependency_check
      return if project.compile.dependencies.empty? || !config.enabled?

      info("Scanning #{project} for known vulnerable dependencies...")
      cp = Buildr.artifacts(task_artifacts).each { |a| a.invoke() if a.respond_to?(:invoke) }.map(&:to_s).join(File::PATH_SEPARATOR)
      Buildr.ant('check') do |ant|
        ant.taskdef(
          :name => 'dependency_check',
          :classpath => cp,
          :classname => 'org.owasp.dependencycheck.taskdefs.DependencyCheckTask')

        ant.dependency_check(
          :applicationname => project.name,
          :reportoutputdirectory => config.report_output,
          :logfile => File.join(config.report_output, 'dependency-check-report.log'),
          :reportformat => "ALL") do
          dependencies = project.compile.dependencies.select do |dep|
            dep.respond_to?(:to_spec)
          end
          local_repo = project.repositories.local
          dependencies.map! do |dep|
            file_location = Buildr.repositories.locate(dep)
            Pathname.new(file_location).relative_path_from(Pathname.new(local_repo)).to_s
          end

          # Run the dependency check task on every JAR file we use
          ant.filelist(:id => "dependency_jars", :dir => local_repo) do
            dependencies.each do |dep|
              ant.file(:name => dep)
            end
          end
        end
      end
    end

    protected

    def associate_with(project)
      @project = project
    end
  end

  class DependencyCheckTask < Rake::Task
    class << self
      def run_local
        Project.local_projects do |local_project|
          reports = {}
          projects = [local_project] + local_project.projects
          projects.each do |p|
            reports[p] = p.task('dependency:check').invoke if p.dependency_check.enabled?
          end
          print_reports(reports)

          # Do any of the result hashes have an non-empty errors list?
          failed = reports.map {|k, v| !v[:errors].empty?}.any?
          fail("Vulnerable dependencies found") if failed
        end
      end

      def print_reports(reports)
        reports.each do |proj, report|
          next if report[:warnings].empty? && report[:errors].empty?

          info("Dependency Check report for #{proj}")
          info("Maximum allowable CVSS is #{report[:max_cvss]}")

          report[:warnings].each do |w|
            warn(w)
          end

          report[:errors].each do |e|
            error(e)
          end

          info("See #{report[:link]} for more information")
          info("\n")
        end
      end
    end

    attr_reader :project

    # You can set dependency-check to fail once a certain CVSS threshold is reached, but
    # it fails by throwing an exception which Buildr does not like.  Instead, we look
    # at the XML of the report.
    def execute(*args)
      config = project.dependency_check
      report_file = File.join(config.report_output, 'dependency-check-report.xml')

      doc = REXML::Document.new(File.open(report_file))
      vulnerabilities = REXML::XPath.match(doc, "//vulnerability")

      scores = Hash.new { |h, k| h[k] = [] }
      vulnerabilities.each do |element|
        score = element.elements['cvssScore'].text.to_f
        file_location = element.parent.parent.elements['filePath'].text
        # Collect all the vulnerabilities for this dependency
        scores[file_location] << score
      end

      report = {
        :errors => [],
        :warnings => [],
        :link => "file://#{report_file.sub(/xml\z/, 'html')}",
        :max_cvss => config.max_allowed_cvss,
      }

      scores.each do |k, v|
        name = Pathname.new(k).relative_path_from(Pathname.new(project.repositories.local)).to_s
        word = (v.length == 1) ? "CVE" : "CVEs"
        msg = "#{v.length} #{word} in #{name} (Highest CVSS: #{v.max})"
        if v.max > config.max_allowed_cvss
          report[:errors] << msg
        else
          report[:warnings] << msg
        end
      end
      report
    end

    protected

    def associate_with(project)
      @project = project
    end
  end

  class Config
    def initialize(project)
      @project = project
    end

    attr_writer :enabled
    def enabled?
      @enabled ||= !@project.packages.empty?
    end

    def max_allowed_cvss=(score)
      if score.to_f > 10.0 || score.to_f < 0
        fail("CVSS score must be between 0 and 10.0")
      else
        @max_allowed_cvss = score
      end
    end

    def max_allowed_cvss
      @max_allowed_cvss ||= 6.0
    end

    def report_output
      @report_output ||= @project.path_to(:target)
    end
  end

  module ProjectExtension
    include Extension

    def dependency_check
      @dependency_check ||= DependencyCheck::Config.new(project)
    end

    first_time do
      desc "Check for open CVEs on dependencies"
      Project.local_task('dependency:check') do |name|
        DependencyCheckTask.run_local
      end
    end

    before_define do |project|
      report_task = DependencyCheckReportTask.define_task('dependency:report')
      report_task.send(:associate_with, project)

      check_task = DependencyCheckTask.define_task('dependency:check' => report_task)
      check_task.send(:associate_with, project)
    end
  end

  class Buildr::Project
    include DependencyCheck::ProjectExtension
  end
end

