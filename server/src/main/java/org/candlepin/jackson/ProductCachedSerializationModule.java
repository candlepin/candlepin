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

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.std.BeanSerializerBase;
import com.google.inject.Inject;

/**
 * Serialization module that allows us to run code before
 * serialization of Pool takes place and serializes provided
 * products
 * @author fnguyen
 *
 */
public class ProductCachedSerializationModule extends SimpleModule {

    @Inject
    public ProductCachedSerializationModule(final ProductCurator productCurator) {
        setSerializerModifier(new BeanSerializerModifier() {
            @Override
            public JsonSerializer<?> modifySerializer(SerializationConfig config,
                BeanDescription beanDesc, JsonSerializer<?> serializer) {
                if (beanDesc.getBeanClass() == Pool.class) {
                    return new PoolSerializer((BeanSerializerBase) serializer);
                }
                return serializer;
            }
        });
    }


}
