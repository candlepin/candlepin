#! /usr/bin/env ruby

require 'naether/maven'

module PomTask
  class Config
    attr_accessor :artifact

    def dependencies
      Buildr.transitive('com.tobedevoured.naether:core:jar:0.13.5')
    end

    def enabled?
      !artifact.nil?
    end

    def initialize(project)
      @project = project
    end
  end

  module ProjectExtension
    include Extension

    def pom
      @pom ||= PomTask::Config.new(project)
    end

    after_define do |project|
      pom = project.pom
      if pom.enabled?
        desc 'Generate a POM file'
        project.task('pom') do
          naether_deps = Buildr::artifacts(pom.dependencies)
          naether_deps.each { |a| a.install }

          # The recommended process is to call
          # Naether::Bootstrap.bootstrap_local_repo(project.repositories.local)
          # but that will perform JAR installations in the Naether code.  I
          # prefer to control all local repository manipulations from within Buildr.
          Naether::Java.internal_load_paths(naether_deps.map { |a| a.to_s })

          # Filter out Rake::FileTask dependencies
          specs = project.compile.dependencies.select { |dep| dep.is_a?(Buildr::Artifact) }
          specs.map! { |s| Buildr::Artifact.to_spec(s) }

          artifact_hash = pom.artifact.to_hash
          id = artifact_hash[:id]
          group = artifact_hash[:group]
          version = artifact_hash[:version]

          maven = Naether::Maven.create_from_notation([group, id, version].join(':'))
          specs.each do |s|
            maven.add_dependency(s)
          end

          destination = project.path_to(:target, "#{id}-#{version}.pom")
          maven.write_pom(destination)
          info("POM written to #{destination}")
        end
      end
    end
  end
end

class Buildr::Project
  include PomTask::ProjectExtension
end
