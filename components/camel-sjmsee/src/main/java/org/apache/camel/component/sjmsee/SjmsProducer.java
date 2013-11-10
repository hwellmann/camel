/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.sjmsee;

import java.util.concurrent.ExecutorService;

import javax.jms.Connection;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.component.sjmsee.jms.ConnectionResource;
import org.apache.camel.impl.DefaultAsyncProducer;
import org.apache.camel.util.ObjectHelper;

/**
 * Base SjmsProducer class.
 */
public abstract class SjmsProducer extends DefaultAsyncProducer {

    /**
     * The {@link MessageProducer} resources for all {@link SjmsProducer}
     * classes.
     */
    protected class MessageProducerResources {
        private final Connection connection;
        private final Session session;
        private final MessageProducer messageProducer;
        private final TransactionCommitStrategy commitStrategy;

        public MessageProducerResources(Connection connection, Session session, MessageProducer messageProducer) {
            this(connection, session, messageProducer, null);
        }

        public MessageProducerResources(Connection connection, Session session, MessageProducer messageProducer, TransactionCommitStrategy commitStrategy) {
            this.connection = connection;
            this.session = session;
            this.messageProducer = messageProducer;
            this.commitStrategy = commitStrategy;
        }
        
        public Connection getConnection() {
            return connection;
        }

        /**
         * Gets the Session value of session for this instance of
         * MessageProducerResources.
         * 
         * @return the session
         */
        public Session getSession() {
            return session;
        }

        /**
         * Gets the QueueSender value of queueSender for this instance of
         * MessageProducerResources.
         * 
         * @return the queueSender
         */
        public MessageProducer getMessageProducer() {
            return messageProducer;
        }

        /**
         * Gets the TransactionCommitStrategy value of commitStrategy for this
         * instance of SjmsProducer.MessageProducerResources.
         * 
         * @return the commitStrategy
         */
        public TransactionCommitStrategy getCommitStrategy() {
            return commitStrategy;
        }
    }

    private final ExecutorService executor;

    public SjmsProducer(Endpoint endpoint) {
        super(endpoint);
        this.executor = endpoint.getCamelContext().getExecutorServiceManager().newDefaultThreadPool(this, "SjmsProducer");
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
    }

    public abstract void sendMessage(Exchange exchange, final AsyncCallback callback) throws Exception;

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        if (log.isDebugEnabled()) {
            log.debug("Processing Exchange.id:{}", exchange.getExchangeId());
        }

        try {
            if (!isSynchronous()) {
                if (log.isDebugEnabled()) {
                    log.debug("  Sending message asynchronously: {}", exchange.getIn().getBody());
                }
                getExecutor().execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            sendMessage(exchange, callback);
                        } catch (Exception e) {
                            ObjectHelper.wrapRuntimeCamelException(e);
                        }
                    }
                });
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("  Sending message synchronously: {}", exchange.getIn().getBody());
                }
                sendMessage(exchange, callback);
            }
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug("Processing Exchange.id:{}", exchange.getExchangeId() + " - FAILED");
            }
            if (log.isDebugEnabled()) {
                log.trace("Exception: " + e.getLocalizedMessage(), e);
            }
            exchange.setException(e);
        }
        log.debug("Processing Exchange.id:{}", exchange.getExchangeId() + " - SUCCESS");

        return isSynchronous();
    }

    protected SjmsEndpoint getSjmsEndpoint() {
        return (SjmsEndpoint)this.getEndpoint();
    }

    protected ConnectionResource getConnectionResource() {
        return getSjmsEndpoint().getConnectionResource();
    }

    /**
     * Gets the acknowledgment mode for this instance of DestinationProducer.
     * 
     * @return int
     */
    public int getAcknowledgeMode() {
        return getSjmsEndpoint().getAcknowledgementMode().intValue();
    }

    /**
     * Gets the synchronous value for this instance of DestinationProducer.
     * 
     * @return true if synchronous, otherwise false
     */
    public boolean isSynchronous() {
        return getSjmsEndpoint().isSynchronous();
    }

    /**
     * Gets the replyTo for this instance of DestinationProducer.
     * 
     * @return String
     */
    public String getReplyTo() {
        return getSjmsEndpoint().getNamedReplyTo();
    }

    /**
     * Gets the destinationName for this instance of DestinationProducer.
     * 
     * @return String
     */
    public String getDestinationName() {
        return getSjmsEndpoint().getDestinationName();
    }

    /**
     * Test to verify if this endpoint is a JMS Topic or Queue.
     * 
     * @return true if it is a Topic, otherwise it is a Queue
     */
    public boolean isTopic() {
        return getSjmsEndpoint().isTopic();
    }

    /**
     * Test to determine if this endpoint should use a JMS Transaction.
     * 
     * @return true if transacted, otherwise false
     */
    public boolean isEndpointTransacted() {
        return getSjmsEndpoint().isTransacted();
    }

    /**
     * Returns the named reply to value for this producer
     * 
     * @return true if it is a Topic, otherwise it is a Queue
     */
    public String getNamedReplyTo() {
        return getSjmsEndpoint().getNamedReplyTo();
    }

    /**
     * Gets the producerCount for this instance of SjmsProducer.
     * 
     * @return int
     */
    public int getProducerCount() {
        return getSjmsEndpoint().getProducerCount();
    }

    /**
     * Gets consumerCount for this instance of SjmsProducer.
     * 
     * @return int
     */
    public int getConsumerCount() {
        return getSjmsEndpoint().getConsumerCount();
    }

    /**
     * Gets the executor for this instance of SjmsProducer.
     * 
     * @return ExecutorService
     */
    public ExecutorService getExecutor() {
        return executor;
    }

    /**
     * Gets the ttl for this instance of SjmsProducer.
     * 
     * @return long
     */
    public long getTtl() {
        return getSjmsEndpoint().getTtl();
    }

    /**
     * Gets the boolean value of persistent for this instance of SjmsProducer.
     * 
     * @return true if persistent, otherwise false
     */
    public boolean isPersistent() {
        return getSjmsEndpoint().isPersistent();
    }

    /**
     * Gets responseTimeOut for this instance of SjmsProducer.
     * 
     * @return long
     */
    public long getResponseTimeOut() {
        return getSjmsEndpoint().getResponseTimeOut();
    }

    /**
     * Gets commitStrategy for this instance of SjmsProducer.
     * 
     * @return TransactionCommitStrategy
     */
    public TransactionCommitStrategy getCommitStrategy() {
        return getSjmsEndpoint().getTransactionCommitStrategy();
    }

}