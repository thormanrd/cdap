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

package co.cask.cdap.data2.registry;

import co.cask.cdap.api.dataset.DatasetProperties;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.data2.datafabric.dataset.DatasetsUtil;
import co.cask.cdap.data2.dataset2.DatasetFramework;
import co.cask.cdap.data2.dataset2.tx.Transactional;
import co.cask.cdap.proto.Id;
import co.cask.tephra.TransactionExecutor;
import co.cask.tephra.TransactionExecutorFactory;
import com.google.common.base.Supplier;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterators;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

/**
 * Store program/adapter -> dataset/stream usage information.
 *
 * TODO: Reduce duplication between this and {@link UsageDataset}.
 */
public class UsageRegistry {

  private static final Logger LOG = LoggerFactory.getLogger(UsageRegistry.class);

  private static final Id.DatasetInstance USAGE_INSTANCE_ID =
    Id.DatasetInstance.from(Constants.SYSTEM_NAMESPACE_ID, "usage.registry");

  private final Transactional<UsageDatasetIterable, UsageDataset> txnl;

  @Inject
  public UsageRegistry(TransactionExecutorFactory txExecutorFactory, final DatasetFramework datasetFramework) {
    txnl = Transactional.of(txExecutorFactory, new Supplier<UsageDatasetIterable>() {
      @Override
      public UsageDatasetIterable get() {
        try {
          UsageDataset usageDataset = DatasetsUtil.getOrCreateDataset(datasetFramework,
                                                                      USAGE_INSTANCE_ID,
                                                                      UsageDataset.class.getSimpleName(),
                                                                      DatasetProperties.EMPTY, null, null);
          return new UsageDatasetIterable(usageDataset);
        } catch (Exception e) {
          LOG.error("Failed to access usage table", e);
          throw Throwables.propagate(e);
        }
      }
    });
  }

  /**
   * Registers usage of a stream by multiple ids.
   *
   * @param users the users of the stream
   * @param streamId the stream
   */
  public void registerAll(final Iterable<? extends Id> users, final Id.Stream streamId) {
    txnl.executeUnchecked(new TransactionExecutor.Function<UsageDatasetIterable, Void>() {
      @Override
      public Void apply(UsageDatasetIterable input) throws Exception {
        for (Id user : users) {
          // TODO: CDAP-2251: remove redundancy
          if (user instanceof Id.Program) {
            register((Id.Program) user, streamId);
          } else if (user instanceof Id.Adapter) {
            register((Id.Adapter) user, streamId);
          }
        }
        return null;
      }
    });
  }

  /**
   * Register usage of a stream by an id.
   *
   * @param user the user of the stream
   * @param streamId the stream
   */
  public void register(Id user, Id.Stream streamId) {
    registerAll(Collections.singleton(user), streamId);
  }

  /**
   * Registers usage of a dataset by multiple ids.
   *
   * @param users the users of the dataset
   * @param datasetId the dataset
   */
  public void registerAll(final Iterable<? extends Id> users, final Id.DatasetInstance datasetId) {
    txnl.executeUnchecked(new TransactionExecutor.Function<UsageDatasetIterable, Void>() {
      @Override
      public Void apply(UsageDatasetIterable input) throws Exception {
        for (Id user : users) {
          // TODO: CDAP-2251: remove redundancy
          if (user instanceof Id.Program) {
            register((Id.Program) user, datasetId);
          } else if (user instanceof Id.Adapter) {
            register((Id.Adapter) user, datasetId);
          }
        }
        return null;
      }
    });
  }

  /**
   * Registers usage of a dataset by multiple ids.
   *
   * @param user the user of the dataset
   * @param datasetId the dataset
   */
  public void register(Id user, Id.DatasetInstance datasetId) {
    registerAll(Collections.singleton(user), datasetId);
  }

  /**
   * Registers usage of a dataset by a program.
   *
   * @param programId program
   * @param datasetInstanceId dataset
   */
  public void register(final Id.Program programId, final Id.DatasetInstance datasetInstanceId) {
    txnl.executeUnchecked(new TransactionExecutor.Function<UsageDatasetIterable, Void>() {
      @Override
      public Void apply(UsageDatasetIterable input) throws Exception {
        input.getUsageDataset().register(programId, datasetInstanceId);
        return null;
      }
    });
  }

  /**
   * Registers usage of a dataset by an adapter.
   *
   * @param adapterId adapter
   * @param datasetInstanceId dataset
   */
  public void register(final Id.Adapter adapterId, final Id.DatasetInstance datasetInstanceId) {
    txnl.executeUnchecked(new TransactionExecutor.Function<UsageDatasetIterable, Void>() {
      @Override
      public Void apply(UsageDatasetIterable input) throws Exception {
        input.getUsageDataset().register(adapterId, datasetInstanceId);
        return null;
      }
    });
  }

  /**
   * Registers usage of a stream by a program.
   *
   * @param programId program
   * @param streamId stream
   */
  public void register(final Id.Program programId, final Id.Stream streamId) {
    txnl.executeUnchecked(new TransactionExecutor.Function<UsageDatasetIterable, Void>() {
      @Override
      public Void apply(UsageDatasetIterable input) throws Exception {
        input.getUsageDataset().register(programId, streamId);
        return null;
      }
    });
  }

