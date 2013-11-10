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

/**
 * TODO Add Class documentation for SjmsConstants
 */
public final class SjmsConstants {
    public static final String QUEUE_PREFIX = "queue:";
    public static final String TOPIC_PREFIX = "topic:";
    public static final String TEMP_QUEUE_PREFIX = "temp:queue:";
    public static final String TEMP_TOPIC_PREFIX = "temp:topic:";

    public static final String JMS_MESSAGE_TYPE = "JmsMessageType";
    public static final String ORIGINAL_MESSAGE = "SjmsOriginalMessage";

    private SjmsConstants() {
        // Helper class
    }

}