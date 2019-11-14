/**
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.async;

import org.candlepin.hibernate.AbstractJsonConverter;
import org.candlepin.model.AsyncJobStatus.SerializedJobData;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;



public class JobDataConverter extends AbstractJsonConverter<SerializedJobData> {
    private static Logger log = LoggerFactory.getLogger(JobDataConverter.class);

    private static final String METADATA_FIELD_NAME = "metadata";
    private static final String JOB_ARGUMENTS_FIELD_NAME = "job_arguments";
    private static final String JOB_CONSTRAINTS_FIELD_NAME = "job_constraints";
    private static final String JOB_OUTPUT_FIELD_NAME = "job_output";

    public JobDataConverter() {
        super(SerializedJobData.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String serialize(JsonFactory factory, SerializedJobData entity) {
        if (factory == null) {
            throw new IllegalArgumentException("factory is null");
        }

        if (entity == null) {
            return null;
        }

        try (StringWriter writer = new StringWriter();
            JsonGenerator generator = factory.createGenerator(writer)) {

            generator.writeStartObject();

            generator.writeFieldName(METADATA_FIELD_NAME);
            this.serializeMetadata(generator, entity.metadata);

            generator.writeFieldName(JOB_ARGUMENTS_FIELD_NAME);
            this.serializeJobArguments(generator, entity.arguments);

            generator.writeFieldName(JOB_CONSTRAINTS_FIELD_NAME);
            this.serializeJobConstraints(generator, entity.constraints);

            generator.writeFieldName(JOB_OUTPUT_FIELD_NAME);
            this.serializeJobOutput(generator, entity.result);

            generator.writeEndObject();
            generator.flush();

            return writer.toString();
        }
    }

    private void serializeMetadata(JsonGenerator generator, Map<String, String> value) {
        if (value != null) {
            generator.writeStartObject();

            for (Map.Entry<String, String> entry : value.entrySet()) {
                generator.writeObjectField(entry.getKey(), entry.getValue());
            }

            generator.writeEndObject();
        }
        else {
            generator.writeNull();
        }
    }

    private void serializeJobArguments(JsonGenerator generator, Map<String, Object> value) {
        if (value != null) {
            generator.writeStartObject();

            for (Map.Entry<String, Object> entry : value.entrySet()) {
                generator.writeFieldName(entry.getKey());
                this.recursiveSerializer(generator, entry.getValue());
            }

            generator.writeEndObject();
        }
        else {
            generator.writeNull();
        }
    }

    private void serializeJobConstraints(JsonGenerator generator, Map<String, Map<String, Object>> value) {
        if (value != null) {
            generator.writeStartObject();

            for (Map.Entry<String, Map<String, Object>> entry : value.entrySet()) {
                generator.writeFieldName(entry.getKey());
                this.recursiveSerializer(generator, entry.getValue());
            }

            generator.writeEndObject();
        }
        else {
            generator.writeNull();
        }
    }

    private void serializeJobOutput(JsonGenerator generator, Object value) {
        this.recursiveSerializer(generator, value);
    }

    private void recursiveSerializer(JsonGenerator generator, Object value) {
        if (value == null) {
            generator.writeNull();
        }
        else if (value instanceof Collection) {
            generator.writeStartArray();

            for (Object element : (Collection) value) {
                this.recursiveSerializer(generator, element);
            }

            generator.writeEndArray();
        }
        else if (value instanceof Map) {
            generator.writeStartObject();

            Map test = (Map) value;

            for (Map.Entry<Object, Object> entry : test.entrySet()) {
                Object key = entry.getKey();

                if (!(key instanceof String)) {
                    // TODO: Fix exception type
                    throw new RuntimeException("Unsupported key type: " + key.getClass());
                }

                generator.writeFieldName((String) key);
                this.recursiveSerializer(generator, entry.getValue());
            }

            generator.writeEndObject();
        }
        else if (value instanceof String || value.getClass().isPrimitive()) {
            // This will only write an object format in the case of complex objects, which shouldn't
            // be the case here.
            generator.writeObject(value);
        }
        else {
            // TODO: Fix exception type
            throw new RuntimeException("Unsupported value type: " + value.getClass());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected SerializedJobData deserialize(JsonFactory factory, String json) {
        if (factory == null) {
            throw new IllegalArgumentException("factory is null");
        }

        if (json == null) {
            return null;
        }

        SerializedJobData jobData = new SerializedJobData();

        try (JsonParser parser = factory.createParser(json)) {
            while (!parser.isClosed()) {
                if (parser.nextToken() == JsonToken.FIELD_NAME) {
                    String field = parser.currentName();

                    if (METADATA_FIELD_NAME.equals(field)) {
                        Map<String, String> metadata = this.deserializeMetadata(parser);
                        jobData.metadata = metadata;
                    }
                    else if (JOB_ARGUMENTS_FIELD_NAME.equals(field)) {
                        Map<String, Object> arguments = this.deserializeObjectMap(parser);
                        jobData.arguments = arguments;
                    }
                    else if (JOB_CONSTRAINTS_FIELD_NAME.equals(field)) {
                        Map<String, Map<String, Object>> constraints = this.deserializeJobConstraints(parser);
                        jobData.constraints = constraints;
                    }
                    else {
                        Object value = this.recursiveDeserializer(parser);

                        if (JOB_OUTPUT_FIELD_NAME.equals(field)) {
                            jobData.result = value;
                        }
                        else {
                            log.warn("Unexpected field name received in job data: {}", field);
                        }
                    }
                }
            }
        }

        return jobData;
    }

    private Map<String, String> deserializeMetadata(JsonParser parser) {
        Map<String, String> metadata = new HashMap<>();

        if (parser.nextToken() != JsonToken.START_OBJECT) {
            throw new RuntimeException("Invalid metadata format");
        }

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            switch (parser.currentToken()) {
                case FIELD_NAME: // We should be seeing this a lot.
                    break;

                case VALUE_STRING:
                    metadata.put(parser.getCurrentName(), parser.getText());
                    break;

                default:
                    throw new RuntimeException("Invalid metadata format");
            }
        }

        return metadata;
    }

    private Map<String, Object> deserializeObjectMap(JsonParser parser) {
        Map<String, Object> args = new HashMap<>();

        if (parser.nextToken() != JsonToken.START_OBJECT) {
            throw new RuntimeException("Malformed object map");
        }

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (parser.currentToken() != JsonToken.FIELD_NAME) {
                throw new RuntimeException("Malformed object map");
            }

            String name = parser.getCurrentName();
            Object obj = this.recursiveDeserializer(parser);

            args.put(name, obj);
        }

        return args;
    }

    private Map<String, Map<String, Object>> deserializeJobConstraints(JsonParser parser) {
        Map<String, Map<String, Object>> constraints = new HashMap<>();

        if (parser.nextToken() != JsonToken.START_OBJECT) {
            throw new RuntimeException("Malformed job constraints");
        }

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (parser.currentToken() != JsonToken.FIELD_NAME) {
                throw new RuntimeException("Malformed job constraints");
            }

            String name = parser.getCurrentName();
            Map<String, Object> cargs = this.deserializeObjectMap(parser);

            constraints.put(name, cargs);
        }

        return constraints;
    }


    private Object recursiveDeserializer(JsonParser parser) {

        while (!parser.isClosed()) {
            switch (parser.nextToken()) {
                case START_ARRAY: // Lists
                    List<Object> list = new LinkedList<>();

                    for (Object obj = this.recursiveDeserializer(parser);
                        parser.currentToken() != JsonToken.END_ARRAY;
                        obj = this.recursiveDeserializer(parser)) {

                        list.add(obj);
                    }

                    return list;

                case END_ARRAY:
                    // Return null, the START_* step will ignore the value and terminate array processing
                    return null;

                case START_OBJECT: // Maps
                    Map<String, Object> map = new HashMap<>();

                    while (!parser.isClosed()) {
                        switch (parser.nextToken()) {
                            case FIELD_NAME:
                                String name = parser.getCurrentName();
                                map.put(name, this.recursiveDeserializer(parser));
                                break;

                            case END_OBJECT:
                                return map;

                            default:
                                throw new RuntimeException("Unexpected token: " + parser.getCurrentToken());
                        }
                    }
                    break;

                case VALUE_TRUE:
                case VALUE_FALSE:
                    return parser.getBooleanValue();

                case VALUE_NULL:
                    return null;

                case VALUE_STRING:
                    return parser.getText();

                case VALUE_NUMBER_FLOAT:
                    return parser.getDoubleValue();

                case VALUE_NUMBER_INT:
                    return parser.getIntValue();

                default:
                    throw new RuntimeException("Unexpected token: " + parser.getCurrentToken());
            }
        }

    }

}
