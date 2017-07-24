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

import com.fasterxml.jackson.databind.ser.impl.BeanAsArraySerializer;
import com.fasterxml.jackson.databind.ser.impl.ObjectIdWriter;
import com.fasterxml.jackson.databind.ser.std.BeanSerializerBase;
import org.candlepin.model.Pool;
import org.candlepin.model.ProductCurator;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * Jackson JsonSerializer that will populate transient fields on
 * Pool, after that it will trigger default serialization of the Pool.
 * In the past, the provided products were mapped as Hibernate collections
 * so it didn't require any special handling. But since now, the provided
 * products are taken from reference cache, this PoolSerializer is necessary
 * in various places: Rules, REST response
 * serialization, HornetQ, AMQP. The reason for choosing
 * JsonSerializer extension is that it is a simplest way to insert code just
 * before default serialization of a Pool. Without disturbing other extensions
 * that we already have in serialization (filtering, Hateoas)
 */
public class PoolSerializer extends BeanSerializerBase {
    private ProductCurator productCurator;
    private BeanSerializerBase base;

    @Override
    public final void serialize(Object bean, JsonGenerator jgen, SerializerProvider provider)
        throws IOException {

        ((Pool) bean).populateAllTransientProvidedProducts(productCurator);
        base.serialize(bean, jgen, provider);
    }

    protected PoolSerializer(BeanSerializerBase src, ProductCurator productCurator) {
        super(src);

        this.productCurator = productCurator;
        this.base = src;
    }

    protected PoolSerializer(BeanSerializerBase src,
        ObjectIdWriter objectIdWriter, Object filterId) {
        super(src, objectIdWriter, filterId);
    }

    protected PoolSerializer(BeanSerializerBase src, String[] toIgnore) {
        super(src, toIgnore);
    }

    @Override
    public BeanSerializerBase withObjectIdWriter(ObjectIdWriter objectIdWriter) {
        return new PoolSerializer(this, objectIdWriter, _propertyFilterId);
    }

    @Override
    protected BeanSerializerBase withFilterId(Object filterId) {
        return new PoolSerializer(this, _objectIdWriter, filterId);
    }

    @Override
    protected BeanSerializerBase withIgnorals(String[] toIgnore) {
        return new PoolSerializer(this, toIgnore);
    }

    protected BeanSerializerBase asArraySerializer() {
        if ((_objectIdWriter == null) && (_anyGetterWriter == null) && (_propertyFilterId == null)) {
            return new BeanAsArraySerializer(this);
        }

        // already is one, so:
        return this;
    }
}
