module Rubocop
  module ProjectExtension
    include Extension

    desc 'Run RuboCop on select files'
      require 'rubocop/rake_task'
      RuboCop::RakeTask.new(:rubocop) do |task|
      task.verbose = true
      task.patterns =
        ['server/client/ruby/candlepin.rb',
         'server/spec/*.rb',
         'server/client/ruby/test/*.rb']
      puts "Running Rubocop on #{task.patterns}"
      task.formatters = ['fuubar']
      task.fail_on_error = true
    end

  end
end

class Buildr::Project
  include Rubocop::ProjectExtension
end
