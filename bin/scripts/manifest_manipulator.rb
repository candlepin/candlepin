#!/usr/bin/env ruby

require 'optparse'
require 'json'
require 'zip'

require_relative "../../client/ruby/candlepin_api"

@pause = false
def print_and_pause(message)
    puts message
    if @pause
        print "Enter y to proceed?"
        input = gets.chomp
        if input.downcase != 'y' and input.downcase != 'yes'
            puts "exiting upon user request"
            exit
        end
    end
end

optparse = OptionParser.new do |opts|
    opts.banner = "Usage: manifest_manipulator -i [FILE] -o [FILE] [[-k|--key] OWNER_KEY] [-p|--pause]"

    opts.on('-i [FILE]', '--input [FILE]', "The input manifest to manipulate.") do |opt|
        @input = opt
    end

    opts.on('-o [FILE]', '--output [FILE]', "The name of the resulting manifest.") do |opt|
        @output = opt
    end

    opts.on('-k [OWNER_KEY]', '--key [OWNER_KEY]', "import the resulting manifest to an owner") do |opt|
        @owner_key = opt
    end

    opts.on('-p', '--pause', "interactive run, pause at every step") do |opt|
        @pause = true
    end

    opts.on('-g', '--goldenticket', "make this a Golden Ticket manifest") do |opt|
        @golden = true
    end

    opts.on( '-h', '--help', 'Display help and exit' ) do
        puts opts
        exit
    end
end

optparse.parse!

if @input == nil or @output == nil
    puts optparse
    exit
end

FileUtils.remove_dir("tmp") if File.exist?("tmp")
FileUtils.remove_dir("tmpexport") if File.exist?("tmpexport")

Zip::File.open(@input) { |zip_file|
    zip_file.each { |f|
    f_path=File.join("tmp", f.name)
    FileUtils.mkdir_p(File.dirname(f_path))
    zip_file.extract(f, f_path) unless File.exist?(f_path)
    }
}

Zip::File.open(File.join("tmp", "consumer_export.zip")) { |zip_file|
    zip_file.each { |f|
    f_path=File.join("tmpexport", f.name)
    FileUtils.mkdir_p(File.dirname(f_path))
    zip_file.extract(f, f_path) unless File.exist?(f_path)
    }
}
puts "Extracted files at current directory in tmp folder"
print_and_pause "About to manipulate extracted files..."

########################################################
# tweak files here
# dont forget to add force=SIGNATURE_CONFLICT&force=MANIFEST_SAME to the url
########################################################
if @golden != nil
  consumer_file = "tmpexport/export/consumer.json"
  fileText = File.read(consumer_file)
  consumer = JSON.parse(fileText)
  consumer['contentAccessMode'] = "org_environment"
  File.write(consumer_file, consumer.to_json)
end
########################################################
# end of tweak files
########################################################
print_and_pause "About to create manifest archive..."

# create a consumer_export.zip from scratch with the updated file
recursive_path = File.join("tmpexport", "**", "**")
updated_inner_zip = File.join("tmp", "updatedinner.zip")

Zip::File::open(updated_inner_zip, Zip::File::CREATE) {
  |zipfile|
  Dir[recursive_path].each do |file|
    if File.file?(file)
  zipfile.add(file.sub("tmpexport/",""),file)
end
  end
}

#add the inner zip to a new manifest
File.delete(@output) if File.exist?(@output)
Zip::File::open(@output, Zip::File::CREATE) {
  |zipfile|
   zipfile.add("consumer_export.zip", updated_inner_zip)
   zipfile.add("signature", File.join("tmp", "signature"))
}


if @owner_key != nil
  print_and_pause "About to upload manifest to owner #{@owner_key}..."
  if @cp == nil
    @cp = Candlepin.new('admin', 'admin', nil, nil, 'localhost', 8443)
  end
  begin
    @cp.import(@owner_key, @output, {:force => ["SIGNATURE_CONFLICT", 'MANIFEST_SAME']})
    puts "done!"
  rescue Exception => e
      puts "#{e}, but probably a successful import!"
  end
end

print_and_pause "About to clean up work directories..."

FileUtils.remove_dir("tmp")
FileUtils.remove_dir("tmpexport")
