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
package org.candlepin.policy.js;

import org.candlepin.exceptions.IseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import javax.inject.Inject;


/**
 * RulesObjectMapper
 *
 * A wrapper around a jackson ObjectMapper that is used to
 * serialize our model objects into JSON and to recreate them
 * from a JSON string.
 *
 * The mapper is configured with filters to filter out any info
 * on our model objects that is not useful to the rules.
 *
 * This class is implemented as a singleton because it is very
 * expensive to create a jackson ObjectMapper and it is preferred
 * to have it instantiated once.
 *
 */
public class RulesObjectMapper {

    private static final Logger log = LoggerFactory.getLogger(RulesObjectMapper.class);

    private final ObjectMapper mapper;

    @Inject
    public RulesObjectMapper(ObjectMapper objectMapper) {
        this.mapper = Objects.requireNonNull(objectMapper);
    }

    public String toJsonString(Map<String, Object> toSerialize) {
        ObjectNode mainNode = this.mapper.createObjectNode();
        for (Entry<String, Object> entry : toSerialize.entrySet()) {
            mainNode.putPOJO(entry.getKey(), entry.getValue());
        }

        try {
            return this.mapper.writeValueAsString(mainNode);
        }
        catch (Exception e) {
            log.error("Unable to serialize objects to JSON.", e);
            throw new IseException("Unable to serialize objects to JSON.", e);
        }
    }

    public <T extends Object> T toObject(String json, Class<T> clazz) {
        try {
            return mapper.readValue(json, clazz);
        }
        catch (Exception e) {
            log.error("Error parsing JSON from rules into: " + clazz.getName(), e);
            log.error(json);
            throw new IseException("Unable to build object from JSON.", e);
        }
    }

    public <T extends Object> T toObject(String json, TypeReference<T> typeref) {
        try {
            return mapper.readValue(json, typeref);
        }
        catch (Exception e) {
            log.error("Error parsing JSON from rules", e);
            log.error(json);
            throw new IseException("Unable to build object from JSON.", e);
        }
    }
}
