require 'parallel_tests'

module ParallelRSpec
  class << self
    def includes_to_string(include_dirs)
      include_dirs.map { |dir| "-I #{dir}" }.join(' ')
    end
  end

  class Config
    attr_writer :requires
    def requires
      @requires || Dir[project.path_to(:client, :ruby)]
    end

    attr_writer :spec_dir
    def spec_dir
      @spec_dir || project.path_to(:spec)
    end

    def enabled?
      File.exist?(spec_dir)
    end

    protected
    def initialize(project)
      @project = project
    end

    attr_reader :project
  end

  module ProjectExtension
    include Extension

    def parallel_rspec
      @parallel_rspec ||= ParallelRSpec::Config.new(project)
    end

    first_time do
      desc "Run RSpec tests in parallel"
      Project.local_task('parallel_rspec')
    end

    after_define do |project|
      config = project.parallel_rspec
      requires = [config.spec_dir] + config.requires

      if config.enabled?
        RSpec::Core::RakeTask.new('serial_rspec') do |task|
          task.rspec_opts = %W{--tag serial --color -fd}
          task.rspec_opts << ParallelRSpec.includes_to_string(requires)
          task.pattern = File.join(config.spec_dir, '*_spec.rb')
          # Running buildr parall_rspec all_tests=true will cause all the tests
          # to run even if a test fails during the run.
          if not ENV['all_tests'].nil?
            task.fail_on_error = false
          end
        end

        project.recursive_task('parallel_rspec' => 'serial_rspec') do |task|
          rspec_opts = %W{--tag ~serial --format documentation}
          rspec_opts << ParallelRSpec.includes_to_string(requires)
          rspec_opts = rspec_opts.join(' ')
          puts rspec_opts

          ParallelTests::CLI.new.run(["--type", "rspec", "-o", rspec_opts, config.spec_dir])
        end
      end
    end
  end
end

class Buildr::Project
  include ParallelRSpec::ProjectExtension
end
