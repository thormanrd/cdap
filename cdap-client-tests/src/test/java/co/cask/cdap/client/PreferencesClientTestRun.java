/*
 * Copyright © 2015 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.cdap.client;

import co.cask.cdap.client.app.AppReturnsArgs;
import co.cask.cdap.client.app.FakeApp;
import co.cask.cdap.client.common.ClientTestBase;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.common.exception.NotFoundException;
import co.cask.cdap.common.exception.ProgramNotFoundException;
import co.cask.cdap.proto.NamespaceMeta;
import co.cask.cdap.proto.ProgramType;
import co.cask.cdap.test.XSlowTests;
import co.cask.common.http.HttpMethod;
import co.cask.common.http.HttpRequest;
import co.cask.common.http.HttpRequests;
import co.cask.common.http.HttpResponse;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * Test for {@link PreferencesClient}
 */
@Category(XSlowTests.class)
public class PreferencesClientTestRun extends ClientTestBase {
  private static final Gson GSON = new Gson();

  private PreferencesClient client;
  private ApplicationClient appClient;
  private ServiceClient serviceClient;
  private ProgramClient programClient;
  private NamespaceClient namespaceClient;

  @Before
  public void setUp() throws Throwable {
    super.setUp();
    client = new PreferencesClient(clientConfig);
    appClient = new ApplicationClient(clientConfig);
    serviceClient = new ServiceClient(clientConfig);
    programClient = new ProgramClient(clientConfig);
    namespaceClient = new NamespaceClient(clientConfig);
  }

  @Test
  public void testProgramAPI() throws Exception {
    // Add Namespace Id when ProgramClient needs it.
    Map<String, String> propMap = Maps.newHashMap();
    propMap.put("key", "instance");
    File jarFile = createAppJarFile(AppReturnsArgs.class);
    appClient.deploy(jarFile);
    try {
      client.setInstancePreferences(propMap);
      Map<String, String> setMap = Maps.newHashMap();
      setMap.put("saved", "args");
      programClient.setRuntimeArgs(AppReturnsArgs.NAME, ProgramType.SERVICE, AppReturnsArgs.SERVICE, setMap);
      assertEquals(setMap, programClient.getRuntimeArgs(AppReturnsArgs.NAME, ProgramType.SERVICE,
                                                        AppReturnsArgs.SERVICE));
      programClient.start(AppReturnsArgs.NAME, ProgramType.SERVICE, AppReturnsArgs.SERVICE,
                          ImmutableMap.of("run", "value"));
      assertProgramRunning(programClient, AppReturnsArgs.NAME, ProgramType.SERVICE, AppReturnsArgs.SERVICE);
      propMap.put("run", "value");
      propMap.putAll(setMap);
      URL serviceURL = new URL(serviceClient.getServiceURL(AppReturnsArgs.NAME, AppReturnsArgs.SERVICE),
                               AppReturnsArgs.ENDPOINT);
      HttpRequest request = HttpRequest.builder(HttpMethod.GET, serviceURL).build();
      HttpResponse response = HttpRequests.execute(request);
      assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
      assertEquals(GSON.toJson(propMap), response.getResponseBodyAsString());
      programClient.stop(AppReturnsArgs.NAME, ProgramType.SERVICE, AppReturnsArgs.SERVICE);
      assertProgramStopped(programClient, AppReturnsArgs.NAME, ProgramType.SERVICE, AppReturnsArgs.SERVICE);

      client.deleteInstancePreferences();
      programClient.start(AppReturnsArgs.NAME, ProgramType.SERVICE, AppReturnsArgs.SERVICE);
      assertProgramRunning(programClient, AppReturnsArgs.NAME, ProgramType.SERVICE, AppReturnsArgs.SERVICE);
      propMap.remove("key");
      propMap.remove("run");
      serviceURL = new URL(serviceClient.getServiceURL(AppReturnsArgs.NAME, AppReturnsArgs.SERVICE),
                           AppReturnsArgs.ENDPOINT);
      request = HttpRequest.builder(HttpMethod.GET, serviceURL).build();
      response = HttpRequests.execute(request);
      assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
      assertEquals(GSON.toJson(propMap), response.getResponseBodyAsString());
      programClient.stop(AppReturnsArgs.NAME, ProgramType.SERVICE, AppReturnsArgs.SERVICE);
      assertProgramStopped(programClient, AppReturnsArgs.NAME, ProgramType.SERVICE, AppReturnsArgs.SERVICE);

      propMap.clear();
      programClient.setRuntimeArgs(AppReturnsArgs.NAME, ProgramType.SERVICE, AppReturnsArgs.SERVICE, propMap);
      programClient.start(AppReturnsArgs.NAME, ProgramType.SERVICE, AppReturnsArgs.SERVICE);
      assertProgramRunning(programClient, AppReturnsArgs.NAME, ProgramType.SERVICE, AppReturnsArgs.SERVICE);
      serviceURL = new URL(serviceClient.getServiceURL(AppReturnsArgs.NAME, AppReturnsArgs.SERVICE),
                           AppReturnsArgs.ENDPOINT);
      request = HttpRequest.builder(HttpMethod.GET, serviceURL).build();
      response = HttpRequests.execute(request);
      assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
      assertEquals(GSON.toJson(propMap), response.getResponseBodyAsString());
    } finally {
      programClient.stop(AppReturnsArgs.NAME, ProgramType.SERVICE, AppReturnsArgs.SERVICE);
      assertProgramStopped(programClient, AppReturnsArgs.NAME, ProgramType.SERVICE, AppReturnsArgs.SERVICE);
      appClient.delete(AppReturnsArgs.NAME);
    }
  }

