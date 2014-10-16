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

package org.candlepin.gutterball.jackson;

import org.candlepin.gutterball.model.snapshot.Entitlement;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Date;

public class EntitlementDeserializer extends JsonDeserializer<Entitlement> {

    private ObjectMapper mapper;

    public EntitlementDeserializer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Entitlement deserialize(JsonParser jp, DeserializationContext context)
            throws IOException, JsonProcessingException {
        JsonNode entJson = jp.getCodec().readTree(jp);
        JsonNode poolJson = entJson.get("pool");
        JsonNode sourceEntJson = poolJson.get("sourceEntitlement");

//        ent.setSourceEntitlement(sourceEntJson == null ? null :
//            mapper.readValue(sourceEntJson.binaryValue(), Entitlement.class));

        int entQuantity = entJson.get("quanity").asInt();
        Date startDate = context.parseDate(poolJson.get("startDate").textValue());
        Date endDate = context.parseDate(poolJson.get("endDate").textValue());

        Entitlement ent = new Entitlement(entQuantity, startDate, endDate);

        return ent;
    }



}
