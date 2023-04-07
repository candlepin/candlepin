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

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

// TODO: This should be made to be immutable.


/**
 * EnvironmentContent represents the promotion of content into a particular environment.
 * When certificates are generated Candlepin will lookup the content for the given product,
 * and if the consumer is *in* an environment, it will only include content if it has
 * been promoted to that environment.
 */
@Entity
@Table(name = EnvironmentContent.DB_TABLE)
public class EnvironmentContent extends AbstractHibernateObject {

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp_environment_content";

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    private String id;

    @Column(name = "environment_id", nullable = false)
    private String environmentId;

    // Convenience value for performing lazy environment lookups off this object, but will not be
    // pulled until fetched via getEnvironment; should be avoided to reduce the frequency of N+1
    // perf issues
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "environment_id", updatable = false, insertable = false)
    private Environment environment;

    @Column(name = "content_id", nullable = false)
    private String contentId;

    private Boolean enabled;

    public EnvironmentContent() {
        // Intentionally left empty
    }

    public String getId() {
        return id;
    }

    public EnvironmentContent setId(String id) {
        this.id = id;
        return this;
    }

    public String getEnvironmentId() {
        return this.environmentId;
    }

    /**
     * Fetches the environment this EnvironmentContent instance belongs to, if the environment ID
     * has been set. This may perform a lazy lookup of the environment, and should generally be
     * avoided in cases where the environment ID is sufficient.
     *
     * @return
     *  the environment for this environment content, or null if the environment has not yet been
     *  set
     */
    public Environment getEnvironment() {
        return environment;
    }

    /**
     * Sets the environment for this environment content. The environment cannot be null and must
     * have a valid ID.
     *
     * @param environment
     *  the environment with which to associate this environment content
     *
     * @return
     *  a reference to this environment content instance
     */
    public EnvironmentContent setEnvironment(Environment environment) {
        if (environment == null || environment.getId() == null) {
            throw new IllegalArgumentException("environment is null or lacks an ID");
        }

        this.environment = environment;
        this.environmentId = environment.getId();

        return this;
    }

    public String getContentId() {
        return this.contentId;
    }

    public EnvironmentContent setContentId(String contentId) {
        this.contentId = contentId;
        return this;
    }

    public EnvironmentContent setContent(Content content) {
        if (content == null || content.getId() == null) {
            throw new IllegalArgumentException("content is null or lacks an ID");
        }

        this.contentId = content.getId();
        return this;
    }

    /**
     * Returns a boolean value indicating the content's enablement if the default enablement has
     * been overridden in this environment. If the content's enablement should use the default set
     * on the product, this method returns null.
     *
     * @return
     *  a boolean value indicating the content's enablement if overridden in the environment; null
     *  if it should use the content's default enablement from the product
     */
    public Boolean getEnabled() {
        return this.enabled;
    }

    public EnvironmentContent setEnabled(Boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(3, 23)
            .append(this.id)
            .toHashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof EnvironmentContent)) {
            return false;
        }

        EnvironmentContent that = (EnvironmentContent) obj;

        return new EqualsBuilder()
            .append(this.getEnvironmentId(), that.getEnvironmentId())
            .append(this.getContentId(), that.getContentId())
            .append(this.getEnabled(), that.getEnabled())
            .isEquals();
    }

    @Override
    public String toString() {
        return String.format("EnvironmentContent [environment: %s, content: %s, enabled: %s]",
            this.getEnvironmentId(), this.getContentId(), this.getEnabled());
    }

}
