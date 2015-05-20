/*
 * Copyright © 2014 Cask Data, Inc.
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

package co.cask.cdap.app.store;

import com.google.common.util.concurrent.Service;

import javax.annotation.Nullable;

/**
 * Stores/Retrieves Information about System Services.
 */
public interface ServiceStore extends Service {

  /**
   * Get the service instance count.
   * @param serviceName Service Name.
   * @return Instance Count (can be null if no value was present for the given ServiceName).
   */
  @Nullable
  Integer getServiceInstance(String serviceName);

  /**
   * Set the service instance count.
   * @param serviceName Service Name.
   * @param instances Instance Count.
   */
  void setServiceInstance(String serviceName, int instances);
}
