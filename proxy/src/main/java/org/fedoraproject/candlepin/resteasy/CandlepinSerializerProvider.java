/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.resteasy;

import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.SerializationConfig;
import org.codehaus.jackson.map.SerializerFactory;
import org.codehaus.jackson.map.ser.SerializerCache;
import org.codehaus.jackson.map.ser.StdSerializerProvider;

/**
 * CandlepinSerializerProvider - Custom serializer implementation to expose our 
 * custom CandlkepinSerializerFactory method.
 */
public class CandlepinSerializerProvider extends StdSerializerProvider {
    
    private SerializerCache cachedSerializers = new SerializerCache();

    public CandlepinSerializerProvider() {
        super();
    }

    protected CandlepinSerializerProvider(SerializationConfig config, 
        StdSerializerProvider src, SerializerFactory f) {
        super(config, src, f);
    }
    
    protected CandlepinSerializerProvider createInstance(SerializationConfig config, 
        SerializerFactory jsf) {
        return new CandlepinSerializerProvider(config, (StdSerializerProvider) this, jsf);
    }

    public JsonSerializer<Object> createSerializerSkipCustom(Class<?> valueType) {
        // Fast lookup from local cache
        JsonSerializer<Object> ser = cachedSerializers.untypedValueSerializer(valueType);
        if (ser != null) {
            return ser;
        }

        CandlepinSerializerFactory csf = (CandlepinSerializerFactory) _serializerFactory;
        ser = (JsonSerializer<Object>) csf.createSerializerSkipCustom(valueType, _config);

        // Needed to create it, lets add to the cache
        cachedSerializers.addNonTypedSerializer(valueType, ser);

        return ser;
    }
}
