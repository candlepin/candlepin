require 'builder'

module PomTask
  class Config
    def enabled?
      !artifacts.nil? && !artifacts.empty?
    end

    def initialize(project)
      @project = project
    end

    attr_writer :artifacts
    def artifacts
      @artifacts ||= []
    end

    attr_writer :create_assembly
    def create_assembly
      @create_assembly ||= true
    end

    # A list of procs that will be executed in the plugin configuration
    # section of the POM.  The proc receives the XML Builder object and
    # the Buildr Project object. Note that the XML Builder object
    # will already be within a plugin element.  Example:
    #
    # p = Proc.new do |xml, project|
    #   xml.groupId("org.apache.maven.plugins")
    #   xml.artifactId("maven-gpg-plugin")
    #   xml.executions do
    #     xml.execution do
    #       xml.id("sign-artifacts")
    #       [...]
    #     end
    #   end
    # end
    #
    # plugin_procs << p
    def plugin_procs=(val)
      if val.respond_to?(:each)
        @plugin_procs = val
      else
        @plugin_procs = [val]
      end
    end

    def plugin_procs
      @plugin_procs ||= []
    end
  end

  class PomBuilder
    attr_reader :artifact
    attr_reader :dependencies
    attr_reader :project
    attr_reader :config

    def initialize(artifact, project, config)
      @artifact = artifact
      @project = project
      @config = config
      # Filter out Rake::FileTask dependencies (note the use of instance_of
      # because we need to match exact instances of FileTask and not
      # child classes since other Buildr tasks inherit from FileTask)
      @dependencies = project.compile.dependencies.reject do |dep|
        dep.instance_of?(Rake::FileTask)
      end
      @buffer = ""
      build
    end

    def build
      artifact_spec = artifact.to_hash
      xml = Builder::XmlMarkup.new(:target => @buffer, :indent => 2)
      xml.instruct!
      xml.project(
        "xsi:schemaLocation" => "http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd",
        "xmlns" => "http://maven.apache.org/POM/4.0.0",
        "xmlns:xsi" => "http://www.w3.org/2001/XMLSchema-instance"
      ) do
        xml.modelVersion("4.0.0")
        xml.groupId(artifact_spec[:group])
        xml.artifactId(artifact_spec[:id])
        xml.version(artifact_spec[:version])
        xml.packaging(artifact_spec[:type].to_s)

        version_properties = {}

        # Manage version numbers in a properties section
        xml.properties do
          dependencies.each do |dep|
            h = dep.to_hash
            prop_name = "#{h[:group]}-#{h[:id]}.version"
            xml.tag!(prop_name, h[:version])
            version_properties[h] = "${#{prop_name}}"
          end
        end

        xml.dependencies do
          dependencies.each do |dep|
            h = dep.to_hash
            xml.dependency do
              xml.groupId(h[:group])
              xml.artifactId(h[:id])
              xml.version(version_properties[h])
            end
          end
        end

        xml.build do
          xml.plugins do
            xml.plugin do
              # See https://maven.apache.org/plugins/maven-assembly-plugin
              xml.artifactId("maven-assembly-plugin")
              xml.configuration do
                xml.descriptorRefs do
                  # This descriptor just archives the entire project.  Any
                  # customizations to the assembly process will require the
                  # definition of an assembly descriptor which is another
                  # XML file that the assembly plugin reads to see which
                  # directories to exclude.  Probably the best way would
                  # be to declare the assembly descriptor in the parent POM
                  # and have all the child projects reference it.  See
                  # https://maven.apache.org/plugins/maven-assembly-plugin/examples/sharing-descriptors.html
                  xml.descriptorRef("project")
                end
              end
              xml.executions do
                xml.execution do
                  xml.id("create-archive")
                  xml.phase("package")
                  xml.goals do
                    xml.goal("single")
                  end
                end
              end
            end

            config.plugin_procs.each do |plugin_proc|
              xml.plugin do
                plugin_proc.call(xml, project)
              end
            end
          end
        end
      end
    end

    def write_pom(destination)
      FileUtils.mkdir_p(File.dirname(destination))
      File.open(destination, "w") { |f| f.write(@buffer) }
    end
  end

  module ProjectExtension
    include Extension

    def pom
      @pom ||= PomTask::Config.new(project)
    end

    first_time do
      desc 'Generate a POM file'
      Project.local_task('pom')
    end

    after_define do |project|
      pom = project.pom
      if pom.enabled?
        project.recursive_task('pom') do
          pom.artifacts.each do |artifact|
            spec = artifact.to_hash
            destination = project.path_to("pom.xml")

            # Special case for when we want to build a POM for just candlepin-api.jar
            if pom.artifacts.length > 1 && spec[:type] != :war
              destination = project.path_to(:target, "#{spec[:id]}-#{spec[:version]}.pom")
            end

            xml = PomBuilder.new(artifact, project, pom)
            xml.write_pom(destination)
            info("POM written to #{destination}")
          end
        end
      end
    end
  end
end

class Buildr::Project
  include PomTask::ProjectExtension
end
