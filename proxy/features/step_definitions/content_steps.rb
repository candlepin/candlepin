Then /^I can create a content called "([^\"]*)"$/ do |content_name|
  create_content content_name
end

Then /^I have Content$/ do
  @candlepin.list_content.length == 1
end

Then /^I have Content "([^\"]*)"$/ do |content|
  contents = @candlepin.list_content()
  found = contents.find {|item| item['content']['name'] == content}
#  puts(found.to_json)
end



def create_content(content_name)
  @candlepin.create_content(content_name, content_name.hash.abs,
                            content_name, 'yum', 'test-vendor',
                            '/path/to/test/content/' + content_name,
                            'path/to/gpg/' + content_name)
end
