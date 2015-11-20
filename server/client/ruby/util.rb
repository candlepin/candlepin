require 'date'

# A file for utility functions and classes that are miscellaneous
def fix_times!(object, list_of_keys_to_check)
    # Recursively fixes times for the list of keys provided
    if object.is_a? Hash
        object.each do |key, value|
            if list_of_keys_to_check.include?(key) and value.is_a? String
                object[key] = DateTime.strptime(value, "%Y-%m-%dT%H:%M:%S")
            else
                object[key] = fix_times!(value, list_of_keys_to_check)
            end
        end
    elsif object.is_a? Array
        object.each_with_index do |item, index|
            object[index] = fix_times!(item, list_of_keys_to_check)
        end
    else
        return object
    end
end
