package org.candlepin.gutterball.jackson;

import com.fasterxml.jackson.databind.util.StdConverter;

import java.util.Map;

public class OwnerJsonToKeyConverter extends StdConverter<Map<String, Object>, String> {

    @Override
    public String convert(Map<String, Object> ownerData) {
        return ownerData.containsKey("key") ? (String) ownerData.get("key") : "Unknown";
    }

}
