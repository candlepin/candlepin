require 'parallel_tests'

module ModifiedRSpec
  class << self
    def default_rspec_opts(rspec)
      opts = %W{--color --format documentation}
      opts.concat(%W{--require #{File.join(Dir.pwd, 'tasks', 'failure_formatter')}})
      opts.concat(%W{--format ModifiedRSpec::Formatters::FailuresFormatter})

      includes = rspec.includes.map { |dir| "-I #{dir}" }
      opts.concat(includes)
      opts
    end

    def rspec_task(name, rspec, &block)
      RSpec::Core::RakeTask.new(name) do |task|
        task.verbose = false
        task.rspec_opts = self.default_rspec_opts(rspec)
        # Running buildr rspec:parallel all_tests=true will cause all the tests
        # to run even if there is a failure in the serial portion of the run.
        unless ENV['all_tests'].nil?
          task.fail_on_error = false
        end
        yield task if block_given?
      end
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

    def includes
      [spec_dir] + requires
    end

    # This is not configurable because we have no way to
    # pass this information to the RSpec formatter.
    def failure_file
      project.path_to(:target, 'rspec.failures')
    end

    protected
    def initialize(project)
      @project = project
    end

    attr_reader :project
  end

  module ProjectExtension
    include Extension

    def rspec
      @rspec ||= ModifiedRSpec::Config.new(project)
    end

    first_time do
      desc "Run RSpec tests in parallel"
      Project.local_task('rspec:parallel')

      desc "*DEPRECATED* use rspec:parallel"
      Project.local_task('parallel_rspec')

      desc "Run RSpec tests that failed last run"
      Project.local_task('rspec:failures')

      desc "Run RSpec tests in serial"
      Project.local_task('rspec')
    end

    after_define do |project|
      rspec = project.rspec

      if rspec.enabled?
        universal_pattern = File.join(rspec.spec_dir, '*_spec.rb')

        task('rspec:clean_failures') do |task|
          FileUtils.rm_f(rspec.failure_file)
        end

        project.recursive_task('rspec' => 'rspec:clean_failures') do |task|
          ModifiedRSpec.rspec_task('hidden_rspec', rspec) do |rspec_task|
            rspec_task.pattern = universal_pattern
          end
          task('hidden_rspec').invoke
        end

        project.recursive_task('rspec:parallel' => 'rspec:clean_failures') do |task|
          ModifiedRSpec.rspec_task('rspec_serial_only', rspec) do |rspec_task|
            rspec_task.rspec_opts.concat(%w{--tag serial})
            rspec_task.pattern = universal_pattern
          end
          task('rspec_serial_only').invoke

          rspec_opts = ModifiedRSpec.default_rspec_opts(rspec)
          rspec_opts.concat(%w{--tag ~serial})
          ParallelTests::CLI.new.run(["--type", "rspec", "-o", rspec_opts.join(' '), rspec.spec_dir])
        end

        project.recursive_task('parallel_rspec') do |task|
          warn("'parallel_rspec' is deprecated.  Use 'rspec:parallel'")
          task('rspec:parallel').invoke
        end

        project.recursive_task('rspec:failures') do |task|
          failures = IO.readlines(rspec.failure_file)
          failures.map! do |f|
            %Q{-e "#{f.strip}"}
          end

          FileUtils.rm_f(rspec.failure_file)

          ModifiedRSpec.rspec_task('rspec_failures', rspec) do |rspec_task|
            rspec_task.rspec_opts.concat(failures)
            rspec_task.pattern = universal_pattern
          end
          task('rspec_failures').invoke
        end

        TEST_SPECIFIC_REGEX = /^rspec:(.+)/
        rule(TEST_SPECIFIC_REGEX) do |task|
          tests, signifiers = TEST_SPECIFIC_REGEX.match(task.name)[1].split(/:/, 2)
          tests = tests.split(/,/)
          tests.map!(&:strip)

          signifiers ||= ""
          signifiers = signifiers.split(/,/)
          signifiers.map!(&:strip)

          excluded_tests, included_tests = tests.partition { |t| t =~ /^-/ }

          # Remove leading '-' from test name
          excluded_tests.map! { |t| t[1..-1] }

          specs_to_run = Dir[universal_pattern]
          unless included_tests.empty?
            specs_to_run.select! do |s|
              File.basename(s).start_with?(*included_tests)
            end
          end

          specs_to_run.reject! do |s|
            File.basename(s).start_with?(*excluded_tests)
          end

          ModifiedRSpec.rspec_task('filtered_rspec', rspec) do |rspec_task|
            signifiers.each do |signifier|
              if signifier =~ /\d+/
                rspec_task.rspec_opts.concat(%W{-l #{signifier}})
              else
                rspec_task.rspec_opts.concat(%W{-e "#{signifier}"})
              end
            end
            rspec_task.rspec_opts.concat(specs_to_run)
            rspec_task.verbose = true
          end
          task('filtered_rspec').invoke
        end
      end
    end
  end
end

class Buildr::Project
  include ModifiedRSpec::ProjectExtension
end
