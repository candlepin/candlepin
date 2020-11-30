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

import org.candlepin.common.jackson.HateoasBeanPropertyFilter;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonStreamContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.PropertyWriter;

/**
 * PoolEventFilter to show full pool json inside a list of entitlements for events
 */
//@Component
public class PoolEventFilter extends HateoasBeanPropertyFilter {

    public boolean isSerializable(Object obj, JsonGenerator jsonGenerator,
        SerializerProvider serializerProvider, PropertyWriter writer) {
        JsonStreamContext context = jsonGenerator.getOutputContext();

        // Special case list of entitlements to show full json
        if (context.getParent() != null && context.getParent().getParent() != null &&
            context.getParent().getParent().getParent() != null &&
            context.getParent().inObject() &&
            context.getParent().getParent().getParent().inRoot()) {
            return true;
        }
        return super.isSerializable(obj, jsonGenerator, serializerProvider, writer);
    }
}
