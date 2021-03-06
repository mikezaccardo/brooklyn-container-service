/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.container.location.openshift;

import com.google.common.base.Joiner;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.container.entity.openshift.OpenShiftPod;
import org.apache.brooklyn.container.entity.openshift.OpenShiftResource;
import org.apache.brooklyn.container.location.kubernetes.KubernetesLocationYamlLiveTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.apache.brooklyn.container.location.openshift.OpenShiftLocationLiveTest.*;

/**
 * Tests YAML apps via the {@code openshift"} location, to an OpenShift endpoint.
 * By extending {@link KubernetesLocationYamlLiveTest}, we get all the k8s tests.
 * <p>
 * It needs configured with something like:
 * <p>
 * <pre>
 * {@code
 * -Dtest.amp.openshift.endpoint=https://master.example.com:8443/
 * -Dtest.amp.openshift.certsBaseDir=/Users/aled/repos/grkvlt/40bdf09b09d5896e19a9d287f41d39bb
 * -Dtest.amp.openshift.namespace=test
 * }
 * </pre>
 */
public class OpenShiftLocationYamlLiveTest extends KubernetesLocationYamlLiveTest {

    // TODO testTomcatContainer seems flaky on the OpenShift deployed at 10.101.1.139, 
    // when using node2.
    //
    // The container's log shows it takes 355 seconds to deploy the default web application 
    // directory /usr/local/tomcat/webapps/ROOT:
    //    24-Nov-2016 22:04:11.906 INFO [main] org.apache.catalina.core.StandardEngine.startInternal Starting Servlet Engine: Apache Tomcat/8.0.39
    //    24-Nov-2016 22:04:11.940 INFO [localhost-startStop-1] org.apache.catalina.startup.HostConfig.deployDirectory Deploying web application directory /usr/local/tomcat/webapps/ROOT
    //    24-Nov-2016 22:10:07.093 INFO [localhost-startStop-1] org.apache.catalina.util.SessionIdGeneratorBase.createSecureRandom Creation of SecureRandom instance for session ID generation using [SHA1PRNG] took [354,156] milliseconds.
    //    24-Nov-2016 22:10:07.123 INFO [localhost-startStop-1] org.apache.catalina.startup.HostConfig.deployDirectory Deployment of web application directory /usr/local/tomcat/webapps/ROOT has finished in 355,183 ms
    //    24-Nov-2016 22:10:07.133 INFO [localhost-startStop-1] org.apache.catalina.startup.HostConfig.deployDirectory Deploying web application directory /usr/local/tomcat/webapps/docs
    //    24-Nov-2016 22:10:07.172 INFO [localhost-startStop-1] org.apache.catalina.startup.HostConfig.deployDirectory Deployment of web application directory /usr/local/tomcat/webapps/docs has finished in 40 ms
    //    24-Nov-2016 22:10:07.173 INFO [localhost-startStop-1] org.apache.catalina.startup.HostConfig.deployDirectory Deploying web application directory /usr/local/tomcat/webapps/examples
    //    24-Nov-2016 22:10:08.051 INFO [localhost-startStop-1] org.apache.catalina.startup.HostConfig.deployDirectory Deployment of web application directory /usr/local/tomcat/webapps/examples has finished in 878 ms
    //    24-Nov-2016 22:10:08.052 INFO [localhost-startStop-1] org.apache.catalina.startup.HostConfig.deployDirectory Deploying web application directory /usr/local/tomcat/webapps/host-manager
    //    24-Nov-2016 22:10:08.104 INFO [localhost-startStop-1] org.apache.catalina.startup.HostConfig.deployDirectory Deployment of web application directory /usr/local/tomcat/webapps/host-manager has finished in 52 ms
    //    24-Nov-2016 22:10:08.104 INFO [localhost-startStop-1] org.apache.catalina.startup.HostConfig.deployDirectory Deploying web application directory /usr/local/tomcat/webapps/manager
    //    24-Nov-2016 22:10:08.159 INFO [localhost-startStop-1] org.apache.catalina.startup.HostConfig.deployDirectory Deployment of web application directory /usr/local/tomcat/webapps/manager has finished in 55 ms
    //
    // With node1, it takes only 6 seconds to deploy the we app.

