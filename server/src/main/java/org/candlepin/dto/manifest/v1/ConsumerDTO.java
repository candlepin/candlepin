/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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
package org.candlepin.dto.manifest.v1;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.candlepin.dto.CandlepinDTO;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import io.swagger.annotations.ApiModel;
import org.candlepin.jackson.SingleValueWrapDeserializer;
import org.candlepin.jackson.SingleValueWrapSerializer;

/**
 * A DTO representation of the Consumer entity as used by the manifest import/export API.
 */
@ApiModel(parent = CandlepinDTO.class,
    description = "DTO representing an upstream consumer as used by the manifest import/export API")
@JsonIgnoreProperties(ignoreUnknown = false)
public class ConsumerDTO extends CandlepinDTO<ConsumerDTO> {
    public static final long serialVersionUID = 1L;

    /**
     * Serialization utility class for wrapping the 'id' field in an 'owner' JSON object,
     * since only the id of the nested owner object is needed by the manifest import/export framework.
     */
    private static class OwnerIdWrapSerializer extends SingleValueWrapSerializer {
        public OwnerIdWrapSerializer() {
            super("id");
        }
    }

    /**
     * Deserialization utility class for unwrapping the 'id' field from an 'owner' JSON object,
     * since only the id of the nested owner object is needed by the manifest import/export framework.
     */
    private static class OwnerIdWrapDeserializer extends SingleValueWrapDeserializer {
        public OwnerIdWrapDeserializer() {
            super("id");
        }
    }

    protected String uuid;
    protected String name;
    protected String owner;
    protected String contentAccessMode;
    protected ConsumerTypeDTO type;
    protected String urlWeb;
    protected String urlApi;

    /**
     * Initializes a new ConsumerDTO instance with null values.
     */
    public ConsumerDTO() {
        // Intentionally left blank
    }

    /**
     * Initializes a new ConsumerDTO instance which is a shallow copy of the provided
     * source entity.
     *
     * @param source
     *  The source entity to copy
     */
    public ConsumerDTO(ConsumerDTO source) {
        super(source);
    }

    /**
     * Retrieves the uuid field of this ConsumerDTO object.
     *
     * @return the uuid of the consumer.
     */
    public String getUuid() {
        return this.uuid;
    }

    /**
     * Sets the uuid to set on this ConsumerDTO object.
     *
     * @param uuid the id to set on this ConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    public ConsumerDTO setUuid(String uuid) {
        this.uuid = uuid;
        return this;
    }

    /**
     * Retrieves the name field of this ConsumerDTO object.
     *
     * @return the name of the consumer.
     */
    public String getName() {
        return this.name;
    }

    /**
     * Sets the name to set on this ConsumerDTO object.
     *
     * @param name the name to set on this ConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    public ConsumerDTO setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Retrieves the owner field of this ConsumerDTO object.
     *
     * @return the owner of the consumer.
     */
    @JsonSerialize(using = OwnerIdWrapSerializer.class)
    public String getOwner() {
        return this.owner;
    }

    /**
     * Sets the owner to set on this ConsumerDTO object.
     *
     * @param owner the owner to set on this ConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    @JsonDeserialize(using = OwnerIdWrapDeserializer.class)
    public ConsumerDTO setOwner(String owner) {
        this.owner = owner;
        return this;
    }

    /**
     * Retrieves the content access mode field of this ConsumerDTO object.
     *
     * @return the content access modes of the consumer.
     */
    public String getContentAccessMode() {
        return this.contentAccessMode;
    }

    /**
     * Sets the content access mode to set on this ConsumerDTO object.
     *
     * @param contentAccessMode the content access mode to set on this ConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    public ConsumerDTO setContentAccessMode(String contentAccessMode) {
        this.contentAccessMode = contentAccessMode;
        return this;
    }

    /**
     * Retrieves the type field of this ConsumerDTO object.
     *
     * @return the type of the consumer.
     */
    public ConsumerTypeDTO getType() {
        return this.type;
    }

    /**
     * Sets the consumer type to set on this ConsumerDTO object.
     *
     * @param type the type to set on this ConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    public ConsumerDTO setType(ConsumerTypeDTO type) {
        this.type = type;
        return this;
    }

    /**
     * Retrieves the web URL field of this ConsumerDTO object.
     *
     * @return the web URL of the consumer.
     */
    public String getUrlWeb() {
        return urlWeb;
    }

    /**
     * Sets the web URL to set on this ConsumerDTO object.
     *
     * @param urlWeb the web URL to set on this ConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    public ConsumerDTO setUrlWeb(String urlWeb) {
        this.urlWeb = urlWeb;
        return this;
    }

    /**
     * Retrieves the API URL field of this ConsumerDTO object.
     *
     * @return the API URL of the consumer.
     */
    public String getUrlApi() {
        return urlApi;
    }

    /**
     * Sets the API URL to set on this ConsumerDTO object.
     *
     * @param urlApi the API URL to set on this ConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    public ConsumerDTO setUrlApi(String urlApi) {
        this.urlApi = urlApi;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("ConsumerDTO [uuid: %s, name: %s, owner id: %s]",
            this.getUuid(), this.getName(), this.getOwner());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof ConsumerDTO) {
            ConsumerDTO that = (ConsumerDTO) obj;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getUuid(), that.getUuid())
                .append(this.getName(), that.getName())
                .append(this.getOwner(), that.getOwner())
                .append(this.getContentAccessMode(), that.getContentAccessMode())
                .append(this.getType(), that.getType())
                .append(this.getUrlWeb(), that.getUrlWeb())
                .append(this.getUrlApi(), that.getUrlApi());

            return builder.isEquals();
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder(37, 7)
            .append(this.getUuid())
            .append(this.getName())
            .append(this.getOwner())
            .append(this.getContentAccessMode())
            .append(this.getType())
            .append(this.getUrlWeb())
            .append(this.getUrlApi());

        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsumerDTO clone() {
        ConsumerDTO copy = super.clone();
        copy.type = type != null ? type.clone() : null;

        return copy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsumerDTO populate(ConsumerDTO source) {
        super.populate(source);

        this.setUuid(source.getUuid());
        this.setName(source.getName());
        this.setOwner(source.getOwner());
        this.setContentAccessMode(source.getContentAccessMode());
        this.setType(source.getType());
        this.setUrlWeb(source.getUrlWeb());
        this.setUrlApi(source.getUrlApi());

        return this;
    }
}
