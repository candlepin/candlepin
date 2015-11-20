# A file for utility functions and classes that are miscellaneous
def remove_matching!(object, pattern)
    # Recursively removes all string items matching a given regexp,
    # deleting key's where appropriate
    if object.is_a? Hash
        object.each do |key, value|
            new_value = remove_matching!(value, pattern)
            if new_value.respond_to? :empty?
                if new_value.empty?
                    object.delete(key)
                end
            elsif new_value.nil?
                object.delete(key)
            else
                object[key] = new_value
            end
        end
    elsif object.is_a? Array
        object.each_with_index do |item, index|
            new_value = remove_matching!(item, pattern)
            if new_value.respond_to? :empty?
                if new_value.empty?
                    object.delete_at(index)
                end
            elsif new_value.nil?
                object.delete_at(index)
            else
                object[index] = new_value
            end
        end
    elsif object.is_a? String
        if pattern.match(object)
            return nil
        else
            return object
        end
    else
        return object
    end
end
