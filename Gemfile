source 'https://rubygems.org'

gem 'buildr', '1.5.3'
# Not a typo - we use both buildr and builder
gem 'builder'
gem 'rspec', '~> 3.0'
gem 'mime-types', '~> 1.25.0'
gem 'oauth'
gem 'parallel_tests'
gem 'buildr-findBugs'
gem 'pmd'
gem 'stringex'
gem 'digest-murmurhash'
gem 'httpclient'
gem 'activesupport', '~> 4.2'
gem 'qpid_proton', '~> 0.17.0'

# Remove this once we are fully using the new Ruby bindings
gem 'rest-client', '~> 1.6.0'

group :development do
  gem 'webrick'
  gem 'pry'
  gem 'pry-byebug'
  gem 'pry-stack_explorer'
  # Rubocop can add new checks in new releases which
  # can result in new errors, so we control the version
  # very strictly
  gem 'rubocop', '=0.36.0'
end
