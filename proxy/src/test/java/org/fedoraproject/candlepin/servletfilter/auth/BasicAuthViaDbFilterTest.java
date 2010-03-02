package org.fedoraproject.candlepin.servletfilter.auth;


import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Properties;

import org.fedoraproject.candlepin.config.Config;
import org.junit.Before;
import org.junit.Test;


public class BasicAuthViaDbFilterTest {
    
    protected void loadSQLFile(String filename) throws Exception {
        
        BufferedReader inputStream = new BufferedReader(new FileReader( "src/test/resources/sql/" + filename));
        String line;
        Class.forName("org.hsqldb.jdbcDriver").newInstance();
        Connection conn = DriverManager.getConnection("jdbc:hsqldb:mem:unit-testing-jpa", "sa", "");
        
        while((line = inputStream.readLine()) != null ) { 
                PreparedStatement stmnt = conn.prepareStatement(line);
                stmnt.executeUpdate();
                stmnt.close();
        }
        
    }

    @Before
    public void setUp() throws Exception {
        // setup properties for test database
        Properties properties = new Config().dbBasicAuthConfiguration();
        properties.setProperty("database.query", "SELECT password FROM WEB_USER where LOGIN=?");
        properties.setProperty("database.password.column", "PASSWORD");
        properties.setProperty("database.connection.driver", "org.hsqldb.jdbcDriver");
        properties.setProperty("database.connection.username", "sa");
        properties.setProperty("database.connection.password", "");
        properties.setProperty("database.connection.url", "jdbc:hsqldb:mem:unit-testing-jpa");

        // load the table & test data
        loadSQLFile("web_user.ddl");
        loadSQLFile("web_user_data.sql");
    }

    @Test
    public void testValidUser() throws Exception {
        
    }
    
}
