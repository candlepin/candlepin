require 'buildr/java/emma'

module ModifiedEmma
  # Emma reports are written out in ISO-8859-1 with the &nbsp; entity unencoded
  # making the resultant HTML all messed up.
  def self.fix_emma_reports(html_file)
    # Ruby 1.8.7 compatible code.  Very slow.
    # content = File.read(html_file)
    # new_content = ''
    # content.each_byte do |c|
    #   new_content += (c == 160) ? '&nbsp;' : c.chr
    # end
    # File.open(html_file, 'w') { |f| f.write(new_content) }

    content = File.read(html_file, :encoding => 'ISO-8859-1')
    content.encode!('UTF-8', 'ISO-8859-1')
    content.gsub!(/\u00A0/, '&nbsp;')
    File.open(html_file, 'w') { |f| f.write(content) }
  end

  module ProjectExtension
    include Extension

    first_time do
      desc "Generate EMMA coverage reports"
      Project.local_task('emma:html')
    end

    after_define do |project|
      emma = project.emma
      unless emma.sources.nil?
        task('emma:html') do |task|
          info "Fixing emma reports"
          glob = File.join(project.path_to(:reports, :emma), '**', '*.html')
          Dir[glob].each do |f|
            ModifiedEmma.fix_emma_reports(f)
          end
        end
      end
    end

  end
end

class Buildr::Project
  include ModifiedEmma::ProjectExtension
end
