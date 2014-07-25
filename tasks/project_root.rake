# A simple plugin to output either the absolute path of the checkout's root
# or the absolute path of the subproject's root.
#
# Note that if you are using this in a shell environment you will need to
# invoke buildr with the -s/--silent option or else you will get extraneous
# output.

require './tasks/util'

module ProjectRoot
  module ProjectExtension
    include Extension
    include ::Candlepin::Util

    first_time do
      desc "Print the root directory of the checkout"
      Project.local_task('checkout_root')

      desc "Print the root directory of the sub-project"
      Project.local_task('project_root')
    end

    PROJECT_ROOT_REGEX = /^project_root:(.+)/

    after_define do |project|
      project.task('checkout_root') do |task|
        puts top_project(project).base_dir
      end

      project.task('project_root') do |task|
        puts project.path_to
      end

      rule(PROJECT_ROOT_REGEX) do |task|
        # Note that if you have a subproject within a subproject you will
        # need to specify the whole subproject string.  E.g.
        # buildr project_root:foo:bar
        subproject = PROJECT_ROOT_REGEX.match(task.name)[1]
        task('subproject_root') do
          puts Buildr.project("#{top_project(project)}:#{subproject}").path_to
        end
        task('subproject_root').invoke
      end
    end
  end
end

class Buildr::Project
  include ::Candlepin::Util
  include ProjectRoot::ProjectExtension
end
