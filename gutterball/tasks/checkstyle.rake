#! /usr/bin/env ruby

require 'buildr/checkstyle'
require 'rexml/document'

module AntTaskCheckstyle
  class << self
    include Buildr::Checkstyle
    def dependencies
      Buildr.transitive('com.puppycrawl.tools:checkstyle:jar:5.7')
    end

    def checkstyle(conf_file, format, output_file, source_paths, options={})
      dependencies = (options[:dependencies] || []) + AntTaskCheckstyle.dependencies
      cp = Buildr.artifacts(dependencies).each { |a| a.invoke() if a.respond_to?(:invoke) }.map(&:to_s).join(File::PATH_SEPARATOR)
      options[:properties] ||= {}
      options[:profiles] ||= []
      begin
        # See http://checkstyle.sourceforge.net/anttask.html
        Buildr.ant('checkstyle') do |ant|
          ant.taskdef(:classpath => cp, :resource => "checkstyletask.properties")
          options[:profiles].each do |profile|
            next unless profile.enabled == "true"
            puts "Checkstyle checking #{profile.name}"

            profile_properties = options[:properties].merge(profile.properties)
            profile.properties.values.map! do |v|
              v.gsub!("${basedir}", profile_properties[:basedir])
            end

            patterns = profile.patterns || Pattern.new(".*\.java", true)
            ant.checkstyle(:classpath => cp, :config => conf_file,
                :failOnViolation => false, :failureProperty => 'checkstyleFailed') do
              format_opts = { :type => format }
              format_opts[:toFile] = output_file unless output_file.nil?
              ant.formatter(format_opts)
              profile.properties.each do |k, v|
                ant.property(:key => k, :value => v)
              end
              source_paths.each do |source_path|
                ant.fileset(:dir => p) do
                  patterns.each do |pattern|
                    ant.filename(:regex => pattern.pattern, :negate => !pattern.is_include)
                  end
                end
              end
            end
          end
          fail("Checkstyle failed") if ant.project.getProperty('checkstyleFailed')
        end
      rescue => e
        warn("Checkstyle found errors")
        raise e if options[:fail_on_error]
      end
    end
  end

  class Profile < Struct.new(:name, :enabled, :properties, :patterns)
  end

  class Pattern < Struct.new(:pattern, :is_include)
  end

  class Config < Buildr::Checkstyle::Config
    def eclipse_xml
      @eclipse_xml || project.path_to(".checkstyle")
    end

    def format
      @format || "plain"
    end

    def source_paths
      # The filesets in the Eclipse checkstyle config will take care of the
      # particulars, so we just want to start at the top
      @source_paths || [project.base_dir]
    end

    def fail_on_error?
      (@fail_on_error.nil?) ? true : @fail_on_error
    end

    def plain_output_file
      # nil will write to stdout
      @plain_output_file || nil
    end

    def properties
      unless @properties
        @properties = super
      end
      @properties
    end

    def eclipse_properties
      return {} unless File.exist?(eclipse_xml)
      return @eclipse_properties unless @eclipse_properties.nil?
      @eclipse_properties = []

      doc = REXML::Document.new(File.new(eclipse_xml))

      property_sets = {}
      configs = REXML::XPath.match(doc, "/fileset-config/local-check-config")
      configs.each do |c|
        props = {}
        c.get_elements('property').inject(props) do |h, el|
          h[el.attribute('name').to_s] = el.attribute('value').to_s
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
      desc "Run Checkstyle"
      Project.local_task('checkstyle')

      desc "Create Checkstyle XML report"
      Project.local_task('checkstyle:xml')

      desc "Create Checkstyle HTML report"
      Project.local_task('checkstyle:html')
    end

    after_define do |project|
      cs = project.checkstyle
      ide = project.eclipse

      ide.natures('net.sf.eclipsecs.core.CheckstyleNature')
      ide.builders('net.sf.eclipsecs.core.CheckstyleBuilder')

      if cs.enabled?
        task('checkstyle:xml').clear
        task('checkstyle:html').clear
        task('checkstyle').enhance do
          AntTaskCheckstyle.checkstyle(cs.configuration_file,
                                       'plain',
                                       cs.plain_output_file,
                                       cs.source_paths.flatten.compact,
                                       :profiles => cs.eclipse_properties,
                                       :properties => cs.properties,
                                       :fail_on_error => cs.fail_on_error?,
                                       :dependencies => cs.extra_dependencies)
        end

        task('checkstyle:xml').enhance do
          reports_dir = project.path_to(:reports, :checkstyle)
          rm_rf(reports_dir)
          mkdir_p(reports_dir)
          AntTaskCheckstyle.checkstyle(cs.configuration_file,
                                       'xml',
                                       cs.xml_output_file,
                                       cs.source_paths.flatten.compact,
                                       :profiles => cs.eclipse_properties,
                                       :properties => cs.properties,
                                       :fail_on_error => false,
                                       :dependencies => cs.extra_dependencies)
        end
        if cs.html_enabled?
          task('checkstyle:html' => task('checkstyle:xml')) do
            puts "Converting XML to HTML"
            mkdir_p(File.dirname(cs.html_output_file))
            Buildr.ant('checkstyle') do |ant|
              ant.xslt(:in => cs.xml_output_file,
                       :out => cs.html_output_file,
                       :style => cs.style_file)
            end
          end
        end
      end
    end
  end
end

class Buildr::Project
  include AntTaskCheckstyle::ProjectExtension
end
