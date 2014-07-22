# A simple plugin to output either the absolute path of the checkout's root
# or the absolute path of the subproject's root.
#
# Note that if you are using this in a shell environment you will need to
# invoke buildr with the -s/--silent option or else you will get extraneous
# output.
module ProjectRoot
  module ProjectExtension
    include Extension

    first_time do
      desc "Print the root directory of the checkout"
      Project.local_task('checkout_root')

      desc "Print the root directory of the sub-project"
      Project.local_task('project_root')
    end

    after_define do |project|
      project.task('checkout_root') do |task|
        # Buildr sets the working directory to the top of the project.  I don't
        # like relying on implementation behavior but I don't know of another way
        # to get the root of the checkout.
        puts Dir.pwd
      end

      project.task('project_root') do |task|
        puts project.path_to
      end
    end
  end
end

class Buildr::Project
  include ProjectRoot::ProjectExtension
end
