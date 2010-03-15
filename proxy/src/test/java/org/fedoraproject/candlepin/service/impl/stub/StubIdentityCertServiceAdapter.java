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
package org.fedoraproject.candlepin.service.impl.stub;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerIdentityCertificate;
import org.fedoraproject.candlepin.service.IdentityCertServiceAdapter;

public class StubIdentityCertServiceAdapter implements IdentityCertServiceAdapter {

    @Override
    public ConsumerIdentityCertificate generateIdentityCert(Consumer consumer,
            String username) {
        ConsumerIdentityCertificate idCert = new ConsumerIdentityCertificate();

        // totally arbitrary
        idCert.setId(43L);
        idCert.setKey("uh0876puhapodifbvj094".getBytes());
        idCert.setPem("hpj-08ha-w4gpoknpon*)&^%#".getBytes());

        return idCert;
    }

    /* (non-Javadoc)
     * @see org.fedoraproject.candlepin.service.IdentityCertServiceAdapter#
     * deleteIdentityCert(org.fedoraproject.candlepin.model.Consumer)
     */
    @Override
    public void deleteIdentityCert(Consumer consumer) {
        //No Op.  Pretend to delete the cert
    }

}

