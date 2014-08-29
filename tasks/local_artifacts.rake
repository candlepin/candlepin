module LocalArtifacts
  module ProjectExtension
    include Extension

    first_time do
      desc "Download artifacts for this project"
      Project.local_task('local_artifacts')
    end

    after_define do |project|
      task('local_artifacts') do |task|
        deps = project.compile.dependencies.select { |dep| dep.is_a?(Buildr::Artifact) }
        Buildr.artifacts(deps).each(&:invoke)
      end
    end
  end
end

class Buildr::Project
  include LocalArtifacts::ProjectExtension
end