  /**
   * Registers usage of a stream by an adapter.
   *
   * @param adapterId adapter
   * @param streamId stream
   */
  public void register(final Id.Adapter adapterId, final Id.Stream streamId) {
    txnl.executeUnchecked(new TransactionExecutor.Function<UsageDatasetIterable, Void>() {
      @Override
      public Void apply(UsageDatasetIterable input) throws Exception {
        input.getUsageDataset().register(adapterId, streamId);
        return null;
      }
    });
  }

  /**
   * Unregisters all usage information of an application.
   *
   * @param applicationId application
   */
  public void unregister(final Id.Application applicationId) {
    txnl.executeUnchecked(new TransactionExecutor.Function<UsageDatasetIterable, Void>() {
      @Override
      public Void apply(UsageDatasetIterable input) throws Exception {
        input.getUsageDataset().unregister(applicationId);
        return null;
      }
    });
  }

  /**
   * Unregisters all usage information of an adapter.
   *
   * @param adapterId application
   */
  public void unregister(final Id.Adapter adapterId) {
    txnl.executeUnchecked(new TransactionExecutor.Function<UsageDatasetIterable, Void>() {
      @Override
      public Void apply(UsageDatasetIterable input) throws Exception {
        input.getUsageDataset().unregister(adapterId);
        return null;
      }
    });
  }

  public Set<Id.DatasetInstance> getDatasets(final Id.Application id) {
    return txnl.executeUnchecked(new TransactionExecutor.Function<UsageDatasetIterable, Set<Id.DatasetInstance>>() {
      @Override
      public Set<Id.DatasetInstance> apply(UsageDatasetIterable input) throws Exception {
        return input.getUsageDataset().getDatasets(id);
      }
    });
  }

  public Set<Id.Stream> getStreams(final Id.Application id) {
    return txnl.executeUnchecked(new TransactionExecutor.Function<UsageDatasetIterable, Set<Id.Stream>>() {
      @Override
      public Set<Id.Stream> apply(UsageDatasetIterable input) throws Exception {
        return input.getUsageDataset().getStreams(id);
      }
    });
  }

  public Set<Id.DatasetInstance> getDatasets(final Id.Program id) {
    return txnl.executeUnchecked(new TransactionExecutor.Function<UsageDatasetIterable, Set<Id.DatasetInstance>>() {
      @Override
      public Set<Id.DatasetInstance> apply(UsageDatasetIterable input) throws Exception {
        return input.getUsageDataset().getDatasets(id);
      }
    });
  }

  public Set<Id.Stream> getStreams(final Id.Program id) {
    return txnl.executeUnchecked(new TransactionExecutor.Function<UsageDatasetIterable, Set<Id.Stream>>() {
      @Override
      public Set<Id.Stream> apply(UsageDatasetIterable input) throws Exception {
        return input.getUsageDataset().getStreams(id);
      }
    });
  }

  public Set<Id.DatasetInstance> getDatasets(final Id.Adapter id) {
    return txnl.executeUnchecked(new TransactionExecutor.Function<UsageDatasetIterable, Set<Id.DatasetInstance>>() {
      @Override
      public Set<Id.DatasetInstance> apply(UsageDatasetIterable input) throws Exception {
        return input.getUsageDataset().getDatasets(id);
      }
    });
  }

  public Set<Id.Stream> getStreams(final Id.Adapter id) {
    return txnl.executeUnchecked(new TransactionExecutor.Function<UsageDatasetIterable, Set<Id.Stream>>() {
      @Override
      public Set<Id.Stream> apply(UsageDatasetIterable input) throws Exception {
        return input.getUsageDataset().getStreams(id);
      }
    });
  }

  public Set<Id.Program> getPrograms(final Id.Stream id) {
    return txnl.executeUnchecked(new TransactionExecutor.Function<UsageDatasetIterable, Set<Id.Program>>() {
      @Override
      public Set<Id.Program> apply(UsageDatasetIterable input) throws Exception {
        return input.getUsageDataset().getPrograms(id);
      }
    });
  }

  public Set<Id.Adapter> getAdapters(final Id.Stream id) {
    return txnl.executeUnchecked(new TransactionExecutor.Function<UsageDatasetIterable, Set<Id.Adapter>>() {
      @Override
      public Set<Id.Adapter> apply(UsageDatasetIterable input) throws Exception {
        return input.getUsageDataset().getAdapters(id);
      }
    });
  }

  public Set<Id.Program> getPrograms(final Id.DatasetInstance id) {
    return txnl.executeUnchecked(new TransactionExecutor.Function<UsageDatasetIterable, Set<Id.Program>>() {
      @Override
      public Set<Id.Program> apply(UsageDatasetIterable input) throws Exception {
        return input.getUsageDataset().getPrograms(id);
      }
    });
  }

  public Set<Id.Adapter> getAdapters(final Id.DatasetInstance id) {
    return txnl.executeUnchecked(new TransactionExecutor.Function<UsageDatasetIterable, Set<Id.Adapter>>() {
      @Override
      public Set<Id.Adapter> apply(UsageDatasetIterable input) throws Exception {
        return input.getUsageDataset().getAdapters(id);
      }
    });
  }

  /**
   * For passing {@link UsageDataset} to {@link Transactional#of}.
   */
  public static final class UsageDatasetIterable implements Iterable<UsageDataset> {
    private final UsageDataset usageDataset;

    private UsageDatasetIterable(UsageDataset usageDataset) {
      this.usageDataset = usageDataset;
    }

    public UsageDataset getUsageDataset() {
      return usageDataset;
    }

    @Override
    public Iterator<UsageDataset> iterator() {
      return Iterators.singletonIterator(usageDataset);
    }
  }
}
