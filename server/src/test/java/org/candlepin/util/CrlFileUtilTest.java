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
package org.candlepin.util;

import static org.candlepin.test.MatchesPattern.matchesPattern;
import static org.junit.Assert.*;

import org.candlepin.TestingModules;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.pki.PKIReader;
import org.candlepin.pki.PKIUtility;

import com.google.inject.Guice;
import com.google.inject.Injector;

import org.apache.commons.io.IOUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.math.BigInteger;
import java.net.URL;
import java.security.Provider;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.security.cert.X509CRLEntry;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;

/**
 * CrlFileUtilTest
 */
@RunWith(MockitoJUnitRunner.class)
public class CrlFileUtilTest {

    private static final Provider BC = new BouncyCastleProvider();
    private CrlFileUtil cfu;

    @Inject private PKIReader pkiReader;
    @Inject private PKIUtility pkiUtility;
    @Mock private CertificateSerialCurator certSerialCurator;
    private File temp;
    private Set<BigInteger> initialEntry;

    @Before
    public void init() throws Exception {
        Injector injector = Guice.createInjector(
            new TestingModules.MockJpaModule(),
            new TestingModules.ServletEnvironmentModule(),
            new TestingModules.StandardTest()
        );
        injector.injectMembers(this);

        this.cfu = new CrlFileUtil(this.pkiReader, this.pkiUtility, this.certSerialCurator);
        this.temp = File.createTempFile("cp_test_crl-", ".pem");
        this.initialEntry = new HashSet<BigInteger>();
        this.initialEntry.add(BigInteger.ONE);
    }

    @After
    public void tearDown() {
        temp.delete();
    }

    @Test
    public void testStripCRLFile() throws Exception {
        URL pemUrl = CrlFileUtilTest.class.getClassLoader().getResource("crl.pem");
        File pemFile = new File(pemUrl.getFile());

        File strippedFile = cfu.stripCRLFile(pemFile);
        BufferedReader r = new BufferedReader(new FileReader(strippedFile));

        String l;
        while ((l = r.readLine()) != null) {
            assertThat(l, matchesPattern("^[A-Za-z0-9+/=]*$"));
        }
        r.close();
    }

    @Test
    public void testNewCRLIsUnmodified() throws Exception {
        this.cfu.initializeCRLFile(temp, initialEntry);
        this.cfu.updateCRLFile(temp, null, null);
        assertThat(new HashSet<BigInteger>(), new ContainsSerials(temp));
    }

    @Test
    public void testNewCRLContainsRevokedSerials() throws Exception {
        Set<BigInteger> revoke = new HashSet<BigInteger>(Arrays.asList(
            new BigInteger("9711838712"),
            new BigInteger("1122402922"),
            new BigInteger("1032531028")
        ));

        this.cfu.initializeCRLFile(temp, initialEntry);
        this.cfu.updateCRLFile(temp, revoke, null);
        assertThat(revoke, new ContainsSerials(temp));
    }

    @Test
    public void testNewCRLContainsRevokedSerialsButNotUnrevokedSerials() throws Exception {
        Set<BigInteger> revoke = new HashSet<BigInteger>(Arrays.asList(
            new BigInteger("3512918537"),
            new BigInteger("1631181032"),
            new BigInteger("9136184178")
        ));

        Set<BigInteger> unrevoke = new HashSet<BigInteger>(Arrays.asList(
            new BigInteger("1235821531"),
            new BigInteger("1631181032"),
            new BigInteger("3823318120")
        ));

        this.cfu.initializeCRLFile(temp, initialEntry);
        this.cfu.updateCRLFile(temp, revoke, unrevoke);

        revoke.removeAll(unrevoke);
        assertThat(revoke, new ContainsSerials(temp));
    }

    @Test
    public void testExistingCRLIsUnmodified() throws Exception {
        Set<BigInteger> prime = new HashSet<BigInteger>(Arrays.asList(
            new BigInteger("1321822616"),
            new BigInteger("3216227128"),
            new BigInteger("2231351827")
        ));

        this.cfu.initializeCRLFile(temp, initialEntry);
        this.cfu.updateCRLFile(temp, prime, null);
        assertThat(prime, new ContainsSerials(temp));

        this.cfu.updateCRLFile(temp, null, null);
        assertThat(prime, new ContainsSerials(temp));
    }

    @Test
    public void testModifiedCRLContainsRevokedSerials() throws Exception {
        Set<BigInteger> prime = new HashSet<BigInteger>(Arrays.asList(
            new BigInteger("2358215310"),
            new BigInteger("7231352433"),
            new BigInteger("8233181205")
        ));

        Set<BigInteger> revoke = new HashSet<BigInteger>(Arrays.asList(
            new BigInteger("1132072301"),
            new BigInteger("7717218925"),
            new BigInteger("2196151762")
        ));

        this.cfu.initializeCRLFile(temp, initialEntry);
        this.cfu.updateCRLFile(temp, prime, null);
        assertThat(prime, new ContainsSerials(temp));

        this.cfu.updateCRLFile(temp, revoke, null);

        revoke.addAll(prime);
        assertThat(revoke, new ContainsSerials(temp));
    }

    @Test
    public void testModifiedCRLContainsRevokedSerialsButNotUnrevokedSerials() throws Exception {
        Set<BigInteger> prime = new HashSet<BigInteger>(Arrays.asList(
            new BigInteger("4420205175"),
            new BigInteger("2475450918"),
            new BigInteger("1501013497")
        ));

        Set<BigInteger> revoke = new HashSet<BigInteger>(Arrays.asList(
            new BigInteger("5219615176"),
            new BigInteger("2239819513"),
            new BigInteger("21822616321")
        ));

        Set<BigInteger> unrevoke = new HashSet<BigInteger>(Arrays.asList(
            new BigInteger("2239819513"),
            new BigInteger("6227128223"),
            new BigInteger("4420205175")
        ));

        this.cfu.initializeCRLFile(temp, initialEntry);
        this.cfu.updateCRLFile(temp, prime, null);
        assertThat(prime, new ContainsSerials(temp));

        this.cfu.updateCRLFile(temp, revoke, unrevoke);

        revoke.addAll(prime);
        revoke.removeAll(unrevoke);
        assertThat(revoke, new ContainsSerials(temp));
        assertFalse(new ContainsSerials(temp).matchesSafely(unrevoke));
    }

    public class ContainsSerials extends TypeSafeMatcher<Set<BigInteger>> {
        private Set<BigInteger> serials;

        public ContainsSerials(File f) {
            BufferedInputStream in = null;
            X509CRL x509crl = null;

            try {
                in = new BufferedInputStream(new FileInputStream(f));
                x509crl = (X509CRL) CertificateFactory.getInstance("X.509").generateCRL(in);
                x509crl.verify(pkiReader.getCACert().getPublicKey(), BC);
                Set<BigInteger> s = new HashSet<BigInteger>();

                for (X509CRLEntry entry : x509crl.getRevokedCertificates()) {
                    s.add(entry.getSerialNumber());
                }
                this.serials = s;
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
            finally {
                IOUtils.closeQuietly(in);
            }
        }

        public ContainsSerials(Set<BigInteger> serials) {
            this.serials = serials;
        }

        @Override
        public boolean matchesSafely(Set<BigInteger> items) {
            return serials.containsAll(items);
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("Found serials ")
                .appendValue(serials);
        }

        @Override
        public void describeMismatchSafely(Set<BigInteger> items, Description mismatchDescription) {
            mismatchDescription.appendText("are not a superset of ").appendValue(items);
        }
    }
}
