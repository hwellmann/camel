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
package org.apache.camel.builder.script;

import org.apache.camel.ScriptTestHelper;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 *
 */
public class GroovyPropertyComponentTest extends CamelTestSupport {

    @Test
    public void testSendMatchingMessage() throws Exception {
        if (!ScriptTestHelper.canRunTestOnThisPlatform()) {
            return;
        }

        log.info("Can run this test");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        getMockEndpoint("mock:unmatched").expectedMessageCount(0);

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", "Camel");

        assertMockEndpointsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                PropertiesComponent pc = context.getComponent("properties", PropertiesComponent.class);
                pc.setLocation("org/apache/camel/builder/script/myproperties.properties");

                from("direct:start").choice().
                        when().groovy("request.headers.get('foo') == context.resolvePropertyPlaceholders('{{foo}}')")
                            .to("mock:result")
                        .otherwise()
                            .to("mock:unmatched");
            }
        };
    }
}
