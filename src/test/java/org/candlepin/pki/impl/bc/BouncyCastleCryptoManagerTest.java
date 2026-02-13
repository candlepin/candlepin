/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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
package org.candlepin.pki.impl.bc;

import org.candlepin.config.Configuration;
import org.candlepin.pki.CertificateReader;
import org.candlepin.pki.CryptoManager;
import org.candlepin.pki.CryptoManagerTest;
import org.candlepin.pki.PrivateKeyReader;
import org.candlepin.pki.SchemeReader;
import org.candlepin.pki.SubjectKeyIdentifierWriter;
import org.candlepin.test.CryptoUtil;



/**
 * PKR test suite for the Bouncy Castle security provider
 */
public class BouncyCastleCryptoManagerTest extends CryptoManagerTest {

    private static final BouncyCastleSecurityProvider SECURITY_PROVIDER_PROVIDER =
        new BouncyCastleSecurityProvider();

    @Override
    protected CryptoManager buildCryptoManager(Configuration config) {
        CertificateReader certReader = CryptoUtil.getCertificateReader();
        PrivateKeyReader keyReader = CryptoUtil.getPrivateKeyReader();
        SchemeReader schemeReader = new SchemeReader(config, keyReader, certReader);
        SubjectKeyIdentifierWriter skiWriter = new BouncyCastleSubjectKeyIdentifierWriter();

        return new BouncyCastleCryptoManager(config, SECURITY_PROVIDER_PROVIDER.get(), schemeReader,
            certReader, skiWriter);
    }

}
