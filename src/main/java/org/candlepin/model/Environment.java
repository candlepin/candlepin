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
package org.candlepin.model;

import org.candlepin.util.SetView;
import org.candlepin.util.Util;

import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;



/**
 * Represents an environment within an owner/organization. Environments are tracked
 * primarily so we can enable/disable/promote content in specific places.
 *
 * Not all deployments of Candlepin will make use of this table, it will at times
 * be completely empty.
 */
@Entity
@Table(name = Environment.DB_TABLE)
public class Environment extends AbstractHibernateObject<Environment> implements Serializable, Owned {

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp_environment";
    private static final long serialVersionUID = 4162471699021316341L;

    /** The maximum number of characters allowed in the name field */
    public static final int NAME_MAX_LENGTH = 255;

    /** The maximum number of characters allowed in the type field */
    public static final int TYPE_MAX_LENGTH = 32;

    /** The maximum number of characters allowed in the description field */
    public static final int DESCRIPTION_MAX_LENGTH = 255;

    /** The maximum number of characters allowed in the contentPrefix field */
    public static final int CONTENT_PREFIX_MAX_LENGTH = 255;

    @Id
    @Column(length = 32)
    @NotNull
    private String id;

    @Column(name = "owner_id", nullable = false)
    private String ownerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", insertable = false, updatable = false)
    private Owner owner;

    @Column(nullable = false)
    @Size(max = NAME_MAX_LENGTH)
    @NotNull
    private String name;

    @Column(nullable = true)
    @Size(max = TYPE_MAX_LENGTH)
    private String type;

    @Column(nullable = true)
    @Size(max = DESCRIPTION_MAX_LENGTH)
    private String description;

    @Column(name = "content_prefix", nullable = true)
    @Size(max = CONTENT_PREFIX_MAX_LENGTH)
    private String contentPrefix;

    @Column(name = "last_content_update", nullable = false)
    private Date lastContentUpdate;

    @OneToMany(mappedBy = "environment", targetEntity = EnvironmentContent.class,
        cascade = CascadeType.ALL)
    private Set<EnvironmentContent> environmentContent = new HashSet<>();

    public Environment() {
        this.syncLastContentUpdate();
    }

    public Environment(String id, String name, Owner owner) {
        this.setId(id)
            .setName(name);

        if (owner != null) {
            this.setOwner(owner);
        }

        this.syncLastContentUpdate();
    }

    /**
     * Get the environment ID.
     *
     * Note that we do not generate environment IDs as we do for most other model objects.
     * Environments are expected to be externally defined and thus we only store their
     * ID.
     *
     * @return environment ID
     */
    @Override
    public String getId() {
        return id;
    }

    public Environment setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getOwnerId() {
        return this.ownerId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getOwnerKey() {
        Owner owner = this.getOwner();
        return owner == null ? null : owner.getOwnerKey();
    }

    /**
     * Fetches the owner of this environment, if the owner ID is set. This may perform a lazy
     * lookup of the owner, and should generally be avoided if the owner ID is sufficient.
     *
     * @return
     *  The owner of this environment, if the owner ID is populated; null otherwise.
     */
    public Owner getOwner() {
        return this.owner;
    }

    public Environment setOwner(Owner owner) {
        if (owner == null || owner.getId() == null) {
            throw new IllegalArgumentException("owner is null or lacks an ID");
        }

        this.owner = owner;
        this.ownerId = owner.getId();

        return this;
    }

    public Set<EnvironmentContent> getEnvironmentContent() {
        return new SetView<>(this.environmentContent);
    }

    public Environment setEnvironmentContent(Set<EnvironmentContent> environmentContent) {
        this.environmentContent = new HashSet<>();

        if (environmentContent != null) {
            environmentContent.forEach(this::addEnvironmentContent);
        }

        return this;
    }

    public Environment addEnvironmentContent(EnvironmentContent envcontent) {
        if (envcontent == null) {
            throw new IllegalArgumentException("envcontent is null");
        }

        // Ensure it's set to this environment. Really this should be immutable and created internally.
        envcontent.setEnvironment(this);

        this.environmentContent.add(envcontent);
        return this;
    }

    public Environment addContent(Content content, boolean enabled) {
        if (content == null) {
            throw new IllegalArgumentException("content is null");
        }

        EnvironmentContent envcontent = new EnvironmentContent()
            .setEnvironment(this)
            .setContent(content)
            .setEnabled(enabled);

        this.environmentContent.add(envcontent);
        return this;
    }

    public String getName() {
        return name;
    }

    public Environment setName(String name) {
        this.name = name;
        return this;
    }

    public String getType() {
        return type;
    }

    public Environment setType(String type) {
        this.type = type != null ? type.toLowerCase() : null;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public Environment setDescription(String description) {
        this.description = description;
        return this;
    }

    public String getContentPrefix() {
        return contentPrefix;
    }

    /**
     * Sets the content prefix for this environment. Because changing the content prefix
     * requires the SCA content payloads to be re-generated with the correct paths, this method
     * also updates the environment's {@code lastContentUpdate} timestamp by calling
     * {@link #syncLastContentUpdate()}.
     *
     * @param contentPrefix the new content prefix for this environment
     * @return this {@code Environment} instance
     * @throws IllegalArgumentException if {@code contentPrefix} exceeds {@value #CONTENT_PREFIX_MAX_LENGTH}
     * characters
     */
    public Environment setContentPrefix(String contentPrefix) {
        if (contentPrefix != null && contentPrefix.length() > CONTENT_PREFIX_MAX_LENGTH) {
            throw new IllegalArgumentException("contentPrefix is too long");
        }

        this.contentPrefix = contentPrefix;
        this.syncLastContentUpdate();
        return this;
    }

    /**
     * Returns the date of the last content update for this environment.
     * If {@code lastContentUpdate} is not set, this method falls back to the environment's creation date,
     * and if that's also not available, the current date is returned.
     *
     * @return the date of the last content update, never {@code null}
     */
    public Date getLastContentUpdate() {
        return Util.firstOf(this.lastContentUpdate, this.getCreated(), new Date());
    }

    /**
     * Sets the date of the last content update for this environment.
     *
     * @param update the new last content update date; must not be {@code null}
     * @return this {@code Environment} instance
     * @throws IllegalArgumentException if {@code update} is {@code null}
     */
    public Environment setLastContentUpdate(Date update) {
        if (update == null) {
            throw new IllegalArgumentException("update is null");
        }

        this.lastContentUpdate = update;
        return this;
    }

    /**
     * Updates the {@code lastContentUpdate} field to the current date and time,
     * then returns this {@code Environment} instance.
     *
     * @return the updated {@code Environment} instance
     */
    public Environment syncLastContentUpdate() {
        this.setLastContentUpdate(new Date());
        return this;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public boolean equals(Object anObject) {
        if (this == anObject) {
            return true;
        }
        if (!(anObject instanceof Environment)) {
            return false;
        }

        Environment another = (Environment) anObject;

        return id.equals(another.getId());
    }

    @Override
    public String toString() {
        return String.format("Environment [id: %s, name: %s, owner: %s]", this.id, this.name, this.owner);
    }
}
