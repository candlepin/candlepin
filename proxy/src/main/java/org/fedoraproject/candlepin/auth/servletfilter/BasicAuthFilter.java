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
package org.fedoraproject.candlepin.auth.servletfilter;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

import java.sql.*;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.config.Config;

import com.sun.jersey.core.util.Base64; 

import org.fedoraproject.candlepin.resource.ForbiddenException;

/**
 * BasicAuthFilter
 */
import com.google.inject.Singleton;
import com.sun.jersey.core.util.Base64;

@Singleton
public class BasicAuthFilter implements Filter {

    Logger log = Logger.getLogger(BasicAuthFilter.class);

    private FilterConfig filterConfig = null;

    public BasicAuthFilter() {
    }

    public void init(FilterConfig filterConfig) throws ServletException {
        this.filterConfig = filterConfig;
    }

    public void destroy() {
        this.filterConfig = null;
    }

    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain)
        throws IOException, ServletException {
        
        FilterChain chain) throws IOException, ServletException {

        log.debug("in basic auth filter");
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String auth = httpRequest.getHeader("authorization");
        if (auth != null && auth.toUpperCase().startsWith("BASIC ")
            && httpRequest.getMethod().equals("POST")
            && httpRequest.getRequestURI().equals("/candlepin/consumer")) {

            String userpassEncoded = auth.substring(6);
            String[] userpass = Base64.base64Decode(userpassEncoded).split(":");

            try {
                doBasicAuth(userpass[0], userpass[1]);
                request.setAttribute("username", userpass[0]);
                chain.doFilter(request, response);
            }
            catch (ForbiddenException ex) {
                httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
            }
            catch (Exception ex) {
                log.error(ex.getMessage());
                httpResponse.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
            }
        }
        chain.doFilter(request, response);
        log.debug("leaving basic auth filter");
    }

    private void doBasicAuth(String username, String password)
        throws InstantiationException, IllegalAccessException,
        ClassNotFoundException, SQLException {
        Properties properties = new Config().dbBasicAuthConfiguration();
       
        String query = properties.getProperty("database.query");
        String dbUrl = properties.getProperty("database.connection.url");
        String passwordColumn = properties.getProperty("database.password.column");
        String dbPassword = properties.getProperty("database.connection.password");
        String dbUser = properties.getProperty("database.connection.username");
        String dbDriver = properties.getProperty("database.connection.driver");
        
        Class.forName(dbDriver).newInstance();
        Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
        PreparedStatement st = conn.prepareStatement(query);
        st.setString(1, username);
        ResultSet rs = st.executeQuery();
        String passwordAssertion = null;
        while (rs.next()) {
            passwordAssertion = rs.getString(passwordColumn);
        }

        if (passwordAssertion != null && passwordAssertion.equals(password)) {
            log.info("BASIC user authenication succeeded for user: "+ username);
        }
        else {
            throw new ForbiddenException(
                "Incorrect username/password combination");
        }

        conn.close();
    }
}
