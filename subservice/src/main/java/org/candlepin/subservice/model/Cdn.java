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
package org.candlepin.subservice.model;

import org.candlepin.model.AbstractHibernateObject;

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
@Table(name = "cps_cdn")
public class Cdn extends AbstractHibernateObject {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    protected String id;

    @Column(nullable = false)
    @Size(max = 255)
    @NotNull
    protected String label;

    @Column(nullable = false)
    @Size(max = 255)
    @NotNull
    protected String name;

    @Column(nullable = false)
    @Size(max = 255)
    @NotNull
    protected String url;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "certificate_id")
    protected CdnCertificate cert;

    public Cdn() {

    }

    // TODO:
    // Add convenience constructors



    public String getId() {
        return this.id;
    }

    public Cdn setId(String id) {
        this.id = id;
        return this;
    }

    public String getLabel() {
        return this.label;
    }

    public Cdn setLabel(String label) {
        this.label = label;
        return this;
    }

    public String getName() {
        return this.name;
    }

    public Cdn setName(String name) {
        this.name = name;
        return this;
    }

    public String getUrl() {
        return this.url;
    }

    public Cdn setUrl(String url) {
        this.url = url;
        return this;
    }

    public CdnCertificate getCertificate() {
        return this.cert;
    }

    public Cdn setCertificate(CdnCertificate cert) {
        this.cert = cert;
        return this;
    }

    public org.candlepin.model.Cdn toCandlepinModel() {
        org.candlepin.model.Cdn output = new org.candlepin.model.Cdn();

        output.setId(this.getId());
        output.setLabel(this.getLabel());
        output.setName(this.getName());
        output.setUrl(this.getUrl());

        CdnCertficiate cert = this.getCertificate();
        output.setCertificate(cert != null ? cert.toCandlepinModel() : null);

        output.setCreated(this.getCreated());
        output.setUpdated(this.getUpdated());

        return output;
    }

}
