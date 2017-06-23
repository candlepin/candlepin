require 'json'
require './tasks/util'

module Swagger
  include ::Candlepin::Util

  class << self
    def dependencies
      Buildr.transitive('io.swagger:swagger-codegen-cli:jar:2.2.1')
    end
  end

  class Config
    attr_accessor :enabled
    attr_accessor :json_source
    attr_reader :project
    attr_writer :language
    attr_writer :destination

    def enabled?
      !!@enabled
    end

    def language
      @language || "ruby"
    end

    def destination
      @language || project.path_to(:target, :swagger_client)
    end

    protected
    def initialize(project)
      @project = project
    end
  end

  module ProjectExtension
    include Extension

    def swagger
      @swagger ||= Swagger::Config.new(project)
    end

    first_time do
      desc 'Generate the Ruby client from swagger.json'
      Project.local_task('swagger:client')

      desc 'Download swagger.json from your local deployment'
      Project.local_task('swagger:json')

      desc 'Download swagger.json and build the client'
      Project.local_task('swagger')
    end

    after_define do |project|
      swagger = project.swagger

      if swagger.enabled?
        # TODO It would be nice if we could deploy and run Candlepin in the task
        # instead of relying on the user to get Candlepin running out-of-band.  It's
        # theoretically possible to deploy with Jetty, grab the swagger.json from
        # Jetty and then shut it down.  I've not been able to get that to work though
        # due to issues with Guice. The code to deploy to Jetty would look like this:

        # jetty = Buildr::Jetty.new('jetty', "http://localhost:8181")
        # task('swagger:jetty' => [project.package(:war), jetty.use]) do |task|
        #   jetty.deploy("http://localhost:8181", task.prerequisites.first)
        #   # download swagger.json here
        # end

        task('swagger:json') do |task|
          old_verify_mode = ENV['SSL_VERIFY_MODE']
          begin
            uri = URI.parse(swagger.json_source)
            http = Net::HTTP.new(uri.host, uri.port)
            if %w[localhost 127.0.0.1 ::1].include?(uri.host)
              ENV['SSL_VERIFY_MODE'] = 'VERIFY_NONE'
            end
            URI.download(swagger.json_source, project.path_to('swagger.json'))
          rescue => e
            error(e)
            error("\t#{e.backtrace.join(%Q^\n\t^)}")
            fail("Could not download Swagger JSON from '#{swagger.json_source}'")
          ensure
            ENV['SSL_VERIFY_MODE'] = old_verify_mode
          end

          # Format the file because the one directly from Swagger looks ugly
          json = JSON.parse(File.read(project.path_to('swagger.json')))
          File.open(project.path_to('swagger.json'), 'w') do |f|
            f.write(JSON.pretty_generate(json))
          end
        end

        task('swagger:client') do |task|
          cp = Buildr.artifacts(Swagger.dependencies).each do |a|
            a.invoke() if a.respond_to?(:invoke)
          end.map(&:to_s).join(File::PATH_SEPARATOR)

          if File.exist?(project.path_to('swagger.json'))
            Java::Commands.java(
              "io.swagger.codegen.SwaggerCodegen",
              "generate",
              "-i", project.path_to('swagger.json'),
              "-l", swagger.language,
              "-o", swagger.destination,
              :classpath => cp)
          else
            fail('No swagger.json file was found.  Run `swagger:json` to fetch it.')
          end
        end

        task('swagger' => ['swagger:json', 'swagger:client'])
      end
    end
  end
end

class Buildr::Project
  include Swagger::ProjectExtension
end
