# Ran into a load of LoadErrors when trying to require from a
# .rake file.  So we put the custom formatter in a .rb file.
require 'rspec/core/formatters/base_formatter'
require 'fileutils'

module ModifiedRSpec
  # Courtesy http://artsy.github.io/blog/2012/05/15/how-to-organize-over-3000-rspec-specs-and-retry-test-failures
  module Formatters
    class FailuresFormatter < RSpec::Core::Formatters::BaseFormatter
      def dump_failures
        return if failed_examples.empty?
        # Ideally we'd get the project directory from Buildr, but I don't see a way
        # to do that easily.  Instead we'll assume that any spec test we're running
        # is going to be under the spec directory in the project directory.
        project_dir = File.dirname(File.dirname(RSpec.configuration.files_to_run.first))
        failure_file = File.join(project_dir, 'target', 'rspec.failures')
        FileUtils.mkdir(File.join(project_dir, 'target'))

        # We are going to append so that parallel tests can all
        # use the same file.  We must be diligent in deleting the
        # failure file before each run.  The rspec.rake plugin
        # will handle the deletion.
        File.open(failure_file, "a") do |f|
          # TODO: This is a blocking lock which could lead to
          # deadlock if something horrible happens in a thread.
          flock(f, File::LOCK_EX) do |l|
            failed_examples.each do |ex|
              f.puts(retry_command(ex))
            end
          end
        end
      end

      def flock(file, mode)
        success = file.flock(mode)
        if success
          begin
            yield file
          ensure
            file.flock(File::LOCK_UN)
          end
        end
        return success
      end

      # We can't just use the line numbers because people are apt
      # to make changes to failing tests and therefore change the
      # line numbers!
      def retry_command(example)
        example.full_description.gsub("\"", "\\\"")
      end
    end
  end
end
