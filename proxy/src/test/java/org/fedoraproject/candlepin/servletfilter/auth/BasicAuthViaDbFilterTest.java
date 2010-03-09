package org.fedoraproject.candlepin.servletfilter.auth;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.fedoraproject.candlepin.config.TestingConfiguration;
import org.junit.After;
import org.junit.Before;

import com.sun.jersey.core.util.Base64;
import org.fedoraproject.candlepin.servlet.filter.auth.BasicAuthViaDbFilter;

public class BasicAuthViaDbFilterTest {

    FilterChain defaultChain;
    HttpServletResponse defaultResponse;
    HttpServletRequest request;
    BasicAuthViaDbFilter filter;
    Connection conn;
    
    @Before
    public void setUp() throws Exception {
        // load the table & test data
        loadSQLFile("web_user.ddl");
        loadSQLFile("web_user_data.sql");
        // set up the default mocks that do nothing
        defaultChain = mock(FilterChain.class);
        defaultResponse = mock(HttpServletResponse.class);
        request = mock(HttpServletRequest.class);
        // default config values for hypersonic db
        filter = new BasicAuthViaDbFilter(new TestingConfiguration("candlepin.properties"));
        // default requests are POST
        when(request.getMethod()).thenReturn("POST");

    }
    
    @After
    public void tearDown() throws Exception { 
        loadSQLFile("web_user_drop.sql");
        conn.close();
    }
    
    protected void loadSQLFile(String filename) throws Exception {
        BufferedReader inputStream = new BufferedReader(new FileReader( "src/test/resources/sql/" + filename));
        String line;
        Class.forName("org.hsqldb.jdbcDriver").newInstance();
        conn = DriverManager.getConnection("jdbc:hsqldb:mem:unit-testing-jpa", "sa", "");
        
        while((line = inputStream.readLine()) != null ) { 
                PreparedStatement stmnt = conn.prepareStatement(line);
                stmnt.executeUpdate();
                stmnt.close();
        }
    }
    
    private String encodeUserPass(String username, String password) {
        String decoded = username + ":" + password;
        byte[] encoded = Base64.encode(decoded);
        
        return new String(encoded);
    }
    
    /**
     * Clean use case. Should not throw an exception
     * @throws Exception
     */
/*
    @Test
    public void testValidUser() throws Exception {
        // return the correct kind of auth
        when(request.getHeader("Authorization")).thenReturn("BASIC " + encodeUserPass("USER", "REDHAT"));
        
        filter.doFilter(request, defaultResponse, defaultChain);
        // successful authentication puts the username attribute on the request
        verify(request).setAttribute("username", "USER");
    }
    
    @Test
    public void testInvalidPass() throws Exception { 
        // return valid user, invalid pass
        when(request.getHeader("Authorization")).thenReturn("BASIC " + encodeUserPass("USER", "REDHA"));
        
        filter.doFilter(request, defaultResponse, defaultChain);
        // unsuccessful authentication returns a 403
        verify(defaultResponse).setStatus(HttpServletResponse.SC_FORBIDDEN);
    }
    
    @Test
    public void testInvalidUser() throws Exception { 
        // return an invalid username
        when(request.getHeader("Authorization")).thenReturn("BASIC " + encodeUserPass("USE", "REDHAT"));
        
        filter.doFilter(request, defaultResponse, defaultChain);
        // unsuccessful authentication returns a 403
        verify(defaultResponse).setStatus(HttpServletResponse.SC_FORBIDDEN);
    }
    
    @Test
    public void testGet() throws Exception { 
        when(request.getMethod()).thenReturn("GET");
        // invalid user; shouldnt matter
        when(request.getHeader("Authorization")).thenReturn("BASIC " + encodeUserPass("USE", "PASSWORD"));
        
        filter.doFilter(request, defaultResponse, defaultChain);
        // this should just pass on to the chain
        verify(defaultChain).doFilter(request,defaultResponse);
    }
    
    @Test
    public void testInvalidDb() throws Exception { 
        // return an invalid username
        when(request.getHeader("Authorization")).thenReturn("BASIC " + encodeUserPass("USER", "REDHAT"));

        filter = new BasicAuthViaDbFilter(new TestingConfiguration("candlepin-baddb.properties"));
        filter.doFilter(request, defaultResponse, defaultChain);
        // regular exceptions should return a 503
        verify(defaultResponse).setStatus(HttpServletResponse.SC_BAD_GATEWAY);
    }
 */
    
}