    @BeforeMethod(alwaysRun = true)
    @Override
    public void setUp() throws Exception {
        super.setUp();

        locationYaml = Joiner.on("\n").join(
                "location:",
                "  openshift:",
                "    " + OpenShiftLocation.CLOUD_ENDPOINT.getName() + ": \"" + OPENSHIFT_ENDPOINT + "\"",
                "    " + OpenShiftLocation.CA_CERT_FILE.getName() + ": \"" + CA_CERT_FILE + "\"",
                "    " + OpenShiftLocation.CLIENT_CERT_FILE.getName() + ": \"" + CLIENT_CERT_FILE + "\"",
                "    " + OpenShiftLocation.CLIENT_KEY_FILE.getName() + ": \"" + CLIENT_KEY_FILE + "\"",
                "    " + OpenShiftLocation.NAMESPACE.getName() + ": \"" + NAMESPACE + "\"",
                "    " + OpenShiftLocation.PRIVILEGED.getName() + ": true",
                "    " + OpenShiftLocation.LOGIN_USER_PASSWORD.getName() + ": p4ssw0rd");
    }

    @Test(groups = {"Live"})
    public void testTomcatOpenShiftPod() throws Exception {
        String yaml = Joiner.on("\n").join(
                locationYaml,
                "services:",
                "  - type: " + OpenShiftPod.class.getName(),
                "    brooklyn.config:",
                "      docker.container.imageName: tomcat",
                "      docker.container.inboundPorts: [ \"8080\" ]");

        runTomcat(yaml, OpenShiftPod.class);
    }

    @Test(groups = {"Live"})
    public void testOpenShiftPod() throws Exception {
        String yaml = Joiner.on("\n").join(
                locationYaml,
                "services:",
                "  - type: " + OpenShiftPod.class.getName(),
                "    brooklyn.config:",
                "      docker.container.imageName: tomcat",
                "      docker.container.inboundPorts:",
                "        - \"8080\"",
                "      shell.env:",
                "        CLUSTER_ID: \"id\"",
                "        CLUSTER_TOKEN: \"token\"");

        Entity app = createStartWaitAndLogApplication(yaml);
        checkPod(app, OpenShiftPod.class);
    }

    /* Test disabled as QA framework AMP does not have catalog entries deployed yet */
    @Test(groups = {"Live"}, enabled = false)
    public void testOpenShiftPodCatalogEntry() throws Exception {
        String yaml = Joiner.on("\n").join(
                locationYaml,
                "services:",
                "  - type: openshift-pod-entity",
                "    brooklyn.config:",
                "      docker.container.imageName: tomcat",
                "      docker.container.inboundPorts:",
                "        - \"8080\"",
                "      shell.env:",
                "        CLUSTER_ID: \"id\"",
                "        CLUSTER_TOKEN: \"token\"");

        Entity app = createStartWaitAndLogApplication(yaml);
        checkPod(app, OpenShiftPod.class);
    }

    @Test(groups = {"Live"})
    public void testNginxOpenShiftResource() throws Exception {
        String yaml = Joiner.on("\n").join(
                locationYaml,
                "services:",
                "  - type: " + OpenShiftResource.class.getName(),
                "    id: nginx",
                "    name: \"nginx\"",
                "    brooklyn.config:",
                "      resource: classpath://nginx.yaml");

        Entity app = createStartWaitAndLogApplication(yaml);
        checkNginxResource(app, OpenShiftResource.class);
    }

    /* Test disabled as QA framework AMP does not have catalog entries deployed yet */
    @Test(groups = {"Live"}, enabled = false)
    public void testNginxOpenShiftResourceCatalogEntry() throws Exception {
        String yaml = Joiner.on("\n").join(
                locationYaml,
                "services:",
                "  - type: openshift-resource-entity",
                "    id: nginx",
                "    name: \"nginx\"",
                "    brooklyn.config:",
                "      resource: classpath://nginx.yaml");

        Entity app = createStartWaitAndLogApplication(yaml);
        checkNginxResource(app, OpenShiftResource.class);
    }

}