  @Test
  public void testPreferences() throws Exception {
    String invalidNamespace = "invalid";
    namespaceClient.create(new NamespaceMeta.Builder().setId(invalidNamespace).build());

    Map<String, String> propMap = client.getInstancePreferences();
    Assert.assertEquals(ImmutableMap.<String, String>of(), propMap);
    propMap.put("k1", "instance");
    client.setInstancePreferences(propMap);
    Assert.assertEquals(propMap, client.getInstancePreferences());

    File jarFile = createAppJarFile(FakeApp.class);
    appClient.deploy(jarFile);

    propMap.put("k1", "namespace");
    client.setNamespacePreferences(Constants.DEFAULT_NAMESPACE, propMap);
    Assert.assertEquals(propMap, client.getNamespacePreferences(Constants.DEFAULT_NAMESPACE, true));
    Assert.assertEquals(propMap, client.getNamespacePreferences(Constants.DEFAULT_NAMESPACE, false));
    Assert.assertTrue(client.getNamespacePreferences(invalidNamespace, false).isEmpty());
    Assert.assertEquals("instance", client.getNamespacePreferences(invalidNamespace, true).get("k1"));

    client.deleteNamespacePreferences(Constants.DEFAULT_NAMESPACE);
    propMap.put("k1", "instance");
    Assert.assertEquals(propMap, client.getNamespacePreferences(Constants.DEFAULT_NAMESPACE, true));
    Assert.assertEquals(ImmutableMap.<String, String>of(), client.getNamespacePreferences(Constants.DEFAULT_NAMESPACE,
                                                                                          false));
    propMap.put("k1", "namespace");
    client.setNamespacePreferences(Constants.DEFAULT_NAMESPACE, propMap);
    Assert.assertEquals(propMap, client.getNamespacePreferences(Constants.DEFAULT_NAMESPACE, true));
    Assert.assertEquals(propMap, client.getNamespacePreferences(Constants.DEFAULT_NAMESPACE, false));
    propMap.put("k1", "application");
    client.setApplicationPreferences(Constants.DEFAULT_NAMESPACE, FakeApp.NAME, propMap);
    Assert.assertEquals(propMap, client.getApplicationPreferences(Constants.DEFAULT_NAMESPACE, FakeApp.NAME, true));
    Assert.assertEquals(propMap, client.getApplicationPreferences(Constants.DEFAULT_NAMESPACE, FakeApp.NAME, false));
    propMap.put("k1", "program");
    client.setProgramPreferences(Constants.DEFAULT_NAMESPACE, FakeApp.NAME, "flows", FakeApp.FLOWS.get(0), propMap);
    Assert.assertEquals(propMap, client.getProgramPreferences(Constants.DEFAULT_NAMESPACE, FakeApp.NAME, "flows",
                                                              FakeApp.FLOWS.get(0), true));
    Assert.assertEquals(propMap, client.getProgramPreferences(Constants.DEFAULT_NAMESPACE, FakeApp.NAME, "flows",
                                                              FakeApp.FLOWS.get(0), false));
    client.deleteProgramPreferences(Constants.DEFAULT_NAMESPACE, FakeApp.NAME, "flows", FakeApp.FLOWS.get(0));
    propMap.put("k1", "application");
    Assert.assertTrue(client.getProgramPreferences(Constants.DEFAULT_NAMESPACE, FakeApp.NAME, "flows",
                                                   FakeApp.FLOWS.get(0), false).isEmpty());
    Assert.assertEquals(propMap, client.getProgramPreferences(Constants.DEFAULT_NAMESPACE, FakeApp.NAME, "flows",
                                                              FakeApp.FLOWS.get(0), true));

    client.deleteApplicationPreferences(Constants.DEFAULT_NAMESPACE, FakeApp.NAME);

    propMap.put("k1", "namespace");
    Assert.assertTrue(client.getApplicationPreferences(Constants.DEFAULT_NAMESPACE, FakeApp.NAME, false).isEmpty());
    Assert.assertEquals(propMap, client.getApplicationPreferences(Constants.DEFAULT_NAMESPACE, FakeApp.NAME, true));
    Assert.assertEquals(propMap, client.getProgramPreferences(Constants.DEFAULT_NAMESPACE, FakeApp.NAME, "flows",
                                                              FakeApp.FLOWS.get(0), true));

    client.deleteNamespacePreferences(Constants.DEFAULT_NAMESPACE);
    propMap.put("k1", "instance");
    Assert.assertTrue(client.getNamespacePreferences(Constants.DEFAULT_NAMESPACE, false).isEmpty());
    Assert.assertEquals(propMap, client.getNamespacePreferences(Constants.DEFAULT_NAMESPACE, true));
    Assert.assertEquals(propMap, client.getApplicationPreferences(Constants.DEFAULT_NAMESPACE, FakeApp.NAME, true));
    Assert.assertEquals(propMap, client.getProgramPreferences(Constants.DEFAULT_NAMESPACE, FakeApp.NAME, "flows",
                                                              FakeApp.FLOWS.get(0), true));

    client.deleteInstancePreferences();
    propMap.clear();
    Assert.assertEquals(propMap, client.getInstancePreferences());
    Assert.assertEquals(propMap, client.getNamespacePreferences(Constants.DEFAULT_NAMESPACE, true));
    Assert.assertEquals(propMap, client.getNamespacePreferences(Constants.DEFAULT_NAMESPACE, true));
    Assert.assertEquals(propMap, client.getApplicationPreferences(Constants.DEFAULT_NAMESPACE, FakeApp.NAME, true));
    Assert.assertEquals(propMap, client.getProgramPreferences(Constants.DEFAULT_NAMESPACE, FakeApp.NAME, "flows",
                                                              FakeApp.FLOWS.get(0), true));


    //Test Deleting Application
    propMap.put("k1", "application");
    client.setApplicationPreferences(Constants.DEFAULT_NAMESPACE, FakeApp.NAME, propMap);
    Assert.assertEquals(propMap, client.getApplicationPreferences(Constants.DEFAULT_NAMESPACE, FakeApp.NAME, false));

    propMap.put("k1", "program");
    client.setProgramPreferences(Constants.DEFAULT_NAMESPACE, FakeApp.NAME, "flows", FakeApp.FLOWS.get(0), propMap);
    Assert.assertEquals(propMap, client.getProgramPreferences(Constants.DEFAULT_NAMESPACE, FakeApp.NAME, "flows",
                                                              FakeApp.FLOWS.get(0), false));

    appClient.delete(FakeApp.NAME);
    // deleting the app should have deleted the preferences that were stored. so deploy the app and check
    // if the preferences are empty. we need to deploy the app again since getting preferences of non-existent apps
    // is not allowed by the API.
    appClient.deploy(jarFile);
    propMap.clear();
    Assert.assertEquals(propMap, client.getApplicationPreferences(Constants.DEFAULT_NAMESPACE, FakeApp.NAME, false));
    Assert.assertEquals(propMap, client.getProgramPreferences(Constants.DEFAULT_NAMESPACE, FakeApp.NAME, "flows",
                                                              FakeApp.FLOWS.get(0), false));

    appClient.delete(FakeApp.NAME);
    namespaceClient.delete(invalidNamespace);
  }

