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
package org.candlepin.model;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;



/**
 * Represents the join table between Content and Owner.
 *
 * This class uses composite primary key from the two
 * entities. This strategy has been chosen so that
 * the current Candlepin schema doesn't change. However,
 * should we encounter any problems with this design,
 * there is nothing that stops us from using standard
 * uuid for the link.
 */
@Entity
@Table(name = OwnerContent.DB_TABLE)
@IdClass(OwnerContentKey.class)
public class OwnerContent implements Persisted, Serializable {

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp2_owner_content";
    private static final long serialVersionUID = -7059065874812188165L;

    /**
     * This class already maps the foreign keys.
     * Because of that we need to disallow
     * Hibernate to update database based on
     * the owner and content fields.
     */
    @ManyToOne
    @JoinColumn(updatable = false, insertable = false)
    private Owner owner;

    @ManyToOne
    @JoinColumn(updatable = false, insertable = false)
    private Content content;

    @Id
    @Column(name = "owner_id")
    private String ownerId;

    @Id
    @Column(name = "content_uuid")
    private String contentUuid;

    public OwnerContent() {
        // Intentionally left empty
    }

    public OwnerContent(Owner owner, Content content) {
        this.setOwner(owner);
        this.setContent(content);
    }

    @Override
    public Serializable getId() {
        this.applyObjectIds();
        return new OwnerContentKey(this.ownerId, this.contentUuid);
    }

    public Owner getOwner() {
        return owner;
    }

    public void setOwner(Owner owner) {
        this.owner = owner;
    }

    public Content getContent() {
        return content;
    }

    public void setContent(Content content) {
        this.content = content;
    }

    /**
     * Sets the database object IDs this join object uses to link owners to content. If either the
     * owner or content are not present or have not been persisted with a valid ID or UUID, this
     * method will throw an IllegalStateException.
     *
     * @throws IllegalStateException
     *  if either owner or content are null or unpersisted
     */
    protected void applyObjectIds() {
        if (this.owner == null) {
            throw new IllegalStateException("An owner must be specified to link content");
        }

        if (this.owner.getId() == null) {
            throw new IllegalStateException("Owner must be persisted before it can be linked to content");
        }

        if (this.content == null) {
            throw new IllegalStateException("Content must be specified to link an owner");
        }

        if (this.content.getUuid() == null) {
            throw new IllegalStateException("Content must be persisted before it can be linked to an owner");
        }

        this.ownerId = owner.getId();
        this.contentUuid = content.getUuid();
    }

    @PrePersist
    protected void onCreate() {
        this.applyObjectIds();
    }

    @PreUpdate
    protected void onUpdate() {
        this.applyObjectIds();
    }
}
