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

      def top_project(project)
        until project.parent.nil? do
          project = project.parent
        end
        project
      end

      def relative_path_to(parent, child)
        Pathname.new(child).relative_path_from(Pathname.new(parent)).to_s
      end

      def top_path_to(project, path)
        top_project(project).path_to(path)
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
    end
  end
end
