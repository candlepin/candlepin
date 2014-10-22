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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class EntitlementDeserializer extends JsonDeserializer<Entitlement> {

    private ObjectMapper mapper;

    public EntitlementDeserializer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Entitlement deserialize(JsonParser jp, DeserializationContext context)
            throws IOException, JsonProcessingException {
        JsonNode entJson = jp.getCodec().readTree(jp);
        return getEntitlement(entJson, context);
    }

    private Entitlement getEntitlement(JsonNode entJson, DeserializationContext context) {

        if (entJson == null) {
            return null;
        }

        JsonNode poolJson = entJson.get("pool");

        int entQuantity = entJson.get("quantity").asInt();
        Date startDate = context.parseDate(poolJson.get("startDate").asText());
        Date endDate = context.parseDate(poolJson.get("endDate").asText());

        Entitlement ent = new Entitlement(entQuantity, startDate, endDate);
        ent.setProductId(getValue(poolJson, "productId"));
        ent.setDerivedProductId(getValue(poolJson, "derivedProductId"));
        ent.setProductName(getValue(poolJson, "productName"));
        ent.setDerivedProductName(getValue(poolJson, "derivedProductName"));
        ent.setRestrictedToUsername(getValue(poolJson, "restrictedToUsername"));
        ent.setContractNumber(getValue(poolJson, "contractNumber"));
        ent.setAccountNumber(getValue(poolJson, "accountNumber"));
        ent.setOrderNumber(getValue(poolJson, "orderNumber"));
        ent.setAttributes(getFlattenedProductAttributes(poolJson));
        //ent.setSourceEntitlement(getEntitlement(poolJson.get("sourceEntitlement"), context));
        ent.setProvidedProducts(flattenProvidedProducts(poolJson));
        ent.setDerivedProvidedProducts(flattenDerivedProvidedProducts(poolJson));
        ent.setDerivedProductAttributes(getDerivedProductAttributes(poolJson));

        return ent;
    }

    private Map<String, String> flattenDerivedProvidedProducts(JsonNode poolJson) {
        return flattenProductElements(poolJson, "derivedProvidedProducts");
    }

    private Map<String, String> flattenProvidedProducts(JsonNode poolJson) {
        return flattenProductElements(poolJson, "providedProducts");
    }

    private Map<String, String> flattenProductElements(JsonNode poolJson, String attr) {
        Map<String, String> products = new HashMap<String, String>();
        if (!poolJson.hasNonNull(attr)) {
            return products;
        }

        Iterator<JsonNode> elements = poolJson.get(attr).elements();
        while (elements.hasNext()) {
            JsonNode providedProductJson = elements.next();
            products.put(providedProductJson.get("productId").textValue(),
                    getValue(providedProductJson, "productName"));
        }
        return products;
    }

    private String getValue(JsonNode json, String key) {
        if (!json.has(key)) {
            return null;
        }
        return json.get(key).textValue();
    }

    private Map<String, String> getFlattenedProductAttributes(JsonNode poolJson) {
        Map<String, String> allAttributes = new HashMap<String, String>();
        if (poolJson.hasNonNull("productAttributes")) {
            allAttributes.putAll(getAttributes(poolJson.get("productAttributes").elements()));
        }
        if (poolJson.hasNonNull("poolAttributes")) {
            allAttributes.putAll(getAttributes(poolJson.get("poolAttributes").elements()));
        }
        return allAttributes;
    }

    private Map<String, String> getDerivedProductAttributes(JsonNode poolJson) {
        Map<String, String> attrs = new HashMap<String, String>();
        if (poolJson.hasNonNull("derivedProductAttributes")) {
            attrs.putAll(getAttributes(poolJson.get("derivedProductAttributes").elements()));
        }
        return attrs;
    }

    private Map<String, String> getAttributes(Iterator<JsonNode> elements) {
        HashMap<String, String> attrs = new HashMap<String, String>();
        while (elements.hasNext()) {
            JsonNode node = elements.next();
            attrs.put(node.get("name").textValue(), node.get("value").textValue());
        }
        return attrs;
    }

}
