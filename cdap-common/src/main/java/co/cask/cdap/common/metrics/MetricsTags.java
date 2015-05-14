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

package co.cask.cdap.common.metrics;

import co.cask.cdap.proto.Id;
import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * Provides ways to obtain the context for certain metrics.
 */
public final class MetricsTags {

  private MetricsTags() {
  }

  public static List<String> flowlet(Id.Program id, String flowletId) {
    return ImmutableList.of(
      "namespace:" + id.getNamespaceId(),
      "app:" + id.getApplicationId(),
      "flow:" + id.getId(),
      "flowlet:" + flowletId);
  }

  public static List<String> service(Id.Program id) {
    return ImmutableList.of(
      "namespace:" + id.getNamespaceId(),
      "app:" + id.getApplicationId(),
      "service:" + id.getId());
  }

  public static List<String> serviceHandler(Id.Program id, String handlerId) {
    return ImmutableList.of(
      "namespace:" + id.getNamespaceId(),
      "app:" + id.getApplicationId(),
      "service:" + id.getId(),
      "handler:" + handlerId);
  }
}
