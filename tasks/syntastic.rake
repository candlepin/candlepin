module Syntastic
  class Config
    attr_writer :classpath_file
    def classpath_file
      @classpath_file || project.path_to(".syntastic_class_path")
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
      desc "Generate a .syntastic_class_path for vim/syntastic"
      Project.local_task('syntastic')
    end

    after_define do |project|
      syntastic = project.syntastic

      project.recursive_task('syntastic') do |task|
        # see https://github.com/scrooloose/syntastic/blob/master/syntax_checkers/java/javac.vim
        total_cp = project.compile.dependencies +
          project.test.compile.dependencies +
          [project.path_to(:target, :classes)]

        total_cp.map!(&:to_s).sort!.uniq!

        File.open(syntastic.classpath_file, "w") do |f|
          total_cp.each { |dep| f.puts(dep) }
          syntastic.additional_jars.each { |jar| f.puts(jar) }
        end
      end
    end
  end
end

class Buildr::Project
  include Syntastic::ProjectExtension
end
