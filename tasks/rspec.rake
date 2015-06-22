require 'parallel_tests'
require 'rspec/core/rake_task'

require './tasks/util'

module ModifiedRSpec
  class CleanFailuresTask < Rake::Task
    attr_reader :project

    def execute(*args)
      super
      trace("Killing #{project.rspec.failure_file}")
      FileUtils.rm_f(project.rspec.failure_file)
    end

    protected

    def associate_with(project)
      @project = project
    end
  end

  class ModifiedRSpecTask < Rake::Task
    attr_reader :project

    class << self
      def run_local(name, *args)
        Project.local_projects do |local_project|
          local_project.task(name).invoke(*args)
        end
      end

      def include(includes)
        Project.local_projects do |local_project|
          local_project.task('rspec').send(:include, includes)
        end
      end

      def exclude(excludes)
        Project.local_projects do |local_project|
          local_project.task('rspec').send(:exclude, excludes)
        end
      end

      def signifiers(signifiers)
        Project.local_projects do |local_project|
          local_project.task('rspec').send(:signifiers, signifiers)
        end
      end
    end

    def initialize(*args)
      super
      @include = []
      @exclude = []
      @signifiers = []
      # The RSpec rake task doesn't inherit from Rake::Task but we need to, so we'll wrap it
      @rspec_task = RSpec::Core::RakeTask.new
      # Set fail_on_error to rspec default
      @fail_on_error = @rspec_task.fail_on_error
    end

    attr_writer :additional_opts
    def additional_opts
      @additional_opts ||= []
    end

    attr_accessor :fail_on_error

    def include(includes)
      @include += includes
    end

    def exclude(excludes)
      @exclude += excludes
    end

    def signifiers(signifiers)
      @signifiers += signifiers
    end

    def build_rspec_opts(rspec)
      opts = %W{--color --format documentation}
      opts.concat(%W{--require #{File.join(Dir.pwd, 'tasks', 'failure_formatter')}})
      opts.concat(%W{--format ModifiedRSpec::Formatters::FailuresFormatter})

      ruby_includes = rspec.ruby_includes.map { |dir| "-I #{dir}" }
      opts.concat(ruby_includes)

      @signifiers.each do |signifier|
        if signifier =~ /\d+/
          opts.concat(%W{-l #{signifier}})
        else
          opts.concat(%W{-e "#{signifier}"})
        end
      end

      opts.concat(additional_opts)
      opts << ENV['RSPEC_OPTS'] unless ENV['RSPEC_OPTS'].nil?
      opts
    end

    def execute(args)
      super
      rspec = project.rspec

      unless rspec.enabled?
        fail("Project #{project} does not have RSpec enabled")
      end

      @rspec_task.rspec_opts = build_rspec_opts(rspec)
      @rspec_task.verbose = false
      @rspec_task.fail_on_error = fail_on_error

      @rspec_task.rspec_opts.concat(files_to_run(rspec.pattern))

      # spec_command is private for some reason
      command = @rspec_task.send(:spec_command)
      begin
        info(command)
        # If you just use system(command) and then interrupt the Buildr process, RSpec will
        # keep running for a little while longer and print extra stuff to the console.  To prevent
        # that, we spawn the command to get the pid.  Then we set a trap so that on a SIGINT, the
        # SIGINT gets sent to rspec too.
        pid = Process.spawn(command)
        set_trap(pid)
        _, status = Process.wait2(pid)
      rescue
        error(@rspec_task.failure_message) if @rspec_task.failure_message
      end
      fail("#{command} failed") if @rspec_task.fail_on_error unless status.success?
    end

    protected

    def set_trap(pid)
      Signal.trap('INT') do
        Process.kill('INT', pid)
      end
    end

    def files_to_run(pattern)
      specs_to_run = Dir[pattern]
      unless @include.empty?
        specs_to_run.select! do |s|
          File.basename(s).start_with?(*@include)
        end
      end

      specs_to_run.reject! do |s|
        File.basename(s).start_with?(*@exclude)
      end

      if specs_to_run.empty?
        fail("No specs found matching #{@include}")
      end
      specs_to_run
    end

    def associate_with(project)
      @project = project
    end
  end

  class ParallelRspecTask < Rake::Task
    attr_reader :project
    attr_accessor :rspec_opts

    def execute(args)
      super
      rspec = project.rspec
      ParallelTests::CLI.new.run(["--type", "rspec", "-o", @rspec_opts.join(' '), rspec.spec_dir])
    end

    protected
    def associate_with(project)
      @project = project
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

    def ruby_includes
      ruby_includes = [spec_dir] + requires
      ruby_includes.select do |dir|
        File.exist?(dir)
      end
    end

    # This is not configurable because we have no way to
    # pass this information to the RSpec formatter.
    def failure_file
      project.path_to(:target, 'rspec.failures')
    end

    def pattern
      File.join(spec_dir, '**', '*_spec.rb')
    end

    protected

    attr_reader :project
    def initialize(project)
      @project = project
    end
  end

  module ProjectExtension
    include Extension
    include ::Candlepin::Util

    TEST_SPECIFIC_REGEX = /^rspec:(.+)/

    def rspec
      @rspec ||= ModifiedRSpec::Config.new(project)
    end

    first_time do
      desc "Run RSpec tests in serial"
      task('rspec') do |task|
        ModifiedRSpecTask.run_local('rspec')
      end

      desc "Run RSpec tests that failed last run"
      Project.local_task('rspec:failures')

      desc "Run RSpec tests in parallel"
      Project.local_task('rspec:parallel')

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

        ModifiedRSpecTask.include(included_tests)
        ModifiedRSpecTask.exclude(excluded_tests)
        ModifiedRSpecTask.signifiers(signifiers)
        task('rspec').invoke()
      end
    end

    before_define do |project|
      clean_failures = CleanFailuresTask.define_task('rspec_clean')
      clean_failures.send(:associate_with, project)

      rspec_task = ModifiedRSpecTask.define_task('rspec' => ['rspec_clean'])
      rspec_task.send(:associate_with, project)

      rspec_failures = ModifiedRSpecTask.define_task('rspec_failures')
      rspec_failures.send(:associate_with, project)
      project.task('rspec:failures') do |task|
        if File.exist?(project.rspec.failure_file)
          failures = IO.readlines(project.rspec.failure_file)
          failures.map!(&:strip)
        else
          failures = nil
        end

        if failures.nil?
          success("No failures found to test.")
        else
          rspec_failures.signifiers(failures)
          rspec_failures.enhance(['rspec_clean'])
          rspec_failures.invoke
        end
      end

      rspec_serial = ModifiedRSpecTask.define_task('rspec_serial')
      rspec_serial.send(:associate_with, project)
      rspec_serial.enhance do |task|
        task.additional_opts = %w{--tag serial}
        task.fail_on_error = false if ENV.key?('all_tests')
      end

      rspec_parallel = ParallelRspecTask.define_task('rspec_parallel') do |task|
        task.rspec_opts = rspec_serial.build_rspec_opts(project.rspec) + %w{--tag ~serial}
      end
      rspec_parallel.send(:associate_with, project)

      project.task('rspec:parallel' => ['rspec_clean', 'rspec_serial', 'rspec_parallel'])
    end
  end
end

class Buildr::Project
  include ModifiedRSpec::ProjectExtension
end
