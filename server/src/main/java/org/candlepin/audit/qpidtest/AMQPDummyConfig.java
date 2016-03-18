package org.candlepin.audit.qpidtest;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;

public class AMQPDummyConfig implements Configuration {

    @Override
    public Configuration subset(String prefix) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Configuration strippedSubset(String prefix) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Properties toProperties() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Properties toProperties(Map<String, String> defaults) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Properties toProperties(Properties defaults) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Map<String, String> toMap() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Map<String, String> toMap(Map<String, String> defaults) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean isEmpty() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean containsKey(String key) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void setProperty(String key, String value) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void clear() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void clearProperty(String key) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Iterable<String> getKeys() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getProperty(String key) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public String getProperty(String key, String defaultValue) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean getBoolean(String key) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public int getInt(String key) {
        if (key.equals(ConfigProperties.AMQP_CONNECTION_RETRY_ATTEMPTS)){
            return 1;
        }
        throw new RuntimeException("Not implemented");
    }

    @Override
    public int getInt(String key, int defaultValue) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public long getLong(String key) {
        if (key.equals(ConfigProperties.AMQP_CONNECTION_RETRY_INTERVAL)){
            return 10;
        }
        throw new RuntimeException("Not implemented");
    }

    @Override
    public long getLong(String key, long defaultValue) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public String getString(String key) {
        if (key.equals(ConfigProperties.AMQP_TRUSTSTORE)){
            return "/etc/candlepin/certs/amqp/candlepin.truststore";
        } else if (key.equals(ConfigProperties.AMQP_TRUSTSTORE_PASSWORD)){
            return "password";
        } else if (key.equals(ConfigProperties.AMQP_KEYSTORE)){
            return  "/etc/candlepin/certs/amqp/candlepin.jks";
        } else if (key.equals(ConfigProperties.AMQP_KEYSTORE_PASSWORD)){
            return "password";
        } else if (key.equals(ConfigProperties.AMQP_CONNECT_STRING)){
            return "tcp://localhost:5671?ssl='true'&ssl_cert_alias='candlepin'";
        } else if (key.equals(ConfigProperties.FULL_AMQP_CONNECT_STRING)){
            return "amqp://guest:guest@localhost/test?brokerlist=" +
                    "'tcp://localhost:5671?ssl='true'&ssl_cert_alias='candlepin''";
        }
        
        throw new RuntimeException("Not implemented");
    }

    @Override
    public String getString(String key, String defaultValue) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public String getString(String key, String defaultValue, TrimMode trimMode) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public List<String> getList(String key) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public List<String> getList(String key, List<String> defaultValue) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Set<String> getSet(String key) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Set<String> getSet(String key, Set<String> defaultValue) {
        throw new RuntimeException("Not implemented");
    }

}
