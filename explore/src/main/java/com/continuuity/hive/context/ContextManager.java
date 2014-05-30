package com.continuuity.hive.context;

import com.continuuity.common.conf.CConfiguration;
import com.continuuity.common.conf.Constants;
import com.continuuity.common.guice.ConfigModule;
import com.continuuity.common.guice.DiscoveryRuntimeModule;
import com.continuuity.common.guice.LocationRuntimeModule;
import com.continuuity.common.guice.ZKClientModule;
import com.continuuity.data.runtime.DataFabricModules;
import com.continuuity.data2.dataset2.manager.DatasetManager;
import com.continuuity.data2.transaction.TransactionSystemClient;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.apache.hadoop.conf.Configuration;
import org.apache.twill.zookeeper.ZKClientService;

import java.io.IOException;

/**
 * Stores/creates context for Hive queries to run in MapReduce jobs.
 */
public class ContextManager {
  private static TransactionSystemClient txClient;
  private static DatasetManager datasetManager;

  public static void initialize(TransactionSystemClient txClient, DatasetManager datasetManager) {
    ContextManager.txClient = txClient;
    ContextManager.datasetManager = datasetManager;
  }

  public static TransactionSystemClient getTxClient(Configuration conf) throws IOException {
    if (txClient == null) {
      selfInit(conf);
    }

    return txClient;
  }

  public static DatasetManager getDatasetManager(Configuration conf) throws IOException {
    if (datasetManager == null) {
      selfInit(conf);
    }

    return datasetManager;
  }

  private static void selfInit(Configuration conf) throws IOException {
    // Self init needs to happen only when running in as a MapReduce job.
    // In other cases, ContextManager will be initialized using initialize method.

    CConfiguration cConf = ConfigurationUtil.get(conf, Constants.Explore.CCONF_CODEC_KEY, CConfCodec.INSTANCE);
    Configuration hConf = ConfigurationUtil.get(conf, Constants.Explore.HCONF_CODEC_KEY, HConfCodec.INSTANCE);

    Injector injector = Guice.createInjector(
      new ConfigModule(cConf, hConf),
      new ZKClientModule(),
      new LocationRuntimeModule().getDistributedModules(),
      new DiscoveryRuntimeModule().getDistributedModules(),
      new DataFabricModules(cConf, hConf).getDistributedModules()
    );

    ZKClientService zkClientService = injector.getInstance(ZKClientService.class);
    // TODO: need to stop zkClientService at the end
    zkClientService.startAndWait();

    datasetManager = injector.getInstance(DatasetManager.class);
    txClient = injector.getInstance(TransactionSystemClient.class);
  }
}
