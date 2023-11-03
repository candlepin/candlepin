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

import java.io.Serializable;
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
public class Environment extends AbstractHibernateObject implements Serializable, Owned {

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp_environment";
    private static final long serialVersionUID = 4162471699021316341L;

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
    @Size(max = 255)
    @NotNull
    private String name;

    @Column
    @Size(max = 32)
    private String type;

    @Column(nullable = true)
    @Size(max = 255)
    private String description;

    @Column(name = "content_prefix")
    @Size(max = 255)
    private String contentPrefix;

    @OneToMany(mappedBy = "environment", targetEntity = EnvironmentContent.class,
        cascade = CascadeType.ALL)
    private Set<EnvironmentContent> environmentContent = new HashSet<>();

    public Environment() {
        // Intentionally left empty
    }

    public Environment(String id, String name, Owner owner) {
        this.setId(id)
            .setName(name);

        if (owner != null) {
            this.setOwner(owner);
        }
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
        if (type != null) {
            this.type = type.toLowerCase();
        }
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

    public void setContentPrefix(String contentPrefix) {
        this.contentPrefix = contentPrefix;
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
