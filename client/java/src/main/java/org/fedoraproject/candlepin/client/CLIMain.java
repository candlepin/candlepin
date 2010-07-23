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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang.StringUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.fedoraproject.candlepin.client.cmds.BaseCommand;
import org.fedoraproject.candlepin.client.cmds.FactsCommand;
import org.fedoraproject.candlepin.client.cmds.HelpCommand;
import org.fedoraproject.candlepin.client.cmds.ListCommand;
import org.fedoraproject.candlepin.client.cmds.RegisterCommand;
import org.fedoraproject.candlepin.client.cmds.SubscribeCommand;
import org.fedoraproject.candlepin.client.cmds.UnRegisterCommand;
import org.fedoraproject.candlepin.client.cmds.UnSubscribeCommand;
import org.fedoraproject.candlepin.client.cmds.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ClientMain
 */
public class CLIMain {
    /**
     *
     */
    private HashMap<String, BaseCommand> cmds = new HashMap<String, BaseCommand>();
    private Configuration config;
    private static final Logger L = LoggerFactory.getLogger(CLIMain.class);

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
                FactsCommand.class, UnRegisterCommand.class };
            for (Class<? extends BaseCommand> cmdClass : commands) {
                BaseCommand cmd = cmdClass.newInstance();
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
        for (String arg : args) {
            if (!arg.startsWith("-")) {
                if (cmds.containsKey(arg)) {
                    return cmds.get(arg);
                }
            }
        }
        return null;
    }

    protected void execute(String[] args) {
        BaseCommand cmd = this.getCommand(args);
        L.info("Command line arguments : {}", Arrays.toString(args));
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
            this.config.setIgnoreTrustManagers(cmdLine.hasOption("k"));
            L.info("Ignoring trust managers? :{}", cmdLine.hasOption("k"));
            cmd.setClient(new DefaultCandlepinClientFacade(this.config));
            cmd.execute(cmdLine);
        }
        catch (ParseException e) {
            cmd.generateHelp();
        }
        catch (RuntimeException e) {
            this.handleClientException(e, cmd);
        }
        catch (Exception e) {
            handleUnknownException(e, cmd, args);
        }
    }

    /**
     * @param e
     */
    private void handleUnknownException(Exception e, BaseCommand cmd,
        String[] args) {
        System.err.println("Unable to execute " + cmd.getName() + " command.");
        L.error("Unable to execute cmd : " + cmd.getName(), e);
    }

    private Configuration loadConfig(CommandLine cmdLine) {
        Properties pr = Utils.getDefaultProperties();
        L.debug("Loaded default config values : {}", Utils.toStr(pr));
        try {
            String loc = cmdLine.getOptionValue("cfg");
            File file = new File(StringUtils.defaultIfEmpty(loc,
                Constants.DEFAULT_CONF_LOC));
            // config file exists
            if (file.exists() && file.canRead() && !file.isDirectory()) {
                L.debug("Config file {} exists. Trying to load values", file
                    .getAbsolutePath());
                Properties conf = new Properties();
                FileInputStream inputStream = new FileInputStream(file);
                conf.load(inputStream);
                L.debug("Loaded config from file {}", file.getAbsolutePath());
                pr = conf;
            }
            else {
                L.debug("Config file {} does not exist. "
                    + "Trying to load values from System.getProperty()", file
                    .getAbsolutePath());
                /*
                 * config file not found. Try getting values from from system
                 * environment
                 */
                tryStoringSystemProperty(pr, Constants.SERVER_URL_KEY);
                tryStoringSystemProperty(pr, Constants.CP_HOME_DIR);
                tryStoringSystemProperty(pr, Constants.CP_CERT_LOC);
            }
        }
        catch (IOException e) {
            // cannot and should not happen since
            // defaultValues.properties is within the jar file
            L.error("Exception trying to load config information", e);
            System.err.println("Unable to load configuration information.");
            System.exit(0);
        }
        L.info("Config info: {}", Utils.toStr(pr));
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
        L.error("Unable to execute cmd : " + cmd.getName(), e);
        if (e.getCause() != null) {
            if (e.getCause().getClass() == java.net.ConnectException.class) {
                System.out.println("Error connecting to " +
                    config.getServerURL());
                return;
            }
        }
        System.err.println("Unable to execute " + cmd.getName() + " command.");
        if (StringUtils.isNotEmpty(e.getMessage())) {
            System.err.println("Reason: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        Security.addProvider(new BouncyCastleProvider());
        CLIMain cli = new CLIMain();
        cli.execute(args);
    }

}
