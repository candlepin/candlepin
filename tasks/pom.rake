require 'builder'

module PomTask
  class Config
    attr_accessor :artifact

    def enabled?
      !artifact.nil?
    end

    def initialize(project)
      @project = project
    end
  end

  class PomBuilder
    attr_reader :artifact
    attr_reader :dependencies

    def initialize(artifact, dependencies)
      @artifact = artifact
      @dependencies = dependencies
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
        xml.dependencies do
          dependencies.each do |dep|
            h = dep.to_hash
            xml.dependency do
              xml.groupId(h[:group])
              xml.artifactId(h[:id])
              xml.version(h[:version])
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
          # Filter out Rake::FileTask dependencies
          deps = project.compile.dependencies.select { |dep| dep.is_a?(Buildr::Artifact) }
          xml = PomBuilder.new(pom.artifact, deps)
          spec = pom.artifact.to_hash
          destination = project.path_to(:target, "#{spec[:id]}-#{spec[:version]}.pom")
          xml.write_pom(destination)
          info("POM written to #{destination}")
        end
      end
    end
  end
end

class Buildr::Project
  include PomTask::ProjectExtension
end
