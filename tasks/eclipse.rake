require 'rexml/document'

module ModifiedEclipse
  class << self
    def fix_classpath(project)
      doc = REXML::Document.new(File.new(project.path_to('.classpath')))
      xpaths = [
        "/classpath/classpathentry[@path='src/test/java']",
        "/classpath/classpathentry[@path='src/test/resources']",
        "/classpath/classpathentry[@path='src/main/resources']",
      ]
      xpaths.each do |exp|
        elements = REXML::XPath.match(doc, exp)
        elements.map! { |e| e.delete_attribute('output') }
      end

      elements = REXML::XPath.match(doc, "/classpath/classpathentry[@kind='output']")
      elements.map! { |e| e.add_attribute('path', 'target-eclipse') }

      File.open(project.path_to('.classpath'), 'w') do |f|
        doc.write(f)
      end
    end

    # Eclipse projects are created in the subproject directory and are unaware
    # of any directories above them.  This creates an issue for Eclipse
    # Checkstyle when we want to reference configuration that is stored at the
    # top level of the entire source tree (such as checks.xml which is off the
    # project_conf directory).
    #
    # One solution would be to create a symlink in every project that points to
    # the shared configuration directory.  This approach creates a lot of
    # redundancy and potential for people to forget to create the symlink
    # directory, etc.
    #
    # The other approach is to generate the .checkstyle file programmatically
    # for each project when the Eclipse task is run.  The master .checkstyle
    # file uses an XML entity to point to the project_conf directory
    #
    # The method below takes the master .checkstyle file which lives in
    # project_conf, reads it, takes the XML entity and alters it to point to
    # the absolute path of checks.xml and then writes the file in the project.
    def fix_checkstyle(project)
      cs = project.checkstyle
      if cs.enabled?
        doc = REXML::Document.new(File.new(cs.eclipse_xml), :raw => :all)
        destination_xml = project.path_to('.checkstyle')

        # The doctype appears to be immutable? So we'll just copy the XML over and add
        # our own corrected doctype.
        altered_doctype = REXML::DocType.new('fileset-config')
        altered_doctype.add(REXML::Entity.new('conf_dir', File.absolute_path('project_conf')))

        comment = REXML::Comment.new('This file is auto-generated.  The master copy is in project_conf.')

        altered_document = REXML::Document.new()
        altered_document.add(comment)
        altered_document.add(
          REXML::XMLDecl.new(REXML::XMLDecl::DEFAULT_VERSION, REXML::XMLDecl::DEFAULT_ENCODING))
        altered_document.add(altered_doctype)
        altered_document.add(doc.root)

        File.open(destination_xml, 'w') do |f|
          altered_document.write(f, 2)
        end
      end
    end
  end

  module ProjectExtension
    include Extension

    after_define do |project|
      eclipse = project.eclipse
      unless eclipse.natures.empty?
        task('clean_eclipse') do |task|
          info("Removing old .classpath and .project")
          FileUtils.rm_f(project.path_to('.project'))
          FileUtils.rm_f(project.path_to('.classpath'))
        end

        task('eclipse' => 'clean_eclipse').enhance do |task|
          info("Fixing eclipse .classpath in #{project}")
          ModifiedEclipse.fix_classpath(project)
          ModifiedEclipse.fix_checkstyle(project)

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
