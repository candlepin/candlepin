package org.candlepin.gutterball.jackson;

import com.fasterxml.jackson.databind.util.StdConverter;

import java.util.Map;

public class HypervisorIdToStringConverter extends StdConverter<Map<String, Object>, String> {

    @Override
    public String convert(Map<String, Object> hypervisorId) {
        return hypervisorId != null && hypervisorId.containsKey("hypervisorId") ?
                    (String) hypervisorId.get("hypervisorId") : null;
    }

}
