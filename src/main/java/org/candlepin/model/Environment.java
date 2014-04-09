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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.hibernate.annotations.ForeignKey;
import org.hibernate.annotations.Index;

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
@Table(name = "cp_environment",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"owner_id", "name"})})
public class Environment extends AbstractHibernateObject implements Serializable,
    Owned {

    @ManyToOne
    @ForeignKey(name = "fk_env_owner")
    @JoinColumn(nullable = false)
    @Index(name = "cp_env_owner_fk_idx")
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

    @OneToMany(mappedBy = "environment", targetEntity = Consumer.class)
    private List<Consumer> consumers;

    @OneToMany(mappedBy = "environment", targetEntity = EnvironmentContent.class,
        cascade = CascadeType.ALL)
    private Set<EnvironmentContent> environmentContent = new HashSet<EnvironmentContent>();


    public Environment() {
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

    public void setId(String id) {
        this.id = id;
    }

    public Owner getOwner() {
        return owner;
    }

    public void setOwner(Owner owner) {
        this.owner = owner;
    }

    public Set<EnvironmentContent> getEnvironmentContent() {
        return environmentContent;
    }

    public void setEnvironmentContent(Set<EnvironmentContent> environmentContent) {
        this.environmentContent = environmentContent;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @XmlTransient
    public List<Consumer> getConsumers() {
        return consumers;
    }

    public void setConsumers(List<Consumer> consumers) {
        this.consumers = consumers;
    }

}
