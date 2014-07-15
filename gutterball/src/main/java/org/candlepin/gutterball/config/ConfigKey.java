package org.candlepin.gutterball.config;

public enum ConfigKey {
    AMQP_CONNECT_STRING("gutterball.amqp.connect"),
    AMQP_KEYSTORE("gutterball.amqp.keystore"),
    AMQP_KEYSTORE_PASSWORD("gutterball.amqp.keystore_password"),
    AMQP_TRUSTSTORE("gutterball.amqp.truststore"),
    AMQP_TRUSTSTORE_PASSWORD("gutterball.amqp.truststore_password");

    private String key;
    private ConfigKey(String key) {
        this.key = key;
    }

    public String toString() {
        return key;
    }
}
