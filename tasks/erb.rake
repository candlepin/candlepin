require 'erb'
require 'ostruct'
require 'yaml'

# Maintain Ruby 1.8 compatibility. Code taken from backports gem.
unless Kernel.method_defined? :define_singleton_method
  module Kernel
    def define_singleton_method(*args, &block)
      class << self
        self
      end.send(:define_method, *args, &block)
    end
  end
end

module ErbRenderer
  class << self
    def render(yaml, filename, project)
      basename = File.basename(filename)
      # Throw away the ".erb" suffix.
      output_file = basename[/(.*)\.erb/, 1]

      # Set ERB trim mode to omit blank lines ending in -%>
      template = ERB.new(File.read(filename), nil, '-')

      begin
        settings = ErbRenderer::YamlData.new(yaml, output_file, project)
        destination = File.join(project.erb.output_dir, output_file)
        File.open(destination, 'w') do |file|
          file.write(template.result(settings.get_binding))
        end
      rescue => e
        warn("Could not render #{basename}!")
        warn("\t#{e}")
        warn("\t#{e.backtrace.join(%Q^\n\t^)}")
      end
    end
  end

  class ProfileStruct < OpenStruct
    def method_missing(sym, *args, &block)
      warn("Could not find profile.#{sym}!")
      super
    end
  end

  class YamlData
    attr_reader :_project
    attr_reader :yaml
    attr_accessor :_

    def profile
      if Buildr.settings.profile.empty?
        warn("There is no profile for the environment #{Buildr.environment}!")
      end
      @profile
    end

    def initialize(full_yaml, output_file, project)
      # Provide an OpenStruct we can use as a namespace within ERB templates if we need to set
      # variables.
      @_ = OpenStruct.new

      # Make the profile an OpenStruct so we can use dot notation to grab properties
      @profile = ProfileStruct.new(Buildr.settings.profile)

      @_project = project

      begin
        @yaml = full_yaml.fetch(output_file)
      rescue IndexError
        warn("#{File.basename(project.erb.yaml)} does not have any data for #{output_file}!")
      end

      blacklist = self.class.instance_methods(false)
      @yaml ||= {}
      @yaml.each_key do |key|
        sym = key.to_sym
        if blacklist.include?(sym)
          warn("#{key} is defined by the #{self.class} class.  Cowardly refusing to override it.")
          next
        end
        if self.respond_to?(sym)
          warn("#{key} is already defined in #{self.method(sym).source_location}.  Boldy overriding it!")
        end
        define_singleton_method(sym) { @yaml[key] }
      end
    end

    # Get a variable from @yaml or return nil.  Takes a block that will only be run
    # if the key is in the hash.  This allows for statements like
    #
    # <%= get("my_optional_value") { |v| v.upcase } || "some default" %>
    #
    # If you instead ran <%= my_optional_value.upcase || "some default" %> then
    # you would get an error because you were dereferencing a nil.
    def get(key)
      if @yaml.has_key?(key) && block_given?
        yield @yaml[key]
      else
        @yaml[key]
      end
    end

    def method_missing(sym, *args, &block)
      name = sym.id2name
      if args.length == 0
        @yaml[name]
      else
        raise NoMethodError, "undefined method '#{sym}' for #{self}", caller(1)
      end
    end

    def get_binding
      binding
    end
  end

  class Config
    def enabled?
      File.exist?(self.erb_directory)
    end

    attr_writer :erb_directory
    def erb_directory
      @erb_directory || project.path_to(:erb)
    end

    attr_writer :yaml
    def yaml
      @yaml || project.path_to('custom.yaml')
    end

    attr_writer :output_dir
    def output_dir
      @output_dir || project.path_to(:target, :generated, :erb)
    end

    protected
    def initialize(project)
      @project = project
    end

    attr_reader :project
  end

  module ProjectExtension
    include Extension

    def erb
      @erb ||= ErbRenderer::Config.new(project)
    end

    first_time do
      desc "Render ERB files"
      Project.local_task('erb')
    end

    before_define do |project|
      task('erb') do |task|
        if task.prerequisites.empty?
          warn("No ERB directory given!")
        else
          # Load YAML at beginning so if the file is malformed, we'll just abort.
          if File.exist?(project.erb.yaml)
            yaml = YAML.load_file(project.erb.yaml)
          else
            yaml = {}
          end

          files = task.prerequisites.map do |path|
            Dir.glob(File.join(path, '**', '*.erb'))
          end
          files.flatten!

          mkdir_p(project.erb.output_dir)
          files.each do |file|
            puts "Rendering #{File.basename(file)} ..."
            ErbRenderer.render(yaml, file, project)
          end
        end
      end
    end

    after_define do |project|
      task('erb' => project.erb.erb_directory) if project.erb.enabled?
    end
  end
end

class Buildr::Project
  include ErbRenderer::ProjectExtension
end
