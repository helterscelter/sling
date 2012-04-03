/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.sling.launchpad.webapp.integrationtest.servlets.post;

import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.sling.commons.testing.integration.HttpTestBase;

/** Test the custom PostOperation provided by the test-services module */
public class CustomPostOperationTest extends HttpTestBase {
    private TestNode testNode;

    final String POST_OPERATION_NAME = "test:example";
    
    /** Our example operation adds a property with this name to the node that it processes */
    public static final String MARKER = "org.apache.sling.launchpad.testservices.post.SlingPostOperationExample";
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        
        final String testPath = "/" + getClass().getSimpleName() + System.currentTimeMillis();
        testNode = new TestNode(HTTP_BASE_URL + testPath, null);
    }

    public void testCustomPostOperation() throws Exception {
        final String jsonPath = testNode.nodeUrl + ".tidy.json";
        assertFalse("Expecting no marker before POST", getContent(jsonPath, CONTENT_TYPE_JSON).contains(MARKER));
        
        final PostMethod post = new PostMethod(testNode.nodeUrl);
        post.addParameter(":operation", POST_OPERATION_NAME);

        assertEquals("Expecting 200 status on POST", 200, httpClient.executeMethod(post));
        assertTrue("Expecting marker to be present after POST", getContent(jsonPath, CONTENT_TYPE_JSON).contains(MARKER));
    }
}