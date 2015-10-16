module ValidateTranslation
  class Config
    attr_writer :po_directory
    def po_directory
      @po_directory || project.path_to(:po)
    end

    def has_po_files?
      File.exist?(po_directory)
    end

    protected
    def initialize(project)
      @project = project
    end

    attr_reader :project
  end


  module ProjectExtension
    include Extension

    def validate_translation
      @validate_translation ||= ValidateTranslation::Config.new(project)
    end

    first_time do
      desc 'validate po files'
      Project.local_task('validate_translation')

      desc 'fix po file validation errors'
      Project.local_task('validate_translation:fix')
    end

    after_define do |project|
        validate_translation = project.validate_translation

        project.recursive_task('validate_translation') do |task|
          if validate_translation.has_po_files?
            puts "Running translation validation in #{project}"
            po_files = Dir[project.path_to(validate_translation.po_directory, '*.po')]
            has_errors = false
            po_files.each do |po_file|
              is_msgstr = false
              filename_printed = false
              File.open(po_file, "r").each_line do |line|
                if line.start_with?('msgid')
                  is_msgstr = false
                elsif line.start_with?('msgstr')
                  is_msgstr = true
                end
                if is_msgstr && line  =~ /([^'])'([^'])/
                  if !filename_printed
                    puts  Buildr::Console.color("\n#{po_file}:", :red)
                    filename_printed = true
                  end
                  puts line
                  has_errors = true
                end
              end
            end
            if has_errors
              raise "Single quotes must be escaped by quotes." \
                  " Please fix above validation errors by executing" \
                  " validate_translation:fix or better yet, enforcing it in zanata"
            else
              puts "Translations validation successful"
            end
          end
        end

        project.recursive_task('validate_translation:fix') do |task|
          if validate_translation.has_po_files?
            puts "Fixing translation validation errors in #{project}"
            po_files = Dir[project.path_to(validate_translation.po_directory, '*.po')]
            po_files.each do |po_file|
              lines = ""
              is_msgstr = false
              File.open(po_file, "r").each_line do |line|
                if line.start_with?('msgid')
                  is_msgstr = false
                elsif line.start_with?('msgstr')
                  is_msgstr = true
                end
                if is_msgstr
                  line = line.gsub(/([^'])'([^'])/,"\\1''\\2")
                end
                lines << line
              end
              File.open(po_file, "w") {|file| file.puts lines }
            end
            puts "Translations update complete"
          end
        end

    end

  end
end

class Buildr::Project
  include ValidateTranslation::ProjectExtension
end
