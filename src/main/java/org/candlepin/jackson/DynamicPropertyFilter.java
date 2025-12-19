/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.jackson;

import org.jboss.resteasy.core.ResteasyContext;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.TokenStreamContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.PropertyWriter;

import java.util.ArrayList;
import java.util.List;


/**
 * DynamicPropertyFilter
 *
 * Class to filter objects on a per-object basis, based upon
 * query parameters received through a DynamicFilterData object
 */
public class DynamicPropertyFilter extends CheckableBeanPropertyFilter {

    public boolean isSerializable(Object obj, JsonGenerator jsonGenerator,
        SerializationContext serializerProvider, PropertyWriter writer) {

        DynamicFilterData filterData = ResteasyContext.getContextData(DynamicFilterData.class);

        if (filterData != null) {
            List<String> path = new ArrayList<>(10);
            path.clear();
            path.add(0, writer.getName());

            // Build full path from the context...
            TokenStreamContext context = jsonGenerator.streamWriteContext();
            while ((context = context.getParent()) != null) {
                String cname = context.currentName();
                if (cname != null) {
                    path.add(0, cname);
                }
            }

            return !filterData.isAttributeExcluded(path);
        }

        // Allow serialization by default
        return true;
    }
}
