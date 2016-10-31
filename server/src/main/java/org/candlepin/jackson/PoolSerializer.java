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

import org.candlepin.model.Pool;
import org.candlepin.model.ProductCurator;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
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
public class PoolSerializer extends JsonSerializer<Pool> {
    private ProductCurator productCurator;
    private JsonSerializer<Object> defaultSerializer;

    public PoolSerializer(JsonSerializer<Object> defaultSerializer, ProductCurator productCurator) {
        this.productCurator = productCurator;
        this.defaultSerializer = defaultSerializer;
    }

    @Override
    public void serialize(Pool pool, JsonGenerator jsonGenerator,
        SerializerProvider serializerProvider)
        throws IOException, JsonProcessingException {
        pool.populateAllTransientProvidedProducts(productCurator);
        defaultSerializer.serialize(pool, jsonGenerator, serializerProvider);
    }
}
