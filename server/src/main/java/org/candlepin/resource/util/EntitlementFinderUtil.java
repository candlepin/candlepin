package org.candlepin.resource.util;

import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.candlepin.model.EntitlementFilterBuilder;
import org.candlepin.resteasy.parameter.KeyValueParameter;

public class EntitlementFinderUtil {

    public static EntitlementFilterBuilder createFilter(String matches,
        List<KeyValueParameter> attrFilters) {
            EntitlementFilterBuilder filters = new EntitlementFilterBuilder();
            for (KeyValueParameter filterParam : attrFilters) {
                filters.addAttributeFilter(filterParam.key(), filterParam.value());
            }
            if (!StringUtils.isEmpty(matches)) {
                filters.addMatchesFilter(matches);
            }
        return filters;
    }

}
