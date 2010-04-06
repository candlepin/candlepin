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
package org.fedoraproject.candlepin.servletfilter.auth;

import java.security.Principal;
import static org.mockito.Mockito.*;

import java.security.cert.X509Certificate;
import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.fedoraproject.candlepin.servlet.filter.auth.SSLAuthFilter;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class SSLAuthFilterTest {

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain chain;

    private SSLAuthFilter filter;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        this.filter = new SSLAuthFilter();
    }

    /**
     * Makes sure next filter is called if user is already set.
     *
     * @throws Exception
     */
    @Test
    public void userSet() throws Exception {
        when(request.getAttribute("username")).thenReturn("some_user");
        this.filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    /**
     * No username and no cert
     *
     * @throws Exception
     */
    @Test
    public void noUserNoCert() throws Exception {
        this.filter.doFilter(request, response, chain);

        verify(request, never()).setAttribute(eq("username"), anyString());
    }

    /**
     * Happy path - parses the username from the cert's DN correctly.
     *
     * @throws Exception
     */
    @Test
    public void correctUserName() throws Exception {
        mockCert("CN=machine_name, OU=someguy@itcenter.org, UID=453-44423-235");
        this.filter.doFilter(request, response, chain);

        verify(request).setAttribute("username", "someguy@itcenter.org");
    }

    /**
     * 403 if DN is set but does not contain OU
     *
     * @throws Exception
     */
    @Test
    public void noUserNameOnCert() throws Exception {
        mockCert("CN=something, UID=99-423-235");
        this.filter.doFilter(request, response, chain);

        verify(request, never()).setAttribute(eq("username"), anyString());
    }


    private void mockCert(String dn) {
        X509Certificate idCert =  mock(X509Certificate.class);
        Principal principal = mock(Principal.class);

        when(principal.getName()).thenReturn(dn);
        when(idCert.getSubjectDN()).thenReturn(principal);
        when(this.request.getAttribute("javax.servlet.request.X509Certificate"))
                .thenReturn(new X509Certificate[]{idCert});
    }

}
