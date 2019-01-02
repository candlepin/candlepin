/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.audit;

import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;

import com.google.inject.Inject;

import org.apache.qpid.client.AMQAnyDestination;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

/**
 * Class that is able to determine status of Qpid Broker. It can determine whether
 * the Qpid Broker is available and also can determine whether it is not FLOW_STOPPED.
 *
 * To achieve this, we use QMF (Qpid Management Framework)
 * @author fnguyen
 *
 */
public class QpidQmf {
    private QpidConnection qpidConnection;

    private static Logger log = LoggerFactory.getLogger(QpidQmf.class);
    private String lastFlowStoppedQueue = "";
    private Configuration config;

    @Inject
    public QpidQmf(QpidConnection qpidConnection, Configuration config) throws URISyntaxException {
        this.qpidConnection = qpidConnection;
        this.config = config;
    }

    /**
     * Indempotent method that connects to Qpid and uses QMF to find information about a given
     * targetType. The best reference about how to interact with QMF can be found here:
     *
     * https://access.redhat.com/documentation/en-US/
     * Red_Hat_Enterprise_MRG/2/html-single/Messaging_Programming_Reference/index.html
     *
     * Other concepts and basic docs here
     *
     * https://qpid.apache.org/releases/qpid-cpp-1.35.0/cpp-broker/book/ch02s02.html
     *
     * @param targetType
     * @param query
     * @return
     * @throws JMSException
     */
    private List<Map<String, Object>> runQuery(String targetType, Map<Object, Object> query)
        throws JMSException {

        Session session = null;
        List<Map<String, Object>> result = new ArrayList<>();
        Connection connection = null;

        try {
            AMQAnyDestination qmfQueue = null;
            AMQAnyDestination responseQueue = null;

            try {
                qmfQueue = new AMQAnyDestination("qmf.default.direct/broker");
                responseQueue = new AMQAnyDestination(
                    "#reply-queue; {create:always, node:{x-declare:{auto-delete:true}}}");
            }
            catch (URISyntaxException e) {
                throw new RuntimeException("Couldn't create destinations", e);
            }

            connection = qpidConnection.newConnection();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer sender = session.createProducer(qmfQueue);
            MessageConsumer receiver = session.createConsumer(responseQueue);

            MapMessage request = session.createMapMessage();
            request.setJMSReplyTo(responseQueue);
            request.setStringProperty("x-amqp-0-10.app-id", "qmf2");
            request.setStringProperty("qmf.opcode", "_query_request");
            request.setObject(targetType, query);
            request.setObject("_what", "OBJECT"); // method name to be
            request.setJMSType("amqp/map");
            sender.send(request);


            Message response = receiver.receive(config.getInt(ConfigProperties.QPID_QMF_RECEIVE_TIMEOUT));

            if (response != null) {
                if (response instanceof MapMessage) {
                    log.debug("Result received {}", response);
                    MapMessage mm = (MapMessage) response;
                    Enumeration en = mm.getMapNames();

                    while (en.hasMoreElements()) {
                        Map<String, Object> next = (Map<String, Object>)
                            mm.getObject(en.nextElement().toString());

                        result.add(next);
                    }
                    return result;
                }
                else {
                    log.error("Received response in incorrect format: {}", response);
                }
            }
            else {
                log.error("No response received");
            }
        }
        catch (JMSException e) {
            throw e;
        }
        finally {
            try {
                if (connection != null) {
                    connection.close();
                }

                if (session != null) {
                    session.close();
                }
            }
            catch (JMSException e) {
                log.warn("Error closing the Qpid connection", e);
            }
        }

        return null;
    }

    /**
     * There are three situations we might end up. The simples is that Qpid connection might be
     * down. Second case is that Qpid is up but it is not responsive, because 'event' exchange
     * is flow stopped. This situation might occur when one of its queues is flow stopped. Last
     * situation is that Qpid is up and running without any difficulties.
     *
     *
     * More about flow control and flowStopped:
     *
     * https://qpid.apache.org/releases/qpid-cpp-1.35.0/cpp-broker/book/producer-flow-control.html
     *
     * @return Enum value. In case of FLOW_STOPPEED, the client can also use getLastFlowStoppedQueue
     * to find out which queue caused the flow stop
     *
     */
    public QpidStatus getStatus() {
        try {
            ExchangeData exchange = getExchageData();
            if (!exchange.exists()) {
                log.debug("Qpid has missing exchange.");
                return QpidStatus.MISSING_EXCHANGE;
            }

            if (!exchange.hasBinding()) {
                log.debug("Qpid has missing exchange binding!");
                return QpidStatus.MISSING_BINDING;
            }

            Set<String> exchangeBoundQueueNames = getExchangeBoundQueueNames("event");
            for (String queue : exchangeBoundQueueNames) {
                Object qinfo = getQueueInfo(queue);
                boolean flowStopped =  QpidQmf.<Boolean>extractValue(qinfo, "_values", "flowStopped");

                /**
                 * The reason we need to indicate this state of FLOW_STOPPED is that it only
                 * takes a single queue that is flow stopped to block whole 'event' exchange.
                 * In other words even if just one queue is flow stopped, Candlepin will start
                 * failing when sending messages to 'event' exchange.
                 *
                 * In  this flow stopped state, clients normally receive the following
                 * exception:
                 *
                 * JMSException: Exception when sending message:timed out waiting for sync
                 */
                if (flowStopped) {
                    lastFlowStoppedQueue = queue;
                    log.debug("Exchange 'event' is flow stopped because of queue {}", queue);
                    return QpidStatus.FLOW_STOPPED;
                }
            }
        }
        catch (JMSException e) {
            log.debug("The Qpid is down, received error when communicating with the Qpid", e);
            return QpidStatus.DOWN;
        }

        return QpidStatus.CONNECTED;
    }

