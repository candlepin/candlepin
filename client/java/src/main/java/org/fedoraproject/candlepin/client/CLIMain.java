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
package org.fedoraproject.candlepin.client;

import static java.lang.System.getProperty;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.Security;
import java.util.HashMap;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang.StringUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.fedoraproject.candlepin.client.cmds.BaseCommand;
import org.fedoraproject.candlepin.client.cmds.HelpCommand;
import org.fedoraproject.candlepin.client.cmds.ListCommand;
import org.fedoraproject.candlepin.client.cmds.RegisterCommand;
import org.fedoraproject.candlepin.client.cmds.SubscribeCommand;
import org.fedoraproject.candlepin.client.cmds.UnRegisterCommand;
import org.fedoraproject.candlepin.client.cmds.UnSubscribeCommand;
import org.fedoraproject.candlepin.client.cmds.Utils;
/**
 * ClientMain
 */
public class CLIMain {
    /**
     *
     */
    private HashMap<String, BaseCommand> cmds = new HashMap<String, BaseCommand>();
    private Configuration config;


    public CLIMain() {
        registerCommands();
    }

    @SuppressWarnings("unchecked")
    protected void registerCommands() {
        // First, create the client we will need to use
        try {
            Class<? extends BaseCommand>[] commands = new Class[]{
                RegisterCommand.class, ListCommand.class,
                SubscribeCommand.class, UnSubscribeCommand.class,
                UnRegisterCommand.class };
            for (Class cmdClass : commands) {
                BaseCommand cmd = (BaseCommand) cmdClass.newInstance();
                cmds.put(cmd.getName(), cmd);
            }
            // Now add the help command
            cmds.put("help", new HelpCommand(cmds));
        }
        catch (Exception e) {
            throw new ClientException(e);
        }
    }

    protected BaseCommand getCommand(String[] args) {
        // Get the first item which does not start with a -
        // and assume it is the module name
        BaseCommand cmd = null;
        for (String arg : args) {
            if (!arg.startsWith("-")) {
                if (cmds.containsKey(arg)) {
                    cmd = cmds.get(arg);
                    break;
                }
            }
        }
        return cmd;
    }

    protected void execute(String[] args) {
        BaseCommand cmd = this.getCommand(args);
        if (cmd == null) {
            System.out.println("No command was specified");
            cmd = cmds.get("help");
        }
        try {
            CommandLine cmdLine = cmd.getCommandLine(args);
            if (cmdLine.hasOption("h")) {
                cmd.generateHelp();
                return;
            }
            this.config = loadConfig(cmdLine);
            System.setProperty("javax.net.ssl.trustStore",
                config.getKeyStoreFileLocation());
            cmd.setClient(new CandlepinConsumerClient(this.config));
            cmd.execute(cmdLine);
        }
        catch (ParseException e) {
            cmd.generateHelp();
        }
        catch (RuntimeException e) {
            this.handleClientException(e, cmd);
        }
    }

    private Configuration loadConfig(CommandLine cmdLine) {
        Properties pr = Utils.getDefaultProperties();
        try {
            String loc = cmdLine.getOptionValue("cfg");
            File file = new File(StringUtils.defaultIfEmpty(loc,
                Constants.DEFAULT_CONF_LOC));
            //config file exists
            if (file.exists() && file.canRead() && !file.isDirectory()) {
                Properties conf = new Properties();
                FileInputStream inputStream = new FileInputStream(file);
                conf.load(inputStream);
                pr = conf;
            }
            else {
                /* config file not found. Try getting values from
                 * from system environment*/
                tryStoringSystemProperty(pr, Constants.SERVER_URL_KEY);
                tryStoringSystemProperty(pr, Constants.CP_HOME_DIR);
                tryStoringSystemProperty(pr, Constants.KEY_STORE_LOC);
                tryStoringSystemProperty(pr, Constants.CP_CERT_LOC);
            }
        }
        catch (IOException e) {
            //cannot and should not happen since
            //defaultValues.properties is within the jar file
            e.printStackTrace();
            System.exit(0);
        }
        return new Configuration(pr);
    }

    /**
     * Try storing system property.
     *
     * @param properties the properties
     * @param key the key
     */
    private void tryStoringSystemProperty(Properties properties, String key) {
        String value = properties.getProperty(key);
        properties.setProperty(key, StringUtils.defaultIfEmpty(
            getProperty(key), value));
    }

    
    protected void handleClientException(RuntimeException e, BaseCommand cmd) {
        if (e.getCause() != null) {
            if (e.getCause().getClass() == java.net.ConnectException.class) {
                System.out.println("Error connecting to " + config.getServerURL());
                return;
            }
        }
        e.printStackTrace();
    }

    public static void main(String[] args) {
        Security.addProvider(new BouncyCastleProvider());
        CLIMain cli = new CLIMain();
        cli.execute(args);

    }


}
