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




public class JobDataConverter extends AbstractJsonConverter<SerializedJobData> {

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

        try (StringWriter writer = new StringWriter(),
            JsonGenerator generator = factory.createGenerator(writer)) {

            generator.writeStartObject();

            generator.writeFieldName(METADATA_FIELD_NAME);
            this.serializeMetadata(generator, entity.getMetadata());

            generator.writeFieldName(JOB_ARGUMENTS_FIELD_NAME);
            this.serializeJobArguments(generator, entity.getJobArguments());

            generator.writeFieldName(JOB_CONSTRAINTS_FIELD_NAME);
            this.serializeJobConstraints(generator, entity.getJobConstraints());

            generator.writeFieldName(JOB_OUTPUT_FIELD_NAME);
            this.serializeJobOutput(generator, entity.getJobOutput());

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

            for (Map.Entry<Object, Object> entry : ((Map) value).entrySet()) {
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

        try (JsonParser parser = this.factory.createParser(json)) {
            while (!parser.isClosed()) {
                if (parser.nextToken() == JsonToken.FIELD_NAME) {
                    String field = parser.currentName();

                    if (METADATA_FIELD_NAME.equals(field)) {
                        Map<String, String> metadata = this.parseMetadata(parser);
                        jobData.setMetadata(metadata);
                    }
                    else if (JOB_ARGUMENTS_FIELD_NAME.equals(field)) {
                        Map<String, Object> arguments = this.parseJobArguments(parser);
                        jobData.setJobArguments(arguments);
                    }
                    else if (JOB_CONSTRAINTS_FIELD_NAME.equals(field)) {
                        Map<String, Map<String, Object>> constraints = this.parseJobConstraints(parser);
                        jobData.setJobConstraints(constraints);
                    }
                    else {
                        Object value = this.recursiveDeserializer(parser);

                        if (JOB_OUTPUT_FIELD_NAME.equals(field)) {
                            jobData.setJobOutput(value);
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


    private Object recursiveDeserializer(JsonParser parser) {

        while (!parser.isClosed()) {
            switch (parser.nextToken()) {
                case START_ARRAY:
                    // It's an array!
                    break;

                case START_OBJECT:
                    // It's a map!
                    break;

                case VALUE_TRUE:
                case VALUE_FALSE:
                    return parser.getBooleanValue();

                case VALUE_NULL:
                    return null;

                case VALUE_STRING:
                    return parser.getText();

                case VALUE_NUMBER_FLOAT:
                case VALUE_NUMBER_INT:







END_ARRAY

END_OBJECT

FIELD_NAME

NOT_AVAILABLE

START_ARRAY

START_OBJECT

VALUE_EMBEDDED_OBJECT

VALUE_FALSE

VALUE_NULL

VALUE_NUMBER_FLOAT

VALUE_NUMBER_INT

VALUE_STRING

VALUE_TRUE


            }
        }

    }

}
