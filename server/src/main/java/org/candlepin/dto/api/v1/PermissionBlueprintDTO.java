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
package org.candlepin.dto.api.v1;

import org.candlepin.dto.TimestampedCandlepinDTO;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;



/**
 * DTO representing the permission blueprints exposed to the API layer.
 *
 * <pre>
 *  {
 *    "id": "string",
 *    "owner": {
 *      "id": "string",
 *      "key": "string",
 *      "displayName": "string",
 *      "href": "string"
 *    },
 *    "access": "NONE",
 *    "type": "OWNER",
 *    "created": "2018-04-26T13:46:11.190Z",
 *    "updated": "2018-04-26T13:46:11.190Z"
 *  }
 * </pre>
 */
@ApiModel(parent = TimestampedCandlepinDTO.class, description = "User information for a given user")
public class PermissionBlueprintDTO extends TimestampedCandlepinDTO<PermissionBlueprintDTO> {

    @ApiModelProperty(required = true, example = "ff808081554a3e4101554a3e9033005d")
    protected String id;
    @ApiModelProperty(required = true)
    protected OwnerDTO owner;
    @ApiModelProperty(required = true, example = "OWNER")
    protected String type;
    @ApiModelProperty(required = true, example = "NONE")
    protected String access;

    /**
     * Initializes a new PermissionBlueprintDTO instance with null values.
     */
    public PermissionBlueprintDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a new PermissionBlueprintDTO instance which is a shallow copy of the provided
     * source entity.
     *
     * @param source
     *  The source entity to copy
     */
    public PermissionBlueprintDTO(PermissionBlueprintDTO source) {
        super(source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PermissionBlueprintDTO populate(PermissionBlueprintDTO source) {
        super.populate(source);

        this.setId(source.getId());
        this.setOwner(source.getOwner());
        this.setType(source.getType());
        this.setAccess(source.getAccess());

        return this;
    }

    /**
     * Fetches this permission's ID. If the ID has not been set, this method returns null.
     *
     * @return
     *  This permission's ID, or null if the ID has not been set
     */
    public String getId() {
        return this.id;
    }

    /**
     * Sets or clears the ID for this permission.
     *
     * @param id
     *  The ID to set for this permission, or null to clear any previously set ID
     *
     * @return
     *  a reference to this DTO
     */
    public PermissionBlueprintDTO setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * Fetches this permission's owner. If the owner has not been set, this method returns null.
     *
     * @return
     *  This permission's owner, or null if the owner has not been set
     */
    public OwnerDTO getOwner() {
        return this.owner;
    }

    /**
     * Sets or clears the owner for this permission.
     *
     * @param owner
     *  The owner to set for this permission, or null to clear any previously set owner
     *
     * @return
     *  a reference to this DTO
     */
    public PermissionBlueprintDTO setOwner(OwnerDTO owner) {
        this.owner = owner;
        return this;
    }

    /**
     * Fetches this permission's type. If the type has not been set, this method returns null.
     *
     * @return
     *  This permission's type, or null if the type has not been set
     */
    public String getType() {
        return this.type;
    }

    /**
     * Sets or clears the type for this permission.
     *
     * @param type
     *  The type to set for this permission, or null to clear any previously set type
     *
     * @return
     *  a reference to this DTO
     */
    public PermissionBlueprintDTO setType(String type) {
        this.type = type;
        return this;
    }

    /**
     * Fetches the access set for this permission. If the access hasn't been set, this method returns
     * null.
     *
     * @return
     *  The access set for this permission, or null if the access hasn't been set.
     */
    public String getAccess() {
        return this.access;
    }

    /**
     * Sets or clears the access level provided by this permission.
     *
     * @param access
     *  The access level to set for this permission, or null to clear any previously set access
     *  level
     *
     * @return
     *  a reference to this DTO
     */
    public PermissionBlueprintDTO setAccess(String access) {
        this.access = access;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof PermissionBlueprintDTO && super.equals(obj)) {
            PermissionBlueprintDTO that = (PermissionBlueprintDTO) obj;

            OwnerDTO thisOwner = this.getOwner();
            OwnerDTO thatOwner = that.getOwner();
            String thisOwnerId = thisOwner != null ? thisOwner.getId() : null;
            String thatOwnerId = thatOwner != null ? thatOwner.getId() : null;

            EqualsBuilder builder = new EqualsBuilder()
                .append(thisOwnerId, thatOwnerId)
                .append(this.getId(), that.getId())
                .append(this.getType(), that.getType())
                .append(this.getAccess(), that.getAccess());

            return builder.isEquals();
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        OwnerDTO owner = this.getOwner();

        HashCodeBuilder builder = new HashCodeBuilder(37, 7)
            .append(super.hashCode())
            .append(this.getId())
            .append(owner != null ? owner.getId() : null)
            .append(this.getType())
            .append(this.getAccess());

        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        OwnerDTO owner = this.getOwner();

        return String.format("PermissionBlueprintDTO [id: %s, owner id: %s, type: %s, access: %s]",
            this.getId(), owner != null ? owner.getId() : null, this.getType(), this.getAccess());
    }
}
