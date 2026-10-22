/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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

package org.candlepin.dto.manifest.v2;

import org.candlepin.dto.TimestampedCandlepinDTO;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Mutable consumer data for version 2 manifests
 */
public class ConsumerDTO extends TimestampedCandlepinDTO<ConsumerDTO> {

    public static final long serialVersionUID = 1L;

    private String uuid;
    private String name;
    private ConsumerTypeDTO type;
    private String ownerId;
    private String urlWeb;
    private String urlApi;

    /**
     * Initializes an empty ConsumerDTO
     */
    public ConsumerDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a shallow copy of the source DTO
     *
     * @param source
     *     the DTO to copy
     */
    public ConsumerDTO(ConsumerDTO source) {
        super(source);
    }

    /**
     * Retrieves the uuid
     *
     * @return the uuid, or null if unset
     */
    public String getUuid() {
        return this.uuid;
    }

    /**
     * Sets the uuid
     *
     * @param uuid
     *     the uuid, or null to clear it
     * @return this DTO
     */
    public ConsumerDTO setUuid(String uuid) {
        this.uuid = uuid;
        return this;
    }

    /**
     * Retrieves the name
     *
     * @return the name, or null if unset
     */
    public String getName() {
        return this.name;
    }

    /**
     * Sets the name
     *
     * @param name
     *     the name, or null to clear it
     * @return this DTO
     */
    public ConsumerDTO setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Retrieves the type
     *
     * @return the type, or null if unset
     */
    public ConsumerTypeDTO getType() {
        return this.type;
    }

    /**
     * Sets the type
     *
     * @param type
     *     the type, or null to clear it
     * @return this DTO
     */
    public ConsumerDTO setType(ConsumerTypeDTO type) {
        this.type = type;
        return this;
    }

    /**
     * Retrieves the owner id
     *
     * @return the owner id, or null if unset
     */
    public String getOwnerId() {
        return this.ownerId;
    }

    /**
     * Sets the owner id
     *
     * @param ownerId
     *     the owner id, or null to clear it
     * @return this DTO
     */
    public ConsumerDTO setOwnerId(String ownerId) {
        this.ownerId = ownerId;
        return this;
    }

    /**
     * Retrieves the url web
     *
     * @return the url web, or null if unset
     */
    public String getUrlWeb() {
        return this.urlWeb;
    }

    /**
     * Sets the url web
     *
     * @param urlWeb
     *     the url web, or null to clear it
     * @return this DTO
     */
    public ConsumerDTO setUrlWeb(String urlWeb) {
        this.urlWeb = urlWeb;
        return this;
    }

    /**
     * Retrieves the url api
     *
     * @return the url api, or null if unset
     */
    public String getUrlApi() {
        return this.urlApi;
    }

    /**
     * Sets the url api
     *
     * @param urlApi
     *     the url api, or null to clear it
     * @return this DTO
     */
    public ConsumerDTO setUrlApi(String urlApi) {
        this.urlApi = urlApi;
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (!(obj instanceof ConsumerDTO that) || !super.equals(obj)) {
            return false;
        }

        return new EqualsBuilder()
            .append(this.getUuid(), that.getUuid())
            .append(this.getName(), that.getName())
            .append(this.getType() != null ? this.getType().getId() : null,
                that.getType() != null ? that.getType().getId() : null)
            .append(this.getOwnerId(), that.getOwnerId())
            .append(this.getUrlWeb(), that.getUrlWeb())
            .append(this.getUrlApi(), that.getUrlApi())
            .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(37, 7)
            .append(super.hashCode())
            .append(this.getUuid())
            .append(this.getName())
            .append(this.getType() != null ? this.getType().getId() : null)
            .append(this.getOwnerId())
            .append(this.getUrlWeb())
            .append(this.getUrlApi())
            .toHashCode();
    }

    @Override
    public ConsumerDTO populate(ConsumerDTO source) {
        super.populate(source);

        this.setUuid(source.getUuid());
        this.setName(source.getName());
        this.setType(source.getType());
        this.setOwnerId(source.getOwnerId());
        this.setUrlWeb(source.getUrlWeb());
        this.setUrlApi(source.getUrlApi());

        return this;
    }

    @Override
    public ConsumerDTO clone() {
        ConsumerDTO copy = super.clone();
        copy.setType(this.type != null ? this.type.clone() : null);
        return copy;
    }
}
