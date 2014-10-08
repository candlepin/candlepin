package org.candlepin.gutterball.jackson;

import com.fasterxml.jackson.databind.util.StdConverter;

import java.util.Map;

public class EnvironmentNameConverter extends StdConverter<Map<String, Object>, String> {

    @Override
    public String convert(Map<String, Object> environment) {
        return environment != null && environment.containsKey("name") ?
                (String) environment.get("name") : null;
    }

}
