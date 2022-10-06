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

import org.candlepin.util.SetView;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;



/**
 * Represents an environment within an owner/organization. Environments are tracked
 * primarily so we can enable/disable/promote content in specific places.
 *
 * Not all deployments of Candlepin will make use of this table, it will at times
 * be completely empty.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = Environment.DB_TABLE)
public class Environment extends AbstractHibernateObject implements Serializable, Owned {

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp_environment";
    private static final long serialVersionUID = 4162471699021316341L;

    @ManyToOne
    @JoinColumn(nullable = false)
    @NotNull
    private Owner owner;

    @Column(nullable = false)
    @Size(max = 255)
    @NotNull
    private String name;

    @Column(nullable = true)
    @Size(max = 255)
    private String description;

    @Id
    @Column(length = 32)
    @NotNull
    private String id;

    @OneToMany(mappedBy = "environment", targetEntity = EnvironmentContent.class,
        cascade = CascadeType.ALL)
    private Set<EnvironmentContent> environmentContent = new HashSet<>();

    public Environment() {
        // Intentionally left empty
    }

    public Environment(String id, String name, Owner owner) {
        this.id = id;
        this.owner = owner;
        this.name = name;
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

    public Owner getOwner() {
        return owner;
    }

    /**
     * @return the owner Id of this Environment.
     */
    @Override
    public String getOwnerId() {
        return (owner == null) ? null : owner.getId();
    }

    public Environment setOwner(Owner owner) {
        this.owner = owner;
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

    public String getDescription() {
        return description;
    }

    public Environment setDescription(String description) {
        this.description = description;
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
