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

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.xml.bind.annotation.XmlTransient;

/**
 * EnvironmentContent represents the promotion of content into a particular environment.
 * When certificates are generated Candlepin will lookup the content for the given product,
 * and if the consumer is *in* an environment, it will only include content if it has
 * been promoted to that environment.
 */
@Entity
@Table(name = "cp2_environment_content")
public class EnvironmentContent extends AbstractHibernateObject {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    private String id;

    @ManyToOne
    @JoinColumn(name = "environment_id", nullable = false)
    @NotNull
    private Environment environment;

    @ManyToOne
    @JoinColumn(name = "content_uuid", nullable = false)
    @NotNull
    private Content content;

    private Boolean enabled;

    public EnvironmentContent() {
    }

    public EnvironmentContent(Environment env, Content content, Boolean enabled) {
        this.setEnvironment(env);
        this.setContent(content);
        this.setEnabled(enabled);
        env.getEnvironmentContent().add(this);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setContent(Content content) {
        this.content = content;
    }

    public Content getContent() {
        return content;
    }

    public void setEnvironment(Environment env) {
        this.environment = env;
    }

    @XmlTransient
    public Environment getEnvironment() {
        return environment;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    @Override
    public int hashCode() {
        HashCodeBuilder hcBuilder = new HashCodeBuilder(3, 23)
            .append(this.content.hashCode());
        return hcBuilder.toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other instanceof EnvironmentContent) {
            EnvironmentContent that = (EnvironmentContent) other;
            return new EqualsBuilder().append(this.enabled, that.enabled)
                .isEquals() && this.content.equals(that.content);
        }
        return false;
    }

}
