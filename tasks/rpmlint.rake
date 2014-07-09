module RpmLint
  class Config
    attr_writer :rpmlint_conf
    def rpmlint_conf
      @rpmlint_conf || project.path_to("rpmlint.config")
    end

    attr_writer :rpmlint_args
    def rpmlint_args
      @rpmlint_args || []
    end

    def enabled?
      !Dir[File.join(project.base_dir, '*.spec')].empty?
    end

    protected
    def initialize(project)
      @project = project
    end

    attr_reader :project
  end


  module ProjectExtension
    include Extension

    def rpmlint
      @rpmlint ||= RpmLint::Config.new(project)
    end

    first_time do
      # Define task not specific to any projet.
      desc 'Run rpmlint on spec files'
      Project.local_task('rpmlint')
    end

    before_define do |project|
      rpmlint = project.rpmlint
      if rpmlint.enabled?
        project.recursive_task('rpmlint') do |task|
          args = ["-f", rpmlint.rpmlint_conf, rpmlint.rpmlint_args].flatten.compact.join(' ')
          specs = task.prerequisites.join(' ')
          sh("rpmlint #{args} #{specs}")
        end
      end
    end

    after_define do |project|
      project.recursive_task('rpmlint' => FileList[project.path_to('*.spec')])
    end
  end
end

class Buildr::Project
  include RpmLint::ProjectExtension
end
