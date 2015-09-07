# Copyright (c) 2012 Red Hat, Inc.
#
# This software is licensed to you under the GNU General Public License,
# version 2 (GPLv2). There is NO WARRANTY for this software, express or
# implied, including the implied warranties of MERCHANTABILITY or FITNESS
# FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
# along with this software; if not, see
# http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
#
# Red Hat trademarks are not licensed under GPLv2. No permission is
# granted to use or replicate Red Hat trademarks that are incorporated
# in this software or its documentation.
#
import sys
import imp
import unittest
from unittest.mock import patch, call, Mock
dbtool = imp.load_source('dbtool', 'dbtool')
from io import StringIO
import os

# Order of tests: 
   #global method tests
   #tests of individual classes 
   #end-to-end unit tests of CMD tool with mocked I/O operations
class DbToolTest(unittest.TestCase):
    
    
    @patch('sys.stdout', new_callable=StringIO)
    @patch('dbtool.Popen')  
    def test_run_command_should_produce_warning(self,  m_Popen, m_stdout):
        m_Popen.return_value.returncode =1
        m_Popen.return_value.communicate.return_value=('testx','errxxx')
        (status, out) = dbtool.run_command("");
        self.assertEqual(status, 1)
        self.assertEqual(out, "testx")
        self.assertIn("WARNING",m_stdout.getvalue())
        
    
    
    @patch("os.path.exists")
    def test_classpath_for_war_should_build_cp(self, m_exists):
        war_cp = "/var/lib/tomcat/webapps/sth/WEB-INF/classes/"
        jboss_war_cp ="/var/lib/jbossas/server/production/deploy/sth.war/WEB-INF/classes/"
        def tomcat6_exists(s):
            return '/usr/sbin/tomcat' == s or war_cp==s or 'jbossas' in s
            
        m_exists.side_effect=tomcat6_exists
        self.assertIn(war_cp,dbtool.classpath_for_war("sth"));
        self.assertIn(jboss_war_cp,dbtool.classpath_for_war("sth"));
     
            
    @patch('sys.stdout')            
    @patch('dbtool.run_command')
    def test_LiquibaseWrapper_should_pass_args(self, m_run_command, m_stdout):
        m_run_command.return_value=(0,'output')
        lb = dbtool.LiquibaseWrapper('filip','verysecret','normalcp','filip.Driver','some/url', 1)
        lb.migrate("cpath")
        m_run_command.assert_called_with(['liquibase', 
                                               '--driver=filip.Driver', 
                                               '--classpath=normalcp', 
                                               '--changeLogFile=cpath', 
                                               '--url=some/url', 
                                               '--username=filip', 
                                               '--password=verysecret', 
                                               'migrate', 
                                               '-Dcommunity=True'])
    @patch('sys.stdout')            
    @patch('dbtool.run_command')
    def test_LiquibaseWrapper_fails_fast(self, m_run_command, m_stdout):
       with self.assertRaises(Exception):
           lb = dbtool.LiquibaseWrapper(None,'','','','', 1)
        
    def testing_pgi(self):
        return dbtool.PostgresInstance("filip", "aaa", "local", "1111"); 
    
    def test_PostgresInstance_jdbc_url(self):
        pgi = self.testing_pgi();
        self.assertEqual(pgi.get_jdbc_url("dd"), "jdbc:postgresql://local:1111/dd", "Generated JDBC URL was incorrect")
    
    @patch("dbtool.run_command")    
    def test_PostgresInstance_create(self,m_run_command):
        pgi = self.testing_pgi();
        m_run_command.return_value = (1, "Database already exists")
        pgi.create("ddd");    
        m_run_command.assert_called_with(['createdb', 
                                               '-U', 
                                               'filip', 
                                               '-h', 
                                               'local', 
                                               '-p', 
                                               '1111', 
                                               'ddd'])
        
    @patch("dbtool.run_command")    
    def test_PostgresInstance_create_fails_in_case_of_error(self,m_run_command):
        pgi = self.testing_pgi();
        m_run_command.return_value = (1, "Terrible fatal error")
        with self.assertRaises(Exception):
            pgi.create("ddd");       
    
    @patch("dbtool.run_command")    
    def test_PostgresInstance_drop(self,m_run_command):
        pgi = self.testing_pgi();
        m_run_command.return_value = (1, "Database already exists")
        pgi.drop("dddd");
        print("Printing all calls made to the mock")
        print(m_run_command.call_args_list)    
        m_run_command.assert_any_call(["psql", 
                                          "--username=filip"
                                          , "-c"
                                          , "SELECT pg_terminate_backend(pg_stat_activity.pid) FROM pg_stat_activity WHERE pg_stat_activity.datname = 'dddd' AND pid <> pg_backend_pid();"])
        
        
        m_run_command.assert_any_call(["dropdb", 
                                          "-U"
                                          , "filip"
                                          , "dddd"])
        
    
    def testing_ora(self):
        return dbtool.OracleInstance("orafilip", "orapass"); 
  
    def test_OracleInstance_jdbc_url(self):
        ora = self.testing_ora();
        self.assertEqual(ora.get_jdbc_url("db"), "jdbc:oracle:thin:@//db");
    
    @patch("dbtool.run_command")     
    def test_OracleInstance_runSql(self,m_run_command):
        ora = self.testing_ora();
        m_run_command.return_value= (0, "no problem!")
        ora.runSql("oradb", "some long sql")
        m_run_command.assert_called_with(['sqlplus', '-S', 'orafilip/orapass@//oradb as sysdba'], "some long sql")
        
    @patch("dbtool.run_command")     
    def test_OracleInstance_runSql_error(self,m_run_command):
        ora = self.testing_ora();
        m_run_command.return_value = (0, "big big ERROR")
        with self.assertRaises(Exception):
            ora.runSql("oradb", "some long sql")


    @patch("dbtool.OracleInstance.runSql")             
    def test_OracleInstance_create(self,m_run_sql):
        ora = self.testing_ora();
        m_run_sql.return_value = ""
        ora.create("dbtocreate")
        m_run_sql.assert_any_call("select 'user exists' from all_users where username='DBTOCREATE';")
        m_run_sql.assert_any_call("create user dbtocreate identified by orapass default tablespace users;")
        m_run_sql.assert_any_call("grant dba to dbtocreate;")
        
    @patch("dbtool.OracleInstance.runSql")             
    def test_OracleInstance_create_fails(self,m_run_sql):
        ora = self.testing_ora();
        m_run_sql.return_value = "user exists"
        ora.create("dbtocreate")
        m_run_sql.assert_called_once_with("select 'user exists' from all_users where username='DBTOCREATE';")
        
    @patch("dbtool.OracleInstance.runSql")      
    def test_OracleInstance_drop(self,m_run_sql):
        ora = self.testing_ora();
        ora.drop("dbtodrop")
        m_run_sql.assert_called_with("drop user dbtodrop cascade;")           
                  
    
    @patch("dbtool.run_command")      
    def test_MysqlInstance_drop(self,m_run_command):
        mysql = dbtool.MysqlInstance("filip","secret")
        mysql.drop("dbtodrop")
        m_run_command.assert_called_with(["mysqladmin", "--user=filip", "--password=secret", "--force", "drop", "dbtodrop"])    
    
    
    def product_mock(self):
        cmock = Mock();
        cmock.classpath.return_value = "some cp"
        cmock.create_changelog_location.return_value = "createclog"
        cmock.default_db_name.return_value = "defaultDbName"
        return cmock
   
    def dbinstance_mock(self):
        dbmock = Mock();
        dbmock.get_jdbc_classpath.return_value="jdbccp"
        dbmock.get_driver_class.return_value="dbinstance.driver"
        dbmock.get_jdbc_url.return_value="dbinstance.jdbcurl"
        return dbmock
        
    @patch("dbtool.LiquibaseWrapper")
    @patch("dbtool.PostgresInstance")
    @patch("dbtool.Candlepin")  
    def test_tool_cp_postgres_success(self, m_candlepin,  m_postgres_instance, m_liquibase_wrapper):
        m_candlepin.return_value = self.product_mock();
        m_postgres_instance.return_value = self.dbinstance_mock();
        
        dbtool.main(["--pg-dbhost", "pghost", "--pg-dbport", "pgdbport" ,"-d","candledb","--product", "candlepin", "-u", "cp","-p", "secret", "--action", "create"])
        m_postgres_instance.assert_called_with("cp", "secret","pghost","pgdbport")
        m_postgres_instance.return_value.create.assert_called_with("candledb")
        m_liquibase_wrapper.assert_called_with("cp", "secret", "some cp:jdbccp", "dbinstance.driver", "dbinstance.jdbcurl", False)
        m_liquibase_wrapper.return_value.migrate.assert_called_with("createclog");

        
    @patch("dbtool.LiquibaseWrapper")
    @patch("dbtool.PostgresInstance")
    @patch("dbtool.Candlepin.classpath")  
    def test_tool_cp_assigns_correct_defaults(self, m_candlepin_classpath,  m_postgres_instance, m_liquibase_wrapper):
        m_candlepin_classpath.return_value = "some cp"
        m_postgres_instance.return_value = self.dbinstance_mock();
        equivalent_arg_lists = [
                     ["--product", "candlepin", "--action", "create"],
                     ["--product", "candlepin", "--action", "create", "-d", "candlepin", "-u", "candlepin"]
                    ]
        
        for arguments in equivalent_arg_lists:
            m_postgres_instance.reset_mock();
            m_liquibase_wrapper.reset_mock();            
            dbtool.main(arguments)
            m_postgres_instance.assert_called_with("candlepin", None,None,None)
            m_postgres_instance.return_value.create.assert_called_with("candlepin")
            m_liquibase_wrapper.assert_called_with("candlepin", None, "some cp:jdbccp", "dbinstance.driver", "dbinstance.jdbcurl", False)
            m_liquibase_wrapper.return_value.migrate.assert_called_with("db/changelog/changelog-create.xml");


    @patch("dbtool.LiquibaseWrapper")
    @patch("dbtool.OracleInstance")
    @patch("dbtool.Candlepin")  
    def test_tool_cp_oracle_success(self, m_candlepin,  m_oracle_instance, m_liquibase_wrapper):
        m_candlepin.return_value = self.product_mock();
        m_oracle_instance.return_value= self.dbinstance_mock();
        
        dbtool.main(["--oracle-user", "orauser", "--database-type", "Oracle", "--oracle-password", "orapass" ,"-d","candlepindb","--product", "candlepin", "-u", "candlepinuser","-p", "secret", "--action", "create"])
        m_oracle_instance.assert_called_with("orauser", "orapass")
        m_oracle_instance.return_value.create.assert_called_with("candlepindb")
        m_liquibase_wrapper.assert_called_with("candlepinuser", "secret", "some cp:jdbccp", "dbinstance.driver", "dbinstance.jdbcurl", False)
        m_liquibase_wrapper.return_value.migrate.assert_called_with("createclog");


    @patch("dbtool.LiquibaseWrapper")
    @patch("dbtool.PostgresInstance")
    @patch("dbtool.Gutterball")  
    def test_tool_gt_postgres_success(self, m_gutterball,  m_postgres_instance, m_liquibase_wrapper):
        m_gutterball.return_value = self.product_mock();        
        m_postgres_instance.return_value = self.dbinstance_mock();
        
        dbtool.main(["--pg-dbhost", "pghost", "--pg-dbport", "pgdbport" ,"--product", "gutterball", "-u", "gutuser","-p", "secret", "--action", "create"])
        m_postgres_instance.assert_called_with("gutuser", "secret","pghost","pgdbport")
        m_postgres_instance.return_value.create.assert_called_with("defaultDbName")
        m_liquibase_wrapper.assert_called_with("gutuser", "secret", "some cp:jdbccp", "dbinstance.driver", "dbinstance.jdbcurl", False)
        m_liquibase_wrapper.return_value.migrate.assert_called_with("createclog");
    
    @patch("dbtool.LiquibaseWrapper")
    @patch("dbtool.PostgresInstance")
    @patch("dbtool.Gutterball.classpath")  
    def test_tool_gt_assigns_correct_defaults(self, m_gutterball_classpath,  m_postgres_instance, m_liquibase_wrapper):
        m_gutterball_classpath.return_value = "some cp"       
        m_postgres_instance.return_value = self.dbinstance_mock();
        equivalent_arg_lists = [
                     ["--product", "gutterball", "--action", "create"],
                     ["--product", "gutterball", "--action", "create", "-u", "gutterball", "-d","gutterball"]
                    ]

        for arguments in equivalent_arg_lists:
            m_postgres_instance.reset_mock();
            m_liquibase_wrapper.reset_mock();  
            dbtool.main(arguments)
            m_postgres_instance.assert_called_with("gutterball", None,None,None)
            m_postgres_instance.return_value.create.assert_called_with("gutterball")
            m_liquibase_wrapper.assert_called_with("gutterball", None, "some cp:jdbccp", "dbinstance.driver", "dbinstance.jdbcurl", False)
            m_liquibase_wrapper.return_value.migrate.assert_called_with( "db/changelog/changelog.xml");
    
    def test_bad_arg_list_should_raise_exception(self):
        with self.assertRaisesRegex(Exception, "You must specify product using --product switch"):
            dbtool.main([])
        
        with self.assertRaisesRegex(Exception, "You must specify action using --action switch"):
            dbtool.main(["--product", "candlepin"])
        
        invalid_oracle_arglists=[["--product", "candlepin", "--action", "create","--database-type", "Oracle","--oracle-user", "XX"],
                                 ["--product", "candlepin", "--action", "create","--database-type", "Oracle"]]
        
        for invalid_oracle_args in invalid_oracle_arglists:
            with self.assertRaisesRegex(Exception, "When using Oracle database, you must specify --oracle-password, possibly --oracle-user"):
                dbtool.main(invalid_oracle_args)    
    
    @patch("dbtool.LiquibaseWrapper")
    @patch("dbtool.PostgresInstance")
    @patch("dbtool.Candlepin.classpath")  
    def test_tool_classpath_switch_adds_custom(self, m_candlepin_classpath,  m_postgres_instance, m_liquibase_wrapper):
        m_candlepin_classpath.return_value = ""       
        m_postgres_instance.return_value = self.dbinstance_mock();
        dbtool.main(["--product", "candlepin", "--action", "create", "--classpath", "additionalcp1:additionalcp2"])
        m_liquibase_wrapper.assert_called_with("candlepin", None, "additionalcp1:additionalcp2:jdbccp", "dbinstance.driver", "dbinstance.jdbcurl", False)
    
    @patch("dbtool.LiquibaseWrapper")
    @patch("dbtool.PostgresInstance")
    @patch("dbtool.Candlepin.classpath")  
    def test_tool_classpath_machine_empty_should_not_fail(self, m_candlepin_classpath,  m_postgres_instance, m_liquibase_wrapper):
        m_candlepin_classpath.return_value = ""       
        m_postgres_instance.return_value = self.dbinstance_mock();
        m_postgres_instance.return_value .get_jdbc_classpath.return_value=""
        dbtool.main(["--product", "candlepin", "--action", "create", "--classpath", "additionalcp1:additionalcp2"])
        m_liquibase_wrapper.assert_called_with("candlepin", None, "additionalcp1:additionalcp2", "dbinstance.driver", "dbinstance.jdbcurl", False)
    
    @patch("dbtool.LiquibaseWrapper")
    @patch("dbtool.PostgresInstance")
    @patch("dbtool.Candlepin.classpath")  
    def test_tool_liquibase_should_fail_without_any_cp(self, m_candlepin_classpath,  m_postgres_instance, m_liquibase_wrapper):
        m_candlepin_classpath.return_value = ""       
        m_postgres_instance.return_value = self.dbinstance_mock();
        m_postgres_instance.return_value .get_jdbc_classpath.return_value=""
        with self.assertRaisesRegex(Exception, "Empty classpath on this machine. At least supply classpath using --classpath"):
            dbtool.main(["--product", "candlepin", "--action", "create"])

if __name__ == "__main__":
   suite = unittest.TestLoader().loadTestsFromTestCase(DbToolTest)
   unittest.TextTestRunner(verbosity=2).run(suite)
   sys.exit(0)
   