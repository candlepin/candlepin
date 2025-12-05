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

import com.google.common.base.Objects;

import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;



@Entity
@Table(name = "cp_anonymous_certificates")
public class AnonymousContentAccessCertificate
    extends RevocableCertificate<AnonymousContentAccessCertificate> {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    private String id;

    @OneToOne(mappedBy = "contentAccessCert", fetch = FetchType.LAZY)
    private AnonymousCloudConsumer consumer;

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public AnonymousCloudConsumer getAnonymousCloudConsumer() {
        return consumer;
    }

    public AnonymousContentAccessCertificate setAnonymousCloudConsumer(AnonymousCloudConsumer consumer) {
        this.consumer = consumer;
        return this;
    }

    @Override
    public int hashCode() {
        return this.getId() != null ? this.getId().hashCode() : 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof AnonymousContentAccessCertificate)) {
            return false;
        }

        return Objects.equal(this.getId(), ((AnonymousContentAccessCertificate) obj).getId());
    }

}
