require 'rexml/document'

module Candlepin
  module Util
    def self.included(base)
      # If you include Util, all methods under InstanceMethods will
      # become available as instance methods while the methods under
      # ClassMethods will become available as class methods.
      # See http://stackoverflow.com/a/973684
      base.send(:include, InstanceMethods)
      base.send(:extend, ClassMethods)
    end

    module ClassMethods
      # Taken from Rails.  Looks for the least indented line in a heredoc and
      # removes that amount of leading whitespace from each line.
      def strip_heredoc(s)
        indent = s.scan(/^[ \t]*(?=\S)/).min
        indent = (indent.nil?) ? 0 : indent.size
        s.gsub(/^[ \t]{#{indent}}/, '')
      end

      def success(message)
        puts Buildr::Console.color(message.to_s, :green)
      end

      def top_project(project)
        until project.parent.nil? do
          project = project.parent
        end
        project
      end

      def relative_path_to(parent, child)
        if parent.is_a?(Buildr::Project)
          parent = parent.base_dir
        end

        Pathname.new(child).relative_path_from(Pathname.new(parent)).to_s
      end

      def top_path_to(project, path)
        top_project(project).path_to(path)
      end

      def in_project(project, path)
        relative_path = relative_path_to(project, path)
        return false if relative_path.start_with?('..')
        pieces = []
        Pathname.new(relative_path).each_filename do |p|
          pieces << p.to_s
        end
        return Pathname.new(project.path_to(*pieces)).expand_path == Pathname.new(path).expand_path
      end

      def as_pom_artifact(project)
        pom_task = task('project-artifact' => project.package)
        pom_task.extend(ActsAsArtifact)
        spec = {}
        spec[:id] = project.name
        spec[:group] = project.group
        spec[:version] = project.version
        spec[:type] = :pom
        pom_task.send(:apply_spec, spec)
      end
    end

    module InstanceMethods
      def spec_version(spec_file)
        File.open(path_to(spec_file)).each_line do |line|
          if line =~ /\s*Version:\s*(.*?)\s*$/
            return $1
          end
        end
        return "Unknown"
      end

      def spec_release(spec_file)
        File.open(path_to(spec_file)).each_line do |line|
          if line =~ /\s*Release:\s*(.*?)\s*$/
            return $1.chomp("%{?dist}")
          end
        end
        return "Unknown"
      end

      def pom_version(pom_file)
        doc = REXML::Document.new(File.open(pom_file))
        node = REXML::XPath.first(doc, '/project/version')
        return node.text.strip
      end
    end
  end
end