  @Test
  public void testDeletingNamespace() throws Exception {
    Map<String, String> propMap = Maps.newHashMap();
    propMap.put("k1", "namespace");
    namespaceClient.create(new NamespaceMeta.Builder().setId("myspace").build());

    client.setNamespacePreferences("myspace", propMap);
    Assert.assertEquals(propMap, client.getNamespacePreferences("myspace", false));
    Assert.assertEquals(propMap, client.getNamespacePreferences("myspace", true));

    namespaceClient.delete("myspace");
    namespaceClient.create(new NamespaceMeta.Builder().setId("myspace").build());
    Assert.assertTrue(client.getNamespacePreferences("myspace", false).isEmpty());
    Assert.assertTrue(client.getNamespacePreferences("myspace", true).isEmpty());

    namespaceClient.delete("myspace");
  }

  @Test(expected = NotFoundException.class)
  public void testInvalidNamespace() throws Exception {
    client.setNamespacePreferences("somespace", ImmutableMap.of("k1", "v1"));
  }

  @Test(expected = NotFoundException.class)
  public void testInvalidApplication() throws Exception {
    client.getApplicationPreferences("somespace", "someapp", true);
  }

  @Test(expected = ProgramNotFoundException.class)
  public void testInvalidProgram() throws Exception {
    client.deleteProgramPreferences("somespace", "someapp", "flows", "myflow");
  }
}