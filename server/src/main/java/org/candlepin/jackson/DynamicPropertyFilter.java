/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.PropertyWriter;

import org.jboss.resteasy.spi.ResteasyProviderFactory;

/**
 * DynamicPropertyFilter
 *
 * Class to filter objects on a per-object basis, based upon
 * query parameters received through a DynamicFilterData object
 */
public class DynamicPropertyFilter extends CheckableBeanPropertyFilter {

    public boolean isSerializable(Object obj, JsonGenerator jsonGenerator,
        SerializerProvider serializerProvider, PropertyWriter writer) {
        DynamicFilterData filterData =
            ResteasyProviderFactory.getContextData(DynamicFilterData.class);
        if (filterData != null) {
            return !filterData.isAttributeExcluded(writer.getName(), obj);
        }

        // Allow serialization by default
        return true;
    }
}
