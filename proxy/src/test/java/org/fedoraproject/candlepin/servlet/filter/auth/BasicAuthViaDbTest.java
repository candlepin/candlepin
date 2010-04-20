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
package org.fedoraproject.candlepin.servlet.filter.auth;

import static org.mockito.Mockito.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;
import org.fedoraproject.candlepin.config.TestingConfiguration;
import org.fedoraproject.candlepin.model.OwnerCurator;
import org.fedoraproject.candlepin.service.impl.DatabaseUserServiceAdapter;
import org.fedoraproject.candlepin.servlet.filter.auth.BasicAuthViaUserServiceFilter;
import org.junit.After;
import org.junit.Before;

public class BasicAuthViaDbTest {

    private FilterChain defaultChain;
    private HttpServletResponse defaultResponse;
    private HttpServletRequest request;
    private BasicAuthViaUserServiceFilter filter;
    private DatabaseUserServiceAdapter userService;
    private OwnerCurator ownerCurator;
    private Connection conn;

    @Before
    public void setUp() throws Exception {
        // load the table & test data
        loadSQLFile("web_user.ddl");
        loadSQLFile("web_user_data.sql");
        // set up the default mocks that do nothing
        defaultChain = mock(FilterChain.class);
        defaultResponse = mock(HttpServletResponse.class);
        request = mock(HttpServletRequest.class);
        ownerCurator = mock(OwnerCurator.class);

        // default config values for hypersonic db
        userService = new DatabaseUserServiceAdapter(
                new TestingConfiguration("candlepin.properties"));
        filter = new BasicAuthViaUserServiceFilter(userService, ownerCurator);
        
        // default requests are POST
        when(request.getMethod()).thenReturn("POST");
    }

    @After
    public void tearDown() throws Exception {
        loadSQLFile("web_user_drop.sql");
        conn.close();
    }

    protected void loadSQLFile(String filename) throws Exception {
        BufferedReader inputStream = new BufferedReader(new FileReader(
            "src/test/resources/sql/" + filename));
        String line;
        Class.forName("org.hsqldb.jdbcDriver").newInstance();
        conn = DriverManager.getConnection("jdbc:hsqldb:mem:unit-testing-jpa",
            "sa", "");

        while ((line = inputStream.readLine()) != null) {
            PreparedStatement stmnt = conn.prepareStatement(line);
            stmnt.executeUpdate();
            stmnt.close();
        }
    }

    private String encodeUserPass(String username, String password) {
        String decoded = username + ":" + password;
        byte[] encoded = Base64.encodeBase64(decoded.getBytes());

        return new String(encoded);
    }


    /**
     *
     * Commenting out these tests because they are no longer valid for the new
     * authentication scheme.  I think that we will have to rethink how this
     * DB authentication will work, because this is no longer simple username-
     * password verification but also Owner and Role retrieval.
     *
     * I'm not removing these files, however, because they might be a nice thing
     * to include in the final product once we figure out how to "properly"
     * incorporate them.
     *
     * Also this appears to be testing both the DatabaseUserServiceAdapter
     * and the BasicAuthViaUserServiceFilter.  This should probably be modified
     * to only test the DatabaseUserServiceAdapter, as the other class is
     * already under test.
     *
     */

    /**
     * Clean use case. Should not throw an exception
     *
     * @throws Exception
     */
    /*
    @Test
    public void testValidUser() throws Exception {
        // return the correct kind of auth
        when(request.getHeader("Authorization")).thenReturn(
            "BASIC " + encodeUserPass("USER", "REDHAT"));

        when(request.getMethod()).thenReturn("POST");

        filter.doFilter(request, defaultResponse, defaultChain);
        // successful authentication puts the username attribute on the request
        verify(request).setAttribute("username", "USER");
    }

    @Test
    public void testInvalidPass() throws Exception {
        // return valid user, invalid pass
        when(request.getHeader("Authorization")).thenReturn(
            "BASIC " + encodeUserPass("CANADA", "MICRO$OFT"));
        when(request.getMethod()).thenReturn("POST");

        filter.doFilter(request, defaultResponse, defaultChain);
        verify(request, never()).setAttribute(eq("username"), anyString());
    }

    @Test
    public void testInvalidUser() throws Exception {
        // return an invalid username
        when(request.getHeader("Authorization")).thenReturn(
            "BASIC " + encodeUserPass("USE", "REDHAT"));
        when(request.getHeader("Authorization")).thenReturn(
            "BASIC " + encodeUserPass("USA", "MICRO$OFT"));
        when(request.getMethod()).thenReturn("POST");

        filter.doFilter(request, defaultResponse, defaultChain);
        verify(request, never()).setAttribute(eq("username"), anyString());
    }

    @Test
    public void testGet() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn(
            "BASIC " + encodeUserPass("USER", "REDHAT"));

        filter.doFilter(request, defaultResponse, defaultChain);
        
        verify(request).setAttribute("username", "USER");
    }
    

    @Test
    public void testInvalidDb() throws Exception {
        // return an invalid username
        when(request.getHeader("Authorization")).thenReturn(
            "BASIC " + encodeUserPass("USER", "REDHAT"));

        userService = new DatabaseUserServiceAdapter(
                new TestingConfiguration("candlepin-baddb.properties"));
        filter = new BasicAuthViaUserServiceFilter(userService, ownerCurator);

        filter.doFilter(request, defaultResponse, defaultChain);
        // regular exceptions should return a 503
        verify(defaultResponse).setStatus(HttpServletResponse.SC_BAD_GATEWAY);
    }
    */


}
