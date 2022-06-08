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

import org.candlepin.service.model.CdnInfo;

import org.hibernate.annotations.GenericGenerator;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;



/**
 * Represents an Content Delivery Network
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = Cdn.DB_TABLE, uniqueConstraints = {@UniqueConstraint(columnNames = {"label"})})
public class Cdn extends AbstractHibernateObject implements CdnInfo {

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp_cdn";

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    private String id;

    @Column(nullable = false)
    @Size(max = 255)
    @NotNull
    private String label;

    @Column(nullable = false)
    @Size(max = 255)
    @NotNull
    private String name;

    @Column(nullable = false)
    @Size(max = 255)
    @NotNull
    private String url;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "certificate_id")
    private Certificate cert;

    public Cdn() {
        // Intentionally left empty
    }

    /**
     * @param label
     * @param name
     * @param url
     */
    // public Cdn(String label, String name, String url) {
    //     this(label, name, url, null);
    // }

    // public Cdn(String label, String name, String url, Certificate cert) {
    //     this.label = label;
    //     this.name = name;
    //     this.url = url;
    //     this.cert = cert;
    // }

    public String getId() {
        return id;
    }

    public Cdn setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getLabel() {
        return label;
    }

    public Cdn setLabel(String label) {
        this.label = label;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return name;
    }

    public Cdn setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUrl() {
        return url;
    }

    public Cdn setUrl(String url) {
        this.url = url;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Certificate getCertificate() {
        return cert;
    }

    public Cdn setCertificate(Certificate cert) {
        this.cert = cert;
        return this;
    }

    public String toString() {
        return String.format(
            "Cdn [id=%s, name=%s, label=%s, url=%s]",
            this.getId(), this.getName(), this.getLabel(), this.getUrl()
        );
    }
}
