require 'parallel_tests'

module ParallelRSpec
  class << self
    def opts_to_string(opts)
      result = ""
      opts.each do |k, v|
        prefix = (k.length == 1) ? "-" : "--"
        result << " #{prefix}#{k} #{v}"
      end
      result
    end
  end

  module ProjectExtension
    include Extension

    first_time do
      desc "Run RSpec tests in parallel"
      Project.local_task('parallel_rspec')
    end

    after_define do |project|
      CANDLEPIN_API = File.join(project.base_dir, "client/ruby")
      RSpec::Core::RakeTask.new('serial_rspec') do |task|
        task.rspec_opts = %W{-I #{CANDLEPIN_API} --tag serial --color -fd}
        # Running buildr parall_rspec all_tests=true will cause all the tests
        # to run even if a test fails during the run.
        if not ENV['all_tests'].nil?
          task.fail_on_error = false
        end
      end

      project.task('parallel_rspec' => 'serial_rspec') do |task|
        rspec_opts = { 
          "I" => CANDLEPIN_API,
          "tag" => "~serial",
          "format" => "documentation"
        }

        spec_dir = project.path_to(:spec)
        ParallelTests::CLI.new.run(["--type", "rspec", "-o", ParallelRSpec.opts_to_string(rspec_opts), spec_dir])  
      end
    end
  end
end

class Buildr::Project
  include ParallelRSpec::ProjectExtension
end
