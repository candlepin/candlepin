/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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

import org.hibernate.annotations.DiscriminatorFormula;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;



/**
 * ContentOverride abstract class to share code between different override types, consumer and
 * activation key for now
 *
 * @param <T>
 *  The type of this content override
 *
 * @param <P>
 *  The type of the parent or containing entity
 */
@Entity
@Table(name = ContentOverride.DB_TABLE)
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorFormula("CASE WHEN key_id IS NOT NULL THEN 'activation_key' " +
    "WHEN environment_id IS NOT NULL THEN 'environment' " +
    "ELSE 'consumer' END")
public abstract class ContentOverride<T extends ContentOverride, P extends AbstractHibernateObject> extends
    AbstractHibernateObject<T> {

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp_content_override";
    public static final int MAX_NAME_AND_LABEL_LENGTH = 255;
    public static final int MAX_VALUE_LENGTH = 2048;

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    private String id;

    @Column(name = "content_label")
    @Size(max = MAX_NAME_AND_LABEL_LENGTH)
    @NotNull
    private String contentLabel;

    @Size(max = 255)
    @NotNull
    private String name;

    @Size(max = MAX_VALUE_LENGTH)
    @NotNull
    private String value;

    public ContentOverride() {
        // Intentionally left empty
    }

    public String getId() {
        return id;
    }

    public T setId(String id) {
        this.id = id;
        return (T) this;
    }

    public T setContentLabel(String contentLabel) {
        this.contentLabel = contentLabel;
        return (T) this;
    }

    public String getContentLabel() {
        return contentLabel;
    }

    public T setName(String name) {
        this.name = name != null ? name.toLowerCase() : null;
        return (T) this;
    }

    public String getName() {
        return name;
    }

    public T setValue(String value) {
        this.value = value;
        return (T) this;
    }

    public String getValue() {
        return value;
    }

    public abstract P getParent();

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("ContentOverride [content: %s, name: %s, value: %s]",
            this.getContentLabel(), this.getName(), this.getValue());
    }
}
