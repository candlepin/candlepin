/*
 *  Copyright (c) 2009 - ${YEAR} Red Hat, Inc.
 *
 *  This software is licensed to you under the GNU General Public License,
 *  version 2 (GPLv2). There is NO WARRANTY for this software, express or
 *  implied, including the implied warranties of MERCHANTABILITY or FITNESS
 *  FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 *  along with this software; if not, see
 *  http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 *  Red Hat trademarks are not licensed under GPLv2. No permission is
 *  granted to use or replicate Red Hat trademarks that are incorporated
 *  in this software or its documentation.
 */

package org.candlepin;

//import org.candlepin.guice.CandlepinContextListener;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.fasterxml.jackson.datatype.hibernate5.Hibernate5Module;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.commons.lang.StringUtils;
import org.candlepin.async.impl.ActiveMQSessionFactory;
import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventFilter;
import org.candlepin.audit.EventSink;
import org.candlepin.audit.EventSinkImpl;
import org.candlepin.common.config.ConfigurationException;
import org.candlepin.common.config.EncryptedConfiguration;
import org.candlepin.common.config.MapConfiguration;
import org.candlepin.common.jackson.HateoasBeanPropertyFilter;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.DatabaseConfigFactory;
//import org.candlepin.guice.CandlepinFilterModule;
import org.candlepin.controller.mode.CandlepinModeManager;
import org.candlepin.dto.api.v1.StatusDTO;
import org.candlepin.guice.CandlepinModule;
import org.candlepin.guice.DefaultConfig;
import org.candlepin.guice.I18nProvider;
import org.candlepin.jackson.PoolEventFilter;
import org.candlepin.messaging.CPMContextListener;
import org.candlepin.messaging.CPMSessionFactory;
import org.candlepin.messaging.impl.artemis.ArtemisContextListener;
import org.candlepin.messaging.impl.artemis.ArtemisSessionFactory;
import org.candlepin.messaging.impl.artemis.ArtemisUtil;
import org.candlepin.messaging.impl.noop.NoopContextListener;
import org.candlepin.messaging.impl.noop.NoopSessionFactory;
import org.candlepin.spring.CandlepinContextListener;
import org.hibernate.dialect.PostgreSQL92Dialect;
import org.jboss.resteasy.springboot.ResteasyAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.*;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.guice.annotation.EnableGuiceModules;
//import org.springframework.jms.annotation.JmsListener;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.xnap.commons.i18n.I18n;

import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import java.io.File;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

import static org.candlepin.config.ConfigProperties.ENCRYPTED_PROPERTIES;
import static org.candlepin.config.ConfigProperties.PASSPHRASE_SECRET_FILE;

@EnableGuiceModules
@Configuration
@Import({ResteasyAutoConfiguration.class})
//@EnableRetry
@EnableAspectJAutoProxy
@EnableWebMvc
@PropertySource("classpath:application.properties")
public class ApplicationConfiguration  implements WebMvcConfigurer  {
    @Autowired
    private ServletContext servletContext;

//    @Autowired
//    I18nProvider i18nProvider;

    private org.candlepin.common.config.Configuration config;
//
//    @Bean
//    public I18n i18n() {
//        return i18nProvider.get();
//    }
    @Bean
    public org.candlepin.common.config.Configuration configuration() {
        try {
            //log.info("Candlepin reading configuration.");
            config = readConfiguration(servletContext);
        }
        catch (ConfigurationException e) {
            //log.error("Could not read configuration file.  Aborting initialization.", e);
            throw new RuntimeException(e);
        }
        return config;
    }
    protected org.candlepin.common.config.Configuration readConfiguration(ServletContext context)
            throws ConfigurationException {

        // Use StandardCharsets.UTF_8 when we move to Java 7
        Charset utf8 = Charset.forName("UTF-8");
        EncryptedConfiguration systemConfig = new EncryptedConfiguration();

        systemConfig.setEncoding(utf8);
        File configFile = new File(ConfigProperties.DEFAULT_CONFIG_FILE);

        if (configFile.canRead()) {
            //log.debug("Loading system configuration");
            // First, read the system configuration
            systemConfig.load(configFile);
            //log.debug("System configuration: " + systemConfig);
        }

        systemConfig.use(PASSPHRASE_SECRET_FILE).toDecrypt(ENCRYPTED_PROPERTIES);

        // load the defaults
        MapConfiguration defaults = new MapConfiguration(ConfigProperties.DEFAULT_PROPERTIES);

        // Default to Postgresql if jpa.config.hibernate.dialect is unset
        DatabaseConfigFactory.SupportedDatabase db = determinDatabaseConfiguration(systemConfig.getString
                ("jpa.config.hibernate.dialect", PostgreSQL92Dialect.class.getName()));
        //log.info("Running under {}", db.getLabel());
        org.candlepin.common.config.Configuration databaseConfig = DatabaseConfigFactory.fetchConfig(db);

        // merge the defaults with the system configuration. PARAMETER ORDER MATTERS.
        // systemConfig must be FIRST to override subsequent configs.  This set up allows
        // the systemConfig to override databaseConfig if necessary, but that's not really something
        // users should be doing unbidden so it is undocumented.
        return EncryptedConfiguration.merge(systemConfig, databaseConfig, defaults);
    }

