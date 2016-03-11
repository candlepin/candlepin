require 'rubocop/rake_task'

module Rubocop
  class Config
    attr_writer :patterns
    def patterns
      @patterns || [File.join(project.base_dir, '**/*.rb')]
    end

    protected
    def initialize(project)
      @project = project
    end

    attr_reader :project
  end


  module ProjectExtension
    include Extension

    def rubocop
      @rubocop ||= Rubocop::Config.new(project)
    end

    first_time do
      desc 'Run RuboCop on select files'
      Project.local_task('rubocop')

      desc 'Run RuboCop on select files'
      Project.local_task('rubocop:auto_correct')
    end

    after_define do |project|

      rubocop = project.rubocop

      project.recursive_task('rubocop') do
        RuboCop::RakeTask.new(:rubocop) do |task|
            task.verbose = true
            task.patterns = rubocop.patterns
            info("Running Rubocop on #{task.patterns}")
            task.formatters = ['fuubar']
            task.fail_on_error = true
        end
      end

      project.recursive_task('rubocop:auto_correct') do
        RuboCop::RakeTask.new(:rubocop) do |task|
            task.verbose = true
            task.patterns = rubocop.patterns
            info("Running Rubocop with auto_correct on #{task.patterns}")
            task.options = ['-a']
            task.formatters = ['fuubar']
            task.fail_on_error = true
        end
      end
    end
  end
end

class Buildr::Project
  include Rubocop::ProjectExtension
end
