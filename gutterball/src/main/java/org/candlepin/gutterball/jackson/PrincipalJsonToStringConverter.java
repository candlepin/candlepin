package org.candlepin.gutterball.jackson;

import com.fasterxml.jackson.databind.util.StdConverter;

import java.util.Map;

public class PrincipalJsonToStringConverter extends StdConverter<Map<String, Object>, String> {

    @Override
    public String convert(Map<String, Object> principal) {
        String type = (String) principal.get("type");
        String name = (String) principal.get("name");
        // Nothing special about this format, just a way to concat both
        // into the same field.
        return type + "@" + name;
    }


}
