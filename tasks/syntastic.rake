module Syntastic
  class Config
    attr_writer :classpath_file
    def classpath_file
      @classpath_file || project.path_to(".syntastic_javac_config")
    end

    attr_writer :additional_jars
    def additional_jars
      @additional_jars || []
    end

    protected
    def initialize(project)
      @project = project
    end

    attr_reader :project
  end


  module ProjectExtension
    include Extension

    def syntastic
      @syntastic ||= Syntastic::Config.new(project)
    end

    first_time do
      # Define task not specific to any projet.
      desc "Generate a .syntastic_javac_config for vim/syntastic"
      Project.local_task('syntastic')

      # This task can be used as follows
      # let g:syntastic_java_javac_custom_classpath_command = "buildr -s syntastic:echo"
      # Note that it can be a bit slow though
      desc "Echo classpath for syntastic to stdout"
      Project.local_task('syntastic:echo')
    end

    after_define do |project|
      syntastic = project.syntastic

      # see https://github.com/scrooloose/syntastic/blob/master/syntax_checkers/java/javac.vim

      total_cp = project.compile.dependencies +
        project.test.compile.dependencies +
        [project.path_to(:target, :classes)]

      total_cp.map!(&:to_s).sort!.uniq!

      # XXX: Maybe this shouldn't be a recursive task?
      # FIXME: This is a bit slow
      project.recursive_task('syntastic:echo') do |task|
        puts total_cp
        syntastic.additional_jars.each { |jar| puts jar }
      end

      project.recursive_task('syntastic') do |task|
        cp_string = total_cp.concat(syntastic.additional_jars).join(':')

        File.open(syntastic.classpath_file, "w") do |f|
          f.puts "let g:syntastic_java_javac_classpath = \"#{cp_string}\""
        end
      end
    end
  end
end

class Buildr::Project
  include Syntastic::ProjectExtension
end
