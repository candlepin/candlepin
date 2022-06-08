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
package org.candlepin.resource;

import org.candlepin.model.CertificateCurator;

import com.google.inject.Inject;

import java.util.List;
import java.util.Objects;

public class CrlResource implements CrlApi {

    private final CertificateCurator certificateCurator;

    @Inject
    public CrlResource(CertificateCurator certificateCurator) {
        this.certificateCurator = Objects.requireNonNull(certificateCurator);
    }

    @Override
    public List<Long> getCurrentCrl() {
        return this.certificateCurator.getNonExpiredRevokedSerials();
    }

}
