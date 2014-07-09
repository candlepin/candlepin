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

import org.hibernate.annotations.GenericGenerator;

/**
 * Represents an Content Delivery Network
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "cp_cdn",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"label"})})
public class Cdn extends AbstractHibernateObject {

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
    private CdnCertificate cert;

    public Cdn() {
        this(null, null, null, null);
    }

    public Cdn(String label, String name, String url,
        CdnCertificate cert) {
        this.label = label;
        this.name = name;
        this.url = url;
        this.cert = cert;
    }

    /**
     * @param string
     * @param string2
     * @param string3
     */
    public Cdn(String key, String name, String url) {
        this(key, name, url, null);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public CdnCertificate getCertificate() {
        return cert;
    }

    public void setCertificate(CdnCertificate cert) {
        this.cert = cert;
    }


}
