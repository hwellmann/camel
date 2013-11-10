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
package org.apache.camel.component.sjmsee.tx;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Session;

import org.apache.camel.Exchange;
import org.apache.camel.component.sjmsee.TransactionCommitStrategy;
import org.apache.camel.spi.Synchronization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SessionTransactionSynchronization is called at the completion of each {@link org.apache.camel.Exhcnage}.
 */
public class SessionTransactionSynchronization implements Synchronization {
    private Logger log = LoggerFactory.getLogger(getClass());
    private Connection connection;
    private Session session;
    private final TransactionCommitStrategy commitStrategy;

    public SessionTransactionSynchronization(Connection conn, Session session, TransactionCommitStrategy commitStrategy) {
        this.connection = conn;
        this.session = session;
        if (commitStrategy == null) {
            this.commitStrategy = new DefaultTransactionCommitStrategy();
        } else {
            this.commitStrategy = commitStrategy;
        }
    }

    /**
     * @see
     * org.apache.camel.spi.Synchronization#onFailure(org.apache.camel.Exchange)
     * @param exchange
     */
    @Override
    public void onFailure(Exchange exchange) {
        try {
            if (commitStrategy.rollback(exchange)) {
                log.debug("Processing failure of Exchange id:{}", exchange.getExchangeId());
                if (session != null && session.getTransacted()) {
                    session.rollback();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to rollback the session: {}", e.getMessage());
        }
        finally {
            try {
                connection.close();
            }
            catch (JMSException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    /**
     * @see
     * org.apache.camel.spi.Synchronization#onComplete(org.apache.camel.Exchange
     * )
     * @param exchange
     */
    @Override
    public void onComplete(Exchange exchange) {
        try {
            if (commitStrategy.commit(exchange)) {
                log.debug("Processing completion of Exchange id:{}", exchange.getExchangeId());
                if (session != null && session.getTransacted()) {
                    session.commit();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to commit the session: {}", e.getMessage());
            exchange.setException(e);
        }
        finally {
            try {
                connection.close();
            }
            catch (JMSException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

}
