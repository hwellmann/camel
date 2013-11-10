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

import org.apache.camel.Exchange;
import org.apache.camel.component.sjmsee.TransactionCommitStrategy;

/**
 * The default commit strategy for all transaction.
 * 
 */
public class DefaultTransactionCommitStrategy implements TransactionCommitStrategy {

    /**
     * @see org.apache.camel.component.sjmsee.TransactionCommitStrategy#commit(org.apache.camel.Exchange)
     *
     * @param exchange
     * @return
     * @throws Exception
     */
    @Override
    public boolean commit(Exchange exchange) throws Exception {
        return true;
    }

    /**
     * @see org.apache.camel.component.sjmsee.TransactionCommitStrategy#rollback(org.apache.camel.Exchange)
     *
     * @param exchange
     * @return
     * @throws Exception
     */
    @Override
    public boolean rollback(Exchange exchange) throws Exception {
        return true;
    }
}
