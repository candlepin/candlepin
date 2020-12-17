/**
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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

import org.candlepin.common.jackson.DynamicPropertyFilterMixIn;
import org.candlepin.dto.api.v1.AttributeDTO;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.List;

public abstract class PoolAnnotationMixIn extends DynamicPropertyFilterMixIn {

    @JsonDeserialize(using = AttributeDeserializer.class)
    private List<AttributeDTO> attributes;
    @JsonDeserialize(using = AttributeDeserializer.class)
    private List<AttributeDTO> productAttributes;
    @JsonDeserialize(using = AttributeDeserializer.class)
    private List<AttributeDTO> derivedProductAttributes;

}
