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

import org.hibernate.annotations.GenericGenerator;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.xml.bind.annotation.XmlRootElement;



/**
 * Represents the join table between Environment and Owner.
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
@Table(name = "cp_owner_env_content_access")
public class OwnerEnvContentAccess extends AbstractHibernateObject {

    private static final long serialVersionUID = 4551448607292211792L;

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    private String id;

    @ManyToOne
    @JoinColumn(nullable = false)
    private Owner owner;

    @ManyToOne
    @JoinColumn(nullable = true)
    private Environment environment;

    @NotNull
    @Column(name = "content_json")
    private String contentJson;

    public OwnerEnvContentAccess() {
        // Intentionally left empty
    }

    public OwnerEnvContentAccess(Owner owner, Environment environment, String contentJson) {
        this.setOwner(owner);
        this.setEnvironment(environment);
        this.setContentJson(contentJson);
    }

    public Owner getOwner() {
        return owner;
    }

    public void setOwner(Owner owner) {
        if (owner.getId() == null) {
            throw new IllegalStateException(
                "Owner must be persisted before it can be linked to an environment"
            );
        }
        this.owner = owner;
    }

    public Environment getEnvironment() {
        return environment;
    }

    public void setEnvironment(Environment environment) {
        if (environment == null) { return; }
        if (environment.getId() == null) {
            throw new IllegalStateException(
                "Environment must be persisted before it can be linked to an owner"
            );
        }
        this.environment = environment;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContentJson() {
        return this.contentJson;
    }

    public void setContentJson(String contentJson) {
        this.contentJson = contentJson;
    }
}
