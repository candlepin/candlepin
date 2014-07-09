
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
    # none at this time
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
