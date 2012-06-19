#
# CHECKSTYLE task, a Buildr plugin would be better, but this is faster
#

require 'buildr/java'

CHECKSTYLE = transitive('checkstyle:checkstyle:jar:5.0')

module CheckStyle
  include Extension

  first_time do
    desc 'Run Checkstyle'
    Project.local_task('checkstyle')
  end

  after_define do |project|
    project.eclipse.natures 'net.sf.eclipsecs.core.CheckstyleNature'
    project.eclipse.builders 'net.sf.eclipsecs.core.CheckstyleBuilder'

      task("checkstyle") do
      Buildr.ant('checkstyle') do |ant|
        rm_rf 'reports/checkstyle_report.xml'
        mkdir_p 'reports'

        ant.taskdef :resource=>"checkstyletask.properties",
          :classpath=>Buildr.artifacts(CHECKSTYLE).each(&:invoke).map(&:name).join(File::PATH_SEPARATOR)

        # check the main src
        ant.checkstyle :config=>"buildconf/checkstyle.xml",
                       :classpath=>Buildr.artifacts(CHECKSTYLE, project.compile.dependencies).each(&:invoke).map(&:name).join(File::PATH_SEPARATOR) do


          ant.classpath :path=>'target/classes'
          ant.formatter :type=>'plain'
          ant.formatter :type=>'xml', :toFile=>"reports/checkstyle_report.xml"

          ant.property :key=>'javadoc.method.scope', :value=>'public'
          ant.property :key=>'javadoc.type.scope', :value=>'package'
          ant.property :key=>'javadoc.var.scope', :value=>'package'
          ant.property :key=>'javadoc.lazy', :value=>'false'
          ant.property :key=>'javadoc.missing.param', :value=>'true'
          ant.property :key=>'checkstyle.cache.file', :value=>'target/checkstyle.cache.src'
          ant.property :key=>'checkstyle.header.file',
              :value=>'buildconf/LICENSE.txt'

          ant.fileset :dir=>"src/main/java", :includes=>'**/*.java', :excludes=>'**/OIDUtil.java'
        end

        # check the test src
        ant.checkstyle :config=>"buildconf/checkstyle.xml" do

          ant.classpath :path=>'target/test/classes'
          ant.formatter :type=>'plain'
          ant.formatter :type=>'xml', :toFile=>"reports/checkstyle_report.xml"

          ant.property :key=>'javadoc.method.scope', :value=>'nothing'
          ant.property :key=>'javadoc.type.scope', :value=>'nothing'
          ant.property :key=>'javadoc.var.scope', :value=>'nothing'
          ant.property :key=>'javadoc.lazy', :value=>'true'
          ant.property :key=>'javadoc.missing.param', :value=>'false'
          ant.property :key=>'checkstyle.cache.file', :value=>'target/checkstyle.cache.src'
          ant.property :key=>'checkstyle.header.file',
              :value=>'buildconf/LICENSE.txt'

          ant.fileset :dir=>"src/test/java", :includes=>'**/*.java'
        end
      end
    end
  end

  task "checkstyle" => :compile
end

class Buildr::Project
  include CheckStyle
end
