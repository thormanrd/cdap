/*
 * Copyright © 2014-2015 Cask Data, Inc.
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

import co.cask.cdap.api.metrics.RuntimeMetrics;
import co.cask.cdap.client.config.ClientConfig;
import co.cask.cdap.client.util.RESTClient;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.common.exception.UnauthorizedException;
import co.cask.cdap.common.metrics.MetricsTags;
import co.cask.cdap.proto.Id;
import co.cask.cdap.proto.MetricQueryResult;
import co.cask.cdap.proto.MetricTagValue;
import co.cask.common.http.HttpMethod;
import co.cask.common.http.HttpResponse;
import co.cask.common.http.ObjectResponse;
import com.google.common.base.Joiner;
import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.reflect.TypeToken;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.inject.Inject;

/**
 * Provides ways to interact with CDAP Metrics.
 */
public class MetricsClient {

  private final RESTClient restClient;
  private final ClientConfig config;

  @Inject
  public MetricsClient(ClientConfig config, RESTClient restClient) {
    this.config = config;
    this.restClient = restClient;
  }

  public MetricsClient(ClientConfig config) {
    this.config = config;
    this.restClient = new RESTClient(config);
  }

  /**
   * Searches for metrics tags matching the given tags.
   *
   * @param tags the tags to match
   * @return the metrics matching the given tags
   * @throws IOException if a network error occurred
   * @throws UnauthorizedException if the request is not authorized successfully in the gateway server
   */
  public List<MetricTagValue> searchTags(List<String> tags)
    throws IOException, UnauthorizedException {

    List<String> queryParts = Lists.newArrayList();
    queryParts.add("target=tag");
    for (String tag : tags) {
      queryParts.add("tag=" + tag);
    }

    URL url = config.resolveURLV3(String.format("metrics/search?%s", Joiner.on("&").join(queryParts)));
    HttpResponse response = restClient.execute(HttpMethod.POST, url, config.getAccessToken());
    ObjectResponse<List<MetricTagValue>> result = ObjectResponse.fromJsonBody(
      response, new TypeToken<List<MetricTagValue>>() { }.getType());
    return result.getResponseObject();
  }

  /**
   * Searches for metrics matching the given tags.
   *
   * @param tags the tags to match
   * @return the metrics matching the given tags
   * @throws IOException if a network error occurred
   * @throws UnauthorizedException if the request is not authorized successfully in the gateway server
   */
  public List<String> searchMetrics(List<String> tags)
    throws IOException, UnauthorizedException {

    List<String> queryParts = Lists.newArrayList();
    queryParts.add("target=metric");
    for (String tag : tags) {
      queryParts.add("tag=" + tag);
    }

    URL url = config.resolveURLV3(String.format("metrics/search?%s", Joiner.on("&").join(queryParts)));
    HttpResponse response = restClient.execute(HttpMethod.POST, url, config.getAccessToken());
    ObjectResponse<List<String>> result = ObjectResponse.fromJsonBody(
      response, new TypeToken<List<String>>() { }.getType());
    return result.getResponseObject();
  }

  /**
   * Gets the value of the given metrics.
   *
   * @param metrics names of the metrics
   * @param groupBys groupBys for the request
   * @param tags tags for the request
   * @return values of the metrics
   * @throws IOException if a network error occurred
   * @throws UnauthorizedException if the request is not authorized successfully in the gateway server
   */
  public MetricQueryResult query(List<String> metrics, List<String> groupBys, List<String> tags)
    throws IOException, UnauthorizedException {

    List<String> queryParts = Lists.newArrayList();
    queryParts.add("target=tag");
    for (String metric : metrics) {
      queryParts.add("metric=" + metric);
    }
    for (String groupBy : groupBys) {
      queryParts.add("groupBy=" + groupBy);
    }
    for (String tag : tags) {
      queryParts.add("tag=" + tag);
    }

    URL url = config.resolveURLV3(String.format("metrics/query?%s", Joiner.on("&").join(queryParts)));
    HttpResponse response = restClient.execute(HttpMethod.POST, url, config.getAccessToken());
    return ObjectResponse.fromJsonBody(response, MetricQueryResult.class).getResponseObject();
  }

