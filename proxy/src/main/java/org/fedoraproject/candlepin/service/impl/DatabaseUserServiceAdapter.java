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

import com.google.inject.Inject;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Properties;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.service.UserServiceAdapter;

/**
 *
 */
public class DatabaseUserServiceAdapter implements UserServiceAdapter{

    private String query;
    private String dbUrl;
    private String passwordColumn;
    private String dbPassword;
    private String dbUser;
    private String dbDriver;

    @Inject
    public DatabaseUserServiceAdapter(Config config) {
        Properties properties = config.dbBasicAuthConfiguration();

        this.query = properties.getProperty("database.query");
        this.dbUrl = properties.getProperty("database.connection.url");
        this.passwordColumn = properties
            .getProperty("database.password.column");
        this.dbPassword = properties
            .getProperty("database.connection.password");
        this.dbUser = properties.getProperty("database.connection.username");
        this.dbDriver = properties.getProperty("database.connection.driver");

    }

    @Override
    public boolean validateUser(String username, String password) throws Exception {
        Connection conn = null;

        try {
            Class.forName(dbDriver).newInstance();
            conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);

            PreparedStatement st = conn.prepareStatement(query);
            st.setString(1, username);
            ResultSet rs = st.executeQuery();

            String passwordAssertion = null;
            while (rs.next()) {
                passwordAssertion = rs.getString(passwordColumn);
            }
            
            return passwordAssertion != null && passwordAssertion.equals(password);
        }
        finally {
            if (conn != null) {
                conn.close();
            }
        }
        
    }

}
