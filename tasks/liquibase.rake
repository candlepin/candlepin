require 'date'
require 'erb'
require 'rexml/document'
require 'stringex_lite'

require './tasks/util'

module Liquibase
  class << self
    def add_to_changelog(file, liquibase)
      file = File.join(liquibase.include_path, File.basename(file))
      liquibase.changelogs.each do |changelog|
        doc_file = File.join(liquibase.changelog_dir, changelog)
        doc = REXML::Document.new(File.open(doc_file))

        # Use double quotes for attributes
        doc.context[:attribute_quote] = :quote
        doc.root.add_element('include', {'file' => file})

        File.open(doc_file, 'w') do |f|
          doc.write(f, 4)
          # REXML doesn't add a newline to the end of the file.  Git complains about that.
          f.write("\n")
        end
      end
    end
  end

  class Config
    attr_writer :changelog_dir
    def changelog_dir
      @changelog_dir || File.join(project.path_to(:src, :main, :resources), include_path)
    end

    def enabled?
      File.exist?(changelog_dir)
    end

    attr_writer :changelogs
    def changelogs
      @changelogs || ['changelog-create.xml']
    end

    attr_writer :include_path
    def include_path
      @include_path || File.join('db', 'changelog')
    end

    attr_writer :template
    def template
      @template || <<-LIQUIBASE
        <?xml version="1.0" encoding="UTF-8"?>

        <databaseChangeLog
                xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-2.0.xsd">

            <changeSet id="<%= id%>" author="<%= author%>">
                <comment><%= description %></comment>
                <!-- See http://www.liquibase.org/documentation/changes/index.html -->
            </changeSet>

        </databaseChangeLog>
        <!-- vim: set expandtab sts=4 sw=4 ai: -->
      LIQUIBASE
    end

    protected
    def initialize(project)
      @project = project
    end

    attr_reader :project
  end

  class TemplateValues < Struct.new(:author, :id, :description)
    def binding
      super
    end
  end

  module ProjectExtension
    include Extension
    include ::Candlepin::Util

    def liquibase
      @liquibase ||= Liquibase::Config.new(project)
    end

    first_time do
      desc "Create a new Liquibase changeset."
      Project.local_task('liquibase:changeset')
    end

    CHANGESET_REGEX = /^liquibase:changeset:(.+)/

    after_define do |project|
      liquibase = project.liquibase

      if liquibase.enabled?
        task("liquibase:changeset") do |task|
          help = strip_heredoc(<<-HELP
          To create a changeset, add a colon to the `liquibase:changeset` task followed
          by a description.  If your description has multiple words, remember to quote it
          so the shell doesn't break it up.

          For example:
              $ buildr "liquibase:changeset:Create a new table"
          HELP
          )
          info(help)
        end

        rule(CHANGESET_REGEX) do |task|
          now = DateTime.now

          values = TemplateValues.new
          values.description = CHANGESET_REGEX.match(task.name)[1]
          values.author = ENV['USER']
          values.id = now.strftime('%Y%m%d%H%M%S') + "-1"

          changeset_slug = values.description.to_url
          date_slug = now.strftime('%Y-%m-%d-%H-%M')
          out_file = "#{date_slug}-#{changeset_slug}.xml"
          out_file = File.join(liquibase.changelog_dir, out_file)

          task('create_changeset') do |t|
            # Set ERB trim mode to omit blank lines ending in -%>
            erb = ERB.new(strip_heredoc(liquibase.template), nil, '-')

            fail("#{project_path_to(project, out_file)} exists already!") if File.exists?(out_file)

            File.open(out_file, 'w') do |f|
              f.write(erb.result(values.binding))
            end

            Liquibase.add_to_changelog(out_file, liquibase)
            info("Wrote #{project_path_to(project, out_file)}")
          end
          task('create_changeset').invoke
        end
      end
    end
  end
end

class Buildr::Project
  include Liquibase::ProjectExtension
end
