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
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlRootElement;



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
@XmlRootElement
@Entity
@Table(name = "cp2_owner_content")
@IdClass(OwnerContentKey.class)
public class OwnerContent implements Persisted, Serializable {
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
        return new OwnerContentKey(this.ownerId, this.contentUuid);
    }

    public Owner getOwner() {
        return owner;
    }

    public void setOwner(Owner owner) {
        if (owner.getId() == null) {
            throw new IllegalStateException(
                "Owner must be persisted before it can be linked to a content"
            );
        }

        this.owner = owner;
        this.ownerId = owner.getId();
    }

    public Content getContent() {
        return content;
    }

    public void setContent(Content content) {
        if (content.getUuid() == null) {
            throw new IllegalStateException(
                "Content must be persisted before it can be linked to an owner"
            );
        }

        this.content = content;
        this.contentUuid = content.getUuid();
    }

}
