require 'rubygems'
require 'libxml'

include LibXML

module  JPPRepo 

  $jpprepo = nil

  class ArchiveDef
    attr_accessor   :groupId
    attr_accessor   :artifactId
    attr_accessor   :version
    def to_s
      return "#{groupId}:#{artifactId}:#{version}"
    end
    def no_version
      return "#{groupId}:#{artifactId}"
    end

  end

  class PostCallbacks
    include XML::SaxParser::Callbacks

    attr_accessor :arch_map
    attr_accessor :archive
    attr_accessor :current
    attr_accessor :key
    attr_accessor :value
    attr_accessor :isKey
    def initialize()
      @current = nil
      @archive = @key
      @arch_map = Hash.new    
    end
    
    def on_start_element(element, attributes)
      if ( element == 'dependency')
        return
      end

      if ( element == 'jpp')
        @value = ArchiveDef.new 
        @archive  = @value
      elsif ( element == 'maven')
        @key = ArchiveDef.new 
        @archive = @key
      else
        @current = element
      end
    end

    def on_end_element(element)
      k="org.apache.ant:ant:1.7.1"
      if ( element == 'dependency')
        @arch_map[@key.no_version]=@value
        @arch_map[@key.to_s]=@value
      end
      if ( element == 'dependencies')
        @arch_map[@key]=@value
      end
      
      @current = nil
      
    end

    def on_characters(chars)
      if @current  && chars.length > 0
        
        case @current
        when "groupId"
          @archive.groupId = chars
        when "artifactId"     
          @archive.artifactId = chars
        when "version"
          @archive.version = chars
        end
      end
    end
  end

  def build_path(group_path, id, version, name)
    if ($jpprepo == nil)
      parser = XML::SaxParser.file("/etc/maven/maven2-depmap.xml")
      parser.callbacks = PostCallbacks.new
      parser.parse
      $jpprepo = parser.callbacks.arch_map
    end

    if ($jpprepo == nil)
      puts "Still no repo"
      return ""
    end
    groupId = group_path.gsub("/",".")
   
    key = "#{groupId}:#{id}:#{version}"
    puts "looking for #{key}"
    jppInfo = $jpprepo[key]
    if (jppInfo == nil)
      puts "No JPP Info for #{key}"
      key = "#{groupId}:#{id}"
      puts "looking for #{key}"
      jppInfo = $jpprepo[key]
    end

    if (jppInfo == nil)
      puts "No JPP Info for #{key}"
      return ""
    else
      retVal =  "/usr/share/maven2/repository/#{jppInfo.groupId}/#{jppInfo.artifactId}.jar"
      puts retVal
      return retVal
    end  
  end
end


Buildr.repositories.extend JPPRepo
Buildr.repositories.local = "target/m2repository"
Buildr.repositories.remote = "file:////usr/share/maven2/repository/JPP"
