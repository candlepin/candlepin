require 'rexml/document'

module ModifiedEclipse
  module ProjectExtension
    include Extension

    after_define do |project|
      unless project.eclipse.natures.empty?
        task('eclipse').enhance do |task|
          info("Fixing eclipse .classpath in #{project}")

          doc = REXML::Document.new(File.new(project.path_to('.classpath')))
          elements = REXML::XPath.match(doc, "/classpath/classpathentry[@path='src/main/resources']")
          elements.map! { |e| e.delete_attribute('output') }
          doc.write(File.open(project.path_to('.classpath'), 'w'))

          # make the gettext output dir to silence eclipse errors
          mkdir_p(project.path_to(:target, "generated-source"))
        end
      end
    end
  end
end

class Buildr::Project
  include ModifiedEclipse::ProjectExtension
end