    private DatabaseConfigFactory.SupportedDatabase determinDatabaseConfiguration(String dialect) {
        if (StringUtils.containsIgnoreCase(
                dialect, DatabaseConfigFactory.SupportedDatabase.MYSQL.getLabel())) {
            return DatabaseConfigFactory.SupportedDatabase.MYSQL;
        }
        if (StringUtils
                .containsIgnoreCase(dialect, DatabaseConfigFactory.SupportedDatabase.MARIADB.getLabel())) {
            return DatabaseConfigFactory.SupportedDatabase.MARIADB;
        }
        return DatabaseConfigFactory.SupportedDatabase.POSTGRESQL;
    }

    @Bean
    public CandlepinModule candlepinModule(org.candlepin.common.config.Configuration config) {
        return new CandlepinModule(config);
    }

    @Bean
    public DefaultConfig defaultConfig() {
        return new DefaultConfig();
    }

    @Bean
    @Qualifier("regex")
    public String getRegex(org.candlepin.common.config.Configuration config) {
        String regex = ".*";
        /*
         * A negative lookeahead regex that makes sure that we can serve
         * static content at docs/ and/or token/
         */
        if (config.getBoolean(ConfigProperties.TOKENPAGE_ENABLED) &&
                config.getBoolean(ConfigProperties.SWAGGER_ENABLED)) {
            // don't filter docs or token
            regex = "^(?!/docs|/token).*";
        }
        else if (config.getBoolean(ConfigProperties.SWAGGER_ENABLED)) {
            // don't filter docs
            regex = "^(?!/docs).*";
        }
        else if (config.getBoolean(ConfigProperties.TOKENPAGE_ENABLED)) {
            // don't filter token
            regex = "^(?!/token).*";
        }
        return regex;
    }


    @Bean
    public CPMContextListener cpmContextListener(@Qualifier("provider") String provider) {
        if (ArtemisUtil.PROVIDER.equalsIgnoreCase(provider)) {
            return new ArtemisContextListener();
        } else {
            return new NoopContextListener();
        }
    }

    @Bean
    public CPMSessionFactory cpmSessionFactory(@Qualifier("provider") String provider, org.candlepin.common.config.Configuration config) {
        if (ArtemisUtil.PROVIDER.equalsIgnoreCase(provider)) {
            return new ArtemisSessionFactory(config);
        } else {
            return new NoopSessionFactory();
        }
    }

    @Bean
    @Qualifier("provider")
    public String getProvider(org.candlepin.common.config.Configuration config) {
        return config.getString(ConfigProperties.CPM_PROVIDER);
    }

//    @Bean
//    @Qualifier("loggingFilterConfig")
//    public FilterConfig getLoggingFilterConfig() {
//        Map<String, String> loggingFilterConfig = new HashMap<>();
//        loggingFilterConfig.put("header.name", "x-candlepin-request-uuid");
//        return (FilterConfig) loggingFilterConfig;
//    }

    @Bean
    public EventSink eventSink(EventFilter eventFilter, EventFactory eventFactory,
                               ObjectMapper mapper, org.candlepin.common.config.Configuration config, ActiveMQSessionFactory sessionFactory,
                               CandlepinModeManager modeManager) throws ActiveMQException {
        return new EventSinkImpl(eventFilter, eventFactory,
                mapper, config, sessionFactory,
                modeManager);
    }

}
