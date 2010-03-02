/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.service.impl;

import org.fedoraproject.candlepin.model.ClientCertificateStatus;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.service.CertificateServiceAdapter;

import java.util.Date;

/**
 * DefaultCertificateServiceAdapter
 */
public class DefaultCertificateServiceAdapter implements
    CertificateServiceAdapter {
    
    @Override
    public ClientCertificateStatus generateEntitlementCert(Consumer consumer,
        Subscription sub, Product product, Date endDate) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ClientCertificateStatus generateIdentityCert(Consumer consumer) {
        // TODO Auto-generated method stub
        ClientCertificateStatus certStatus =  new ClientCertificateStatus();
        return certStatus;
    }

}