  public RuntimeMetrics getFlowletMetrics(Id.Program flowId, String flowletId) {
    return getMetrics(MetricsTags.flowlet(flowId, flowletId),
                      Constants.Metrics.Name.Flow.FLOWLET_INPUT,
                      Constants.Metrics.Name.Flow.FLOWLET_PROCESSED,
                      Constants.Metrics.Name.Flow.FLOWLET_EXCEPTIONS);
  }

  public RuntimeMetrics getServiceMetrics(Id.Program serviceId) {
    return getMetrics(MetricsTags.service(serviceId),
                      Constants.Metrics.Name.Service.SERVICE_INPUT,
                      Constants.Metrics.Name.Service.SERVICE_PROCESSED,
                      Constants.Metrics.Name.Service.SERVICE_EXCEPTIONS);
  }

  /**
   * Gets the {@link RuntimeMetrics} for a particular metrics context.
   *
   * @param tags the metrics tags
   * @param inputName the metrics key for input counter
   * @param processedName the metrics key for processed counter
   * @param exceptionName the metrics key for exception counter
   * @return the {@link RuntimeMetrics}
   */
  private RuntimeMetrics getMetrics(
    final List<String> tags, final String inputName,
    final String processedName, final String exceptionName) {

    return new RuntimeMetrics() {
      @Override
      public long getInput() {
        return getTotalCounter(tags, inputName);
      }

      @Override
      public long getProcessed() {
        return getTotalCounter(tags, processedName);
      }

      @Override
      public long getException() {
        return getTotalCounter(tags, exceptionName);
      }

      @Override
      public void waitForinput(long count, long timeout, TimeUnit timeoutUnit)
        throws TimeoutException, InterruptedException {
        doWaitFor(inputName, count, timeout, timeoutUnit);
      }

      @Override
      public void waitForProcessed(long count, long timeout, TimeUnit timeoutUnit)
        throws TimeoutException, InterruptedException {
        doWaitFor(processedName, count, timeout, timeoutUnit);
      }

      @Override
      public void waitForException(long count, long timeout, TimeUnit timeoutUnit)
        throws TimeoutException, InterruptedException {
        doWaitFor(exceptionName, count, timeout, timeoutUnit);
      }

      @Override
      public void waitFor(String name, long count,
                          long timeout, TimeUnit timeoutUnit) throws TimeoutException, InterruptedException {
        doWaitFor(name, count, timeout, timeoutUnit);
      }

      private void doWaitFor(String name, long count, long timeout, TimeUnit timeoutUnit)
        throws TimeoutException, InterruptedException {
        long value = getTotalCounter(tags, name);

        // Min sleep time is 10ms, max sleep time is 1 seconds
        long sleepMillis = Math.max(10, Math.min(timeoutUnit.toMillis(timeout) / 10, TimeUnit.SECONDS.toMillis(1)));
        Stopwatch stopwatch = new Stopwatch().start();
        while (value < count && stopwatch.elapsedTime(timeoutUnit) < timeout) {
          TimeUnit.MILLISECONDS.sleep(sleepMillis);
          value = getTotalCounter(tags, name);
        }

        if (value < count) {
          throw new TimeoutException("Time limit reached. Got '" + value + "' instead of '" + count + "'");
        }
      }

      @Override
      public String toString() {
        return String.format("%s; tags=%d, processed=%d, exception=%d",
                             Joiner.on(",").join(tags),
                             getInput(), getProcessed(), getException());
      }
    };
  }

  private long getTotalCounter(List<String> tags, String metricName) {
    try {
      MetricQueryResult result = query(ImmutableList.of(metricName), ImmutableList.<String>of(), tags);
      if (result.getSeries().length == 0) {
        return 0;
      }

      MetricQueryResult.TimeValue[] timeValues = result.getSeries()[0].getData();
      if (timeValues.length == 0) {
        return 0;
      }

      // since it is totals, we know there's one value only
      return timeValues[0].getValue();
    } catch (Exception e) {
      throw Throwables.propagate(e);
    }
  }

}