    public String getLastFlowStoppedQueue() {
        return lastFlowStoppedQueue;
    }

    /**
     * Finds fully qualified names of queues that are bound to an exchange
     *
     * @param exchangeName
     * @return Fully qualified names of queues
     * @throws JMSException Error connecting
     */
    private Set<String> getExchangeBoundQueueNames(String exchangeName) throws JMSException {
        List<Map<String, Object>> mm = runQuery("_schema_id",
            Collections.singletonMap("_class_name", "binding"));

        Set<String> result = new HashSet<>();

        for (Map<String, Object> res : mm) {
            if (extractValue(res, "_values", "exchangeRef", "_object_name")
                .equals("org.apache.qpid.broker:exchange:" + exchangeName)) {
                result.add(QpidQmf.<String>extractValue(res, "_values", "queueRef", "_object_name"));
            }
        }
        return result;
    }

    private ExchangeData getExchageData() throws JMSException {
        List<Map<String, Object>> exchanges = runQuery("_schema_id",
            Collections.singletonMap("_class_name", "exchange"));

        for (Map<String, Object> exchange : exchanges) {
            String exchangeName = QpidQmf.<String>extractValue(exchange, "_values", "name");
            if (exchangeName.equals("event")) {
                long bindingCount = QpidQmf.<Long>extractValue(exchange, "_values", "bindingCount");
                log.debug("The 'event' exchange has a binding count of: {}", bindingCount);
                return new ExchangeData(true, bindingCount);
            }
        }
        return new ExchangeData(false, 0);
    }

    /**
     * Get information about a Queue with a given name
     * @param queueName fully qualified queueName
     * @return A Map that contains variuos information about the queue
     * @throws JMSException When not connected to Broker
     */
    private Map<String, Object> getQueueInfo(String queueName) throws JMSException {
        log.debug("Getting info about queue {}", queueName);
        List<Map<String, Object>> mm = runQuery("_object_id",
            Collections.singletonMap("_object_name", queueName));

        if (mm == null || mm.size() == 0) {
            throw new RuntimeException("Couldn't find a queue in Qpid: " + queueName);
        }
        else if (mm.size() > 1) {
            throw new RuntimeException("Found unexpected amount of information about queue: " + queueName);
        }

        return mm.get(0);
    }

    /**
     * The responses from Qpid come in a form of nested maps. The nested map might have
     * several sub-levels and at the end there is some non-map result. It might be a
     * String value represented as a byte array. Another possibility is Boolean value.
     * This method helps parse the nested map and get the final String value.

     * @param object The nested map that contains Maps and byte arrays
     * @param mapKeys succession of keys to be extracted. For example if 2 mapKeys
     *                (key1, key2) are provided, it means that there is a map on level 1 that has key1
     *                and under that key1 there is a map that contains key2. Under
     *                key2 there is non-map value
     * @return non-map value that resides under the last mapKey
     * @throws JMSException Error connecting
     */
    public static <T> T extractValue(Object object, String  ... mapKeys) throws JMSException {
        log.debug("Extracting [{}] from {}", Arrays.toString(mapKeys), object);
        if (object == null) {
            throw new IllegalArgumentException("Map Names is null");
        }

        for (String key : mapKeys) {
            log.debug("Extracting key {} from the object", key);
            if (!(object instanceof Map)) {
                String msg = String.format("The object under key %s is not a map! Object: %s", key, object);
                throw new RuntimeException(msg);
            }

            object = ((Map<String, Object>) object).get(key);
            log.debug("Extracted {} under key {}", object, key);
            if (object == null) {
                throw new RuntimeException("The extracted value at key " + key + " was null!");
            }
        }

        if (object instanceof byte[]) {
            log.debug("Found byte array that will be Stringified to {}", new String((byte[]) object));
            return (T) new String((byte[]) object);
        }

        if (object instanceof Boolean) {
            log.debug("Found boolean");
            return (T) object;
        }

        if (object instanceof Long) {
            log.debug("Found Long");
            return (T) object;
        }
        else {
            throw new RuntimeException("Expected the value to be byte[] but found: " + object.getClass());
        }
    }

    /**
     * Represents the qpid exchange state as reported by QMF.
     */
    private class ExchangeData {
        private boolean exists;
        private long bindingCount;

        public ExchangeData(boolean exists, long bindingCount) {
            this.exists = exists;
            this.bindingCount = bindingCount;
        }

        public boolean hasBinding() {
            return bindingCount > 0L;
        }

        public boolean exists() {
            return exists;
        }
    }

}
