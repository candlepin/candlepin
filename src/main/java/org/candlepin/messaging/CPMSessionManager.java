package org.candlepin.messaging;

public interface CPMSessionManager {

    CPMConsumer createConsumerSession(CPMConsumerConfig config) throws CPMException;

    CPMProducer createProducerSession(CPMProducerConfig config) throws CPMException;

}
