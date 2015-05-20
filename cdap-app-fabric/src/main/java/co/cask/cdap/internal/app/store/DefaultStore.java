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

package co.cask.cdap.internal.app.store;

import co.cask.cdap.api.ProgramSpecification;
import co.cask.cdap.api.data.stream.StreamSpecification;
import co.cask.cdap.api.dataset.DatasetAdmin;
import co.cask.cdap.api.dataset.DatasetDefinition;
import co.cask.cdap.api.dataset.DatasetProperties;
import co.cask.cdap.api.dataset.table.Table;
import co.cask.cdap.api.flow.FlowSpecification;
import co.cask.cdap.api.flow.FlowletConnection;
import co.cask.cdap.api.flow.FlowletDefinition;
import co.cask.cdap.api.schedule.SchedulableProgramType;
import co.cask.cdap.api.schedule.ScheduleSpecification;
import co.cask.cdap.api.service.ServiceSpecification;
import co.cask.cdap.api.worker.WorkerSpecification;
import co.cask.cdap.app.ApplicationSpecification;
import co.cask.cdap.app.program.Program;
import co.cask.cdap.app.program.Programs;
import co.cask.cdap.app.store.Store;
import co.cask.cdap.archive.ArchiveBundler;
import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.common.namespace.NamespacedLocationFactory;
import co.cask.cdap.data2.datafabric.dataset.DatasetsUtil;
import co.cask.cdap.data2.dataset2.DatasetFramework;
import co.cask.cdap.data2.dataset2.DatasetManagementException;
import co.cask.cdap.data2.dataset2.tx.Transactional;
import co.cask.cdap.internal.app.ForwardingApplicationSpecification;
import co.cask.cdap.internal.app.ForwardingFlowSpecification;
import co.cask.cdap.internal.app.program.ProgramBundle;
import co.cask.cdap.proto.AdapterStatus;
import co.cask.cdap.proto.Id;
import co.cask.cdap.proto.NamespaceMeta;
import co.cask.cdap.proto.ProgramRunStatus;
import co.cask.cdap.proto.ProgramType;
import co.cask.cdap.proto.RunRecord;
import co.cask.cdap.templates.AdapterDefinition;
import co.cask.tephra.TransactionExecutor;
import co.cask.tephra.TransactionExecutorFactory;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import org.apache.twill.filesystem.Location;
import org.apache.twill.filesystem.LocationFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

/**
 * Implementation of the Store that ultimately places data into MetaDataTable.
 */
public class DefaultStore implements Store {
  public static final String APP_META_TABLE = "app.meta";
  private static final Logger LOG = LoggerFactory.getLogger(DefaultStore.class);
  private static final Id.DatasetInstance appMetaDatasetInstanceId =
    Id.DatasetInstance.from(Constants.SYSTEM_NAMESPACE, APP_META_TABLE);

  private final LocationFactory locationFactory;
  private final NamespacedLocationFactory namespacedLocationFactory;
  private final CConfiguration configuration;
  private final DatasetFramework dsFramework;

  private Transactional<AppMds, AppMetadataStore> txnl;

  @Inject
  public DefaultStore(CConfiguration conf,
                      LocationFactory locationFactory,
                      NamespacedLocationFactory namespacedLocationFactory,
                      TransactionExecutorFactory txExecutorFactory,
                      DatasetFramework framework) {
    this.configuration = conf;
    this.locationFactory = locationFactory;
    this.namespacedLocationFactory = namespacedLocationFactory;
    this.dsFramework = framework;

    txnl = Transactional.of(txExecutorFactory, new Supplier<AppMds>() {
      @Override
      public AppMds get() {
        try {
          Table mdsTable = DatasetsUtil.getOrCreateDataset(dsFramework, appMetaDatasetInstanceId, "table",
                                                           DatasetProperties.EMPTY,
                                                           DatasetDefinition.NO_ARGUMENTS, null);
          return new AppMds(mdsTable);
        } catch (Exception e) {
          throw Throwables.propagate(e);
        }
      }
    });
  }

  /**
   * Adds datasets and types to the given {@link DatasetFramework} used by app mds.
   *
   * @param framework framework to add types and datasets to
   */
  public static void setupDatasets(DatasetFramework framework) throws IOException, DatasetManagementException {
    framework.addInstance(Table.class.getName(), Id.DatasetInstance.from(
                            Constants.DEFAULT_NAMESPACE_ID, (Joiner.on(".").join(Constants.SYSTEM_NAMESPACE,
                                                                                 APP_META_TABLE))),
                          DatasetProperties.EMPTY);
  }

  @Nullable
  @Override
  public Program loadProgram(final Id.Program id, ProgramType type) throws IOException {
    ApplicationMeta appMeta = txnl.executeUnchecked(new TransactionExecutor.Function<AppMds, ApplicationMeta>() {
      @Override
      public ApplicationMeta apply(AppMds mds) throws Exception {
        return mds.apps.getApplication(id.getNamespaceId(), id.getApplicationId());
      }
    });

    if (appMeta == null) {
      return null;
    }

    Location programLocation = getProgramLocation(id, type);
    // I guess this can happen when app is being deployed at the moment... todo: should be prevented by framework
    Preconditions.checkArgument(appMeta.getLastUpdateTs() >= programLocation.lastModified(),
                                "Newer program update time than the specification update time. " +
                                "Application must be redeployed");

    return Programs.create(programLocation);
  }

  @Override
  public void compareAndSetStatus(final Id.Program id, final String pid, final ProgramRunStatus expectedStatus,
                                  final ProgramRunStatus updateStatus) {
    Preconditions.checkArgument(expectedStatus != null, "Expected of program run should be defined");
    Preconditions.checkArgument(updateStatus != null, "Updated state of program run should be defined");
    txnl.executeUnchecked(new TransactionExecutor.Function<AppMds, Void>() {
      @Override
      public Void apply(AppMds mds) throws Exception {
        RunRecord target = mds.apps.getRun(id, pid);
        if (target.getStatus() == expectedStatus) {
          long now = System.currentTimeMillis();
          long nowSecs = TimeUnit.MILLISECONDS.toSeconds(now);
          switch (updateStatus) {
            case RUNNING:
              mds.apps.recordProgramStart(id, pid, nowSecs, target.getAdapterName(), target.getTwillRunId());
              break;
            case SUSPENDED:
              mds.apps.recordProgramSuspend(id, pid);
              break;
            case COMPLETED:
            case KILLED:
            case FAILED:
              mds.apps.recordProgramStop(id, pid, nowSecs, updateStatus);
              break;
            default:
              break;
          }
        }
        return null;
      }
    });
  }

  @Override
  public void setStart(final Id.Program id, final String pid, final long startTime, final String adapter,
                       final String twillRunId) {
    txnl.executeUnchecked(new TransactionExecutor.Function<AppMds, Void>() {
      @Override
      public Void apply(AppMds mds) throws Exception {
        mds.apps.recordProgramStart(id, pid, startTime, adapter, twillRunId);
        return null;
      }
    });
  }

  @Override
  public void setStart(Id.Program id, String pid, long startTime) {
    setStart(id, pid, startTime, null, null);
  }

  @Override
  public void setStop(final Id.Program id, final String pid, final long endTime, final ProgramRunStatus runStatus) {
    Preconditions.checkArgument(runStatus != null, "Run state of program run should be defined");
    txnl.executeUnchecked(new TransactionExecutor.Function<AppMds, Void>() {
      @Override
      public Void apply(AppMds mds) throws Exception {
        mds.apps.recordProgramStop(id, pid, endTime, runStatus);
        return null;
      }
    });


    // todo: delete old history data
  }

  @Override
  public void setSuspend(final Id.Program id, final String pid) {
    txnl.executeUnchecked(new TransactionExecutor.Function<AppMds, Void>() {
      @Override
      public Void apply(AppMds mds) throws Exception {
        mds.apps.recordProgramSuspend(id, pid);
        return null;
      }
    });
  }

  @Override
  public void setResume(final Id.Program id, final String pid) {
    txnl.executeUnchecked(new TransactionExecutor.Function<AppMds, Void>() {
      @Override
      public Void apply(AppMds mds) throws Exception {
        mds.apps.recordProgramResumed(id, pid);
        return null;
      }
    });
  }

  @Override
  public List<RunRecord> getRuns(final Id.Program id, final ProgramRunStatus status,
                                 final long startTime, final long endTime, final int limit, final String adapter) {
    return txnl.executeUnchecked(new TransactionExecutor.Function<AppMds, List<RunRecord>>() {
      @Override
      public List<RunRecord> apply(AppMds mds) throws Exception {
        return mds.apps.getRuns(id, status, startTime, endTime, limit, adapter);
      }
    });
  }

  @Override
  public List<RunRecord> getRuns(Id.Program id, ProgramRunStatus status, long startTime, long endTime, int limit) {
    return getRuns(id, status, startTime, endTime, limit, null);
  }

  @Override
  public List<RunRecord> getRuns(final ProgramRunStatus status, final Predicate<RunRecord> filter) {
    return txnl.executeUnchecked(new TransactionExecutor.Function<AppMds, List<RunRecord>>() {
      @Override
      public List<RunRecord> apply(AppMds mds) throws Exception {
        return mds.apps.getRuns(status, filter);
      }
    });
  }

  /**
   * Returns run record for a given run.
   *
   * @param id program id
   * @param runid run id
   * @return run record for runid
   */
  @Override
  public RunRecord getRun(final Id.Program id, final String runid) {
    return txnl.executeUnchecked(new TransactionExecutor.Function<AppMds, RunRecord>() {
      @Override
      public RunRecord apply(AppMds mds) throws Exception {
        return mds.apps.getRun(id, runid);
      }
    });
  }

  @Override
  public void addApplication(final Id.Application id,
                             final ApplicationSpecification spec, final Location appArchiveLocation) {

    txnl.executeUnchecked(new TransactionExecutor.Function<AppMds, Void>() {
      @Override
      public Void apply(AppMds mds) throws Exception {
        mds.apps.writeApplication(id.getNamespaceId(), id.getId(), spec, appArchiveLocation.toURI().toString());

        return null;
      }
    });

  }

  // todo: this method should be moved into DeletedProgramHandlerState, bad design otherwise
  @Override
  public List<ProgramSpecification> getDeletedProgramSpecifications(final Id.Application id,
                                                                    ApplicationSpecification appSpec) {

    ApplicationMeta existing = txnl.executeUnchecked(new TransactionExecutor.Function<AppMds, ApplicationMeta>() {
      @Override
      public ApplicationMeta apply(AppMds mds) throws Exception {
        return mds.apps.getApplication(id.getNamespaceId(), id.getId());
      }
    });

    List<ProgramSpecification> deletedProgramSpecs = Lists.newArrayList();

    if (existing != null) {
      ApplicationSpecification existingAppSpec = existing.getSpec();

      ImmutableMap<String, ProgramSpecification> existingSpec = new ImmutableMap.Builder<String, ProgramSpecification>()
                                                                      .putAll(existingAppSpec.getMapReduce())
                                                                      .putAll(existingAppSpec.getSpark())
                                                                      .putAll(existingAppSpec.getWorkflows())
                                                                      .putAll(existingAppSpec.getFlows())
                                                                      .putAll(existingAppSpec.getServices())
                                                                      .build();

      ImmutableMap<String, ProgramSpecification> newSpec = new ImmutableMap.Builder<String, ProgramSpecification>()
                                                                      .putAll(appSpec.getMapReduce())
                                                                      .putAll(existingAppSpec.getSpark())
                                                                      .putAll(appSpec.getWorkflows())
                                                                      .putAll(appSpec.getFlows())
                                                                      .putAll(appSpec.getServices())
                                                                      .build();


      MapDifference<String, ProgramSpecification> mapDiff = Maps.difference(existingSpec, newSpec);
      deletedProgramSpecs.addAll(mapDiff.entriesOnlyOnLeft().values());
    }

    return deletedProgramSpecs;
  }

  @Override
  public void addStream(final Id.Namespace id, final StreamSpecification streamSpec) {
    txnl.executeUnchecked(new TransactionExecutor.Function<AppMds, Void>() {
      @Override
      public Void apply(AppMds mds) throws Exception {
        mds.apps.writeStream(id.getId(), streamSpec);
        return null;
      }
    });
  }

  @Override
  public StreamSpecification getStream(final Id.Namespace id, final String name) {
    return txnl.executeUnchecked(new TransactionExecutor.Function<AppMds, StreamSpecification>() {
      @Override
      public StreamSpecification apply(AppMds mds) throws Exception {
        return mds.apps.getStream(id.getId(), name);
      }
    });
  }

  @Override
  public Collection<StreamSpecification> getAllStreams(final Id.Namespace id) {
    return txnl.executeUnchecked(new TransactionExecutor.Function<AppMds, Collection<StreamSpecification>>() {
      @Override
      public Collection<StreamSpecification> apply(AppMds mds) throws Exception {
        return mds.apps.getAllStreams(id.getId());
      }
    });
  }

  @Override
  public FlowSpecification setFlowletInstances(final Id.Program id, final String flowletId, final int count) {
    Preconditions.checkArgument(count > 0, "cannot change number of flowlet instances to negative number: " + count);

    LOG.trace("Setting flowlet instances: namespace: {}, application: {}, flow: {}, flowlet: {}, " +
                "new instances count: {}", id.getNamespaceId(), id.getApplicationId(), id.getId(), flowletId, count);

    FlowSpecification flowSpec = txnl.executeUnchecked(new TransactionExecutor.Function<AppMds, FlowSpecification>() {
      @Override
      public FlowSpecification apply(AppMds mds) throws Exception {
        ApplicationSpecification appSpec = getAppSpecOrFail(mds, id);
        ApplicationSpecification newAppSpec = updateFlowletInstancesInAppSpec(appSpec, id, flowletId, count);
        replaceAppSpecInProgramJar(id, newAppSpec, ProgramType.FLOW);

        mds.apps.updateAppSpec(id.getNamespaceId(), id.getApplicationId(), newAppSpec);
        return appSpec.getFlows().get(id.getId());
      }
    });

    LOG.trace("Set flowlet instances: namespace: {}, application: {}, flow: {}, flowlet: {}, instances now: {}",
              id.getNamespaceId(), id.getApplicationId(), id.getId(), flowletId, count);
    return flowSpec;
  }

  @Override
  public int getFlowletInstances(final Id.Program id, final String flowletId) {
    return txnl.executeUnchecked(new TransactionExecutor.Function<AppMds, Integer>() {
      @Override
      public Integer apply(AppMds mds) throws Exception {
        ApplicationSpecification appSpec = getAppSpecOrFail(mds, id);
        FlowSpecification flowSpec = getFlowSpecOrFail(id, appSpec);
        FlowletDefinition flowletDef = getFlowletDefinitionOrFail(flowSpec, flowletId, id);
        return flowletDef.getInstances();
      }
    });

  }

  @Override
  public void setWorkerInstances(final Id.Program id, final int instances) {
    Preconditions.checkArgument(instances > 0, "cannot change number of program " +
      "instances to negative number: " + instances);
    txnl.executeUnchecked(new TransactionExecutor.Function<AppMds, Void>() {
      @Override
      public Void apply(AppMds mds) throws Exception {
        ApplicationSpecification appSpec = getAppSpecOrFail(mds, id);
        WorkerSpecification workerSpec = getWorkerSpecOrFail(id, appSpec);
        WorkerSpecification newSpecification = new WorkerSpecification(workerSpec.getClassName(),
                                                                       workerSpec.getName(),
                                                                       workerSpec.getDescription(),
                                                                       workerSpec.getProperties(),
                                                                       workerSpec.getDatasets(),
                                                                       workerSpec.getResources(),
                                                                       instances);
        ApplicationSpecification newAppSpec = replaceWorkerInAppSpec(appSpec, id, newSpecification);
        replaceAppSpecInProgramJar(id, newAppSpec, ProgramType.WORKER);
        mds.apps.updateAppSpec(id.getNamespaceId(), id.getApplicationId(), newAppSpec);
        return null;
      }
    });

    LOG.trace("Setting program instances: namespace: {}, application: {}, worker: {}, new instances count: {}",
              id.getNamespaceId(), id.getApplicationId(), id.getId(), instances);
  }

  @Override
  public void setServiceInstances(final Id.Program id, final int instances) {
    Preconditions.checkArgument(instances > 0,
                                "cannot change number of program instances to negative number: %s", instances);

    txnl.executeUnchecked(new TransactionExecutor.Function<AppMds, Void>() {
      @Override
      public Void apply(AppMds mds) throws Exception {
        ApplicationSpecification appSpec = getAppSpecOrFail(mds, id);
        ServiceSpecification serviceSpec = getServiceSpecOrFail(id, appSpec);

        // Create a new spec copy from the old one, except with updated instances number
        serviceSpec = new ServiceSpecification(serviceSpec.getClassName(), serviceSpec.getName(),
                                               serviceSpec.getDescription(), serviceSpec.getHandlers(),
                                               serviceSpec.getResources(), instances);

        ApplicationSpecification newAppSpec = replaceServiceSpec(appSpec, id.getId(), serviceSpec);
        replaceAppSpecInProgramJar(id, newAppSpec, ProgramType.SERVICE);

        mds.apps.updateAppSpec(id.getNamespaceId(), id.getApplicationId(), newAppSpec);
        return null;
      }
    });

    LOG.trace("Setting program instances: namespace: {}, application: {}, service: {}, new instances count: {}",
              id.getNamespaceId(), id.getApplicationId(), id.getId(), instances);
  }

  @Override
  public int getServiceInstances(final Id.Program id) {
    return txnl.executeUnchecked(new TransactionExecutor.Function<AppMds, Integer>() {
      @Override
      public Integer apply(AppMds mds) throws Exception {
        ApplicationSpecification appSpec = getAppSpecOrFail(mds, id);
        ServiceSpecification serviceSpec = getServiceSpecOrFail(id, appSpec);
        return serviceSpec.getInstances();
      }
    });
  }

  @Override
  public int getWorkerInstances(final Id.Program id) {
    return txnl.executeUnchecked(new TransactionExecutor.Function<AppMds, Integer>() {
      @Override
      public Integer apply(AppMds mds) throws Exception {
        ApplicationSpecification appSpec = getAppSpecOrFail(mds, id);
        WorkerSpecification workerSpec = getWorkerSpecOrFail(id, appSpec);
        return workerSpec.getInstances();
      }
    });
  }

  @Override
  public void removeApplication(final Id.Application id) {
    LOG.trace("Removing application: namespace: {}, application: {}", id.getNamespaceId(), id.getId());

    txnl.executeUnchecked(new TransactionExecutor.Function<AppMds, Void>() {
      @Override
      public Void apply(AppMds mds) throws Exception {
        mds.apps.deleteApplication(id.getNamespaceId(), id.getId());
        mds.apps.deleteProgramArgs(id.getNamespaceId(), id.getId());
        mds.apps.deleteProgramHistory(id.getNamespaceId(), id.getId());
        return null;
      }
    });
  }

  @Override
  public void removeAllApplications(final Id.Namespace id) {
    LOG.trace("Removing all applications of namespace with id: {}", id.getId());

    txnl.executeUnchecked(new TransactionExecutor.Function<AppMds, Void>() {
      @Override
      public Void apply(AppMds mds) throws Exception {
        mds.apps.deleteApplications(id.getId());
        mds.apps.deleteProgramArgs(id.getId());
        mds.apps.deleteProgramHistory(id.getId());
        return null;
      }
    });
  }

  @Override
  public void removeAll(final Id.Namespace id) {
    LOG.trace("Removing all applications of namespace with id: {}", id.getId());

    txnl.executeUnchecked(new TransactionExecutor.Function<AppMds, Void>() {
      @Override
      public Void apply(AppMds mds) throws Exception {
        mds.apps.deleteApplications(id.getId());
        mds.apps.deleteProgramArgs(id.getId());
        mds.apps.deleteAllStreams(id.getId());
        mds.apps.deleteProgramHistory(id.getId());
        return null;
      }
    });
  }

  @Override
  public void storeRunArguments(final Id.Program id, final Map<String, String> arguments) {
    LOG.trace("Updated program args in mds: id: {}, app: {}, prog: {}, args: {}",
              id.getId(), id.getApplicationId(), id.getId(), Joiner.on(",").withKeyValueSeparator("=").join(arguments));

    txnl.executeUnchecked(new TransactionExecutor.Function<AppMds, Void>() {
      @Override
      public Void apply(AppMds mds) throws Exception {
        mds.apps.writeProgramArgs(id, arguments);
        return null;
      }
    });
  }

  @Override
  public Map<String, String> getRunArguments(final Id.Program id) {
    return txnl.executeUnchecked(new TransactionExecutor.Function<AppMds, Map<String, String>>() {
      @Override
      public Map<String, String> apply(AppMds mds) throws Exception {
        ProgramArgs programArgs = mds.apps.getProgramArgs(id);
        return programArgs == null ? Maps.<String, String>newHashMap() : programArgs.getArgs();
      }
    });
  }

  @Nullable
  @Override
  public ApplicationSpecification getApplication(final Id.Application id) {
    return txnl.executeUnchecked(new TransactionExecutor.Function<AppMds, ApplicationSpecification>() {
      @Override
      public ApplicationSpecification apply(AppMds mds) throws Exception {
        return getApplicationSpec(mds, id);
      }
    });
  }

  @Override
  public Collection<ApplicationSpecification> getAllApplications(final Id.Namespace id) {
    return txnl.executeUnchecked(new TransactionExecutor.Function<AppMds, Collection<ApplicationSpecification>>() {
      @Override
      public Collection<ApplicationSpecification> apply(AppMds mds) throws Exception {
        return Lists.transform(mds.apps.getAllApplications(id.getId()),
                               new Function<ApplicationMeta, ApplicationSpecification>() {
                                 @Override
                                 public ApplicationSpecification apply(ApplicationMeta input) {
                                   return input.getSpec();
                                 }
                               });
      }
    });
  }

  @Nullable
  @Override
  public Location getApplicationArchiveLocation(final Id.Application id) {
    return txnl.executeUnchecked(new TransactionExecutor.Function<AppMds, Location>() {
      @Override
      public Location apply(AppMds mds) throws Exception {
        ApplicationMeta meta = mds.apps.getApplication(id.getNamespaceId(), id.getId());
        return meta == null ? null : locationFactory.create(URI.create(meta.getArchiveLocation()));
      }
    });
  }

  @Override
  public void changeFlowletSteamConnection(final Id.Program flow, final String flowletId,
                                           final String oldValue, final String newValue) {

    Preconditions.checkArgument(flow != null, "flow cannot be null");
    Preconditions.checkArgument(flowletId != null, "flowletId cannot be null");
    Preconditions.checkArgument(oldValue != null, "oldValue cannot be null");
    Preconditions.checkArgument(newValue != null, "newValue cannot be null");

    LOG.trace("Changing flowlet stream connection: namespace: {}, application: {}, flow: {}, flowlet: {}," +
                " old coonnected stream: {}, new connected stream: {}",
              flow.getNamespaceId(), flow.getApplicationId(), flow.getId(), flowletId, oldValue, newValue);

    txnl.executeUnchecked(new TransactionExecutor.Function<AppMds, Void>() {
      @Override
      public Void apply(AppMds mds) throws Exception {
        ApplicationSpecification appSpec = getAppSpecOrFail(mds, flow);

        FlowSpecification flowSpec = getFlowSpecOrFail(flow, appSpec);

        boolean adjusted = false;
        List<FlowletConnection> conns = Lists.newArrayList();
        for (FlowletConnection con : flowSpec.getConnections()) {
          if (FlowletConnection.Type.STREAM == con.getSourceType() &&
            flowletId.equals(con.getTargetName()) &&
            oldValue.equals(con.getSourceName())) {

            conns.add(new FlowletConnection(con.getSourceType(), newValue, con.getTargetName()));
            adjusted = true;
          } else {
            conns.add(con);
          }
        }

        if (!adjusted) {
          throw new IllegalArgumentException(
            String.format("Cannot change stream connection to %s, the connection to be changed is not found," +
                            " namespace: %s, application: %s, flow: %s, flowlet: %s, source stream: %s",
                          newValue, flow.getNamespaceId(), flow.getApplicationId(), flow.getId(), flowletId, oldValue));
        }

        FlowletDefinition flowletDef = getFlowletDefinitionOrFail(flowSpec, flowletId, flow);
        FlowletDefinition newFlowletDef = new FlowletDefinition(flowletDef, oldValue, newValue);
        ApplicationSpecification newAppSpec = replaceInAppSpec(appSpec, flow, flowSpec, newFlowletDef, conns);

        replaceAppSpecInProgramJar(flow, newAppSpec, ProgramType.FLOW);

        Id.Application app = flow.getApplication();
        mds.apps.updateAppSpec(app.getNamespaceId(), app.getId(), newAppSpec);
        return null;
      }
    });


    LOG.trace("Changed flowlet stream connection: namespace: {}, application: {}, flow: {}, flowlet: {}," +
                " old coonnected stream: {}, new connected stream: {}",
              flow.getNamespaceId(), flow.getApplicationId(), flow.getId(), flowletId, oldValue, newValue);

    // todo: change stream "used by" flow mapping in metadata?
  }

  @Override
  public void addSchedule(final Id.Program program, final ScheduleSpecification scheduleSpecification) {
    txnl.executeUnchecked(new TransactionExecutor.Function<AppMds, Void>() {
      @Override
      public Void apply(AppMds mds) throws Exception {
        ApplicationSpecification appSpec = getAppSpecOrFail(mds, program);
        Map<String, ScheduleSpecification> schedules = Maps.newHashMap(appSpec.getSchedules());
        String scheduleName = scheduleSpecification.getSchedule().getName();
        Preconditions.checkArgument(!schedules.containsKey(scheduleName), "Schedule with the name '" +
          scheduleName + "' already exists.");
        schedules.put(scheduleSpecification.getSchedule().getName(), scheduleSpecification);
        ApplicationSpecification newAppSpec = new AppSpecificationWithChangedSchedules(appSpec, schedules);
        // TODO: double check this ProgramType.valueOf()
        replaceAppSpecInProgramJar(program, newAppSpec,
                                   ProgramType.valueOf(scheduleSpecification.getProgram().getProgramType().name()));
        mds.apps.updateAppSpec(program.getNamespaceId(), program.getApplicationId(), newAppSpec);
        return null;
      }
    });
  }

  @Override
  public void deleteSchedule(final Id.Program program, final SchedulableProgramType programType,
                             final String scheduleName) {
    txnl.executeUnchecked(new TransactionExecutor.Function<AppMds, Void>() {
      @Override
      public Void apply(AppMds mds) throws Exception {
        ApplicationSpecification appSpec = getAppSpecOrFail(mds, program);
        Map<String, ScheduleSpecification> schedules = Maps.newHashMap(appSpec.getSchedules());
        ScheduleSpecification removed = schedules.remove(scheduleName);
        if (removed == null) {
          throw new NoSuchElementException("no such schedule @ account id: " + program.getNamespaceId() +
                                             ", app id: " + program.getApplication() +
                                             ", program id: " + program.getId() +
                                             ", schedule name: " + scheduleName);
        }

        ApplicationSpecification newAppSpec = new AppSpecificationWithChangedSchedules(appSpec, schedules);
        // TODO: double check this ProgramType.valueOf()
        replaceAppSpecInProgramJar(program, newAppSpec, ProgramType.valueOf(programType.name()));
        mds.apps.updateAppSpec(program.getNamespaceId(), program.getApplicationId(), newAppSpec);
        return null;
      }
    });
  }

  private static class AppSpecificationWithChangedSchedules extends ForwardingApplicationSpecification {
    private final Map<String, ScheduleSpecification> newSchedules;

    private AppSpecificationWithChangedSchedules(ApplicationSpecification delegate,
                                                 Map<String, ScheduleSpecification> newSchedules) {
      super(delegate);
      this.newSchedules = newSchedules;
    }

    @Override
    public Map<String, ScheduleSpecification> getSchedules() {
      return newSchedules;
    }
  }


  @Override
  public boolean programExists(final Id.Program id, final ProgramType type) {
    return txnl.executeUnchecked(new TransactionExecutor.Function<AppMds, Boolean>() {
      @Override
      public Boolean apply(AppMds mds) throws Exception {
        ApplicationSpecification appSpec = getApplicationSpec(mds, id.getApplication());
        if (appSpec == null) {
          return false;
        }
        ProgramSpecification programSpecification = null;
        try {
          if (type == ProgramType.FLOW) {
            programSpecification = getFlowSpecOrFail(id, appSpec);
          } else if (type == ProgramType.SERVICE) {
            programSpecification = getServiceSpecOrFail(id, appSpec);
          } else if (type == ProgramType.WORKFLOW) {
            programSpecification = appSpec.getWorkflows().get(id.getId());
          } else if (type == ProgramType.MAPREDUCE) {
            programSpecification = appSpec.getMapReduce().get(id.getId());
          } else if (type == ProgramType.SPARK) {
            programSpecification = appSpec.getSpark().get(id.getId());
          } else if (type == ProgramType.WORKER) {
            programSpecification = appSpec.getWorkers().get(id.getId());
          } else if (type == ProgramType.WEBAPP) {
            // no-op
          } else {
            throw new IllegalArgumentException("Invalid ProgramType");
          }
        } catch (NoSuchElementException e) {
          programSpecification = null;
        } catch (Exception e) {
          Throwables.propagate(e);
        }
        return (programSpecification != null);
      }
    });
  }

  @Override
  @Nullable
  public NamespaceMeta createNamespace(final NamespaceMeta metadata) {
    Preconditions.checkArgument(metadata != null, "Namespace metadata cannot be null.");
    return txnl.executeUnchecked(new TransactionExecutor.Function<AppMds, NamespaceMeta>() {
      @Override
      public NamespaceMeta apply(AppMds input) throws Exception {
        Id.Namespace namespaceId = Id.Namespace.from(metadata.getName());
        NamespaceMeta existing = input.apps.getNamespace(namespaceId);
        if (existing != null) {
          return existing;
        }
        input.apps.createNamespace(metadata);
        return null;
      }
    });
  }

  @Override
  public void updateNamespace(final NamespaceMeta metadata) {
    Preconditions.checkArgument(metadata != null, "Namespace metadata cannot be null.");
    txnl.executeUnchecked(new TransactionExecutor.Function<AppMds, Void>() {
      @Override
      public Void apply(AppMds input) throws Exception {
        NamespaceMeta existing = input.apps.getNamespace(Id.Namespace.from(metadata.getName()));
        if (existing != null) {
          input.apps.createNamespace(metadata);
        }
        return null;
      }
    });
  }

  @Override
  @Nullable
  public NamespaceMeta getNamespace(final Id.Namespace id) {
    Preconditions.checkArgument(id != null, "Namespace id cannot be null.");
    return txnl.executeUnchecked(new TransactionExecutor.Function<AppMds, NamespaceMeta>() {
      @Override
      public NamespaceMeta apply(AppMds input) throws Exception {
        return input.apps.getNamespace(id);
      }
    });
  }

  @Override
  @Nullable
  public NamespaceMeta deleteNamespace(final Id.Namespace id) {
    Preconditions.checkArgument(id != null, "Namespace id cannot be null.");
    return txnl.executeUnchecked(new TransactionExecutor.Function<AppMds, NamespaceMeta>() {
      @Override
      public NamespaceMeta apply(AppMds input) throws Exception {
        NamespaceMeta existing = input.apps.getNamespace(id);
        if (existing != null) {
          input.apps.deleteNamespace(id);
        }
        return existing;
      }
    });
  }

  @Override
  public List<NamespaceMeta> listNamespaces() {
    return txnl.executeUnchecked(new TransactionExecutor.Function<AppMds, List<NamespaceMeta>>() {
      @Override
      public List<NamespaceMeta> apply(AppMds input) throws Exception {
        return input.apps.listNamespaces();
      }
    });
  }

  @Override
  public void addAdapter(final Id.Namespace id, final AdapterDefinition adapterSpec) {
    txnl.executeUnchecked(new TransactionExecutor.Function<AppMds, Void>() {
      @Override
      public Void apply(AppMds mds) throws Exception {
        mds.apps.writeAdapter(id, adapterSpec, AdapterStatus.STOPPED);
        return null;
      }
    });
  }

  @Nullable
  @Override
  public AdapterDefinition getAdapter(final Id.Namespace id, final String name) {
    return txnl.executeUnchecked(new TransactionExecutor.Function<AppMds, AdapterDefinition>() {
      @Override
      public AdapterDefinition apply(AppMds mds) throws Exception {
        return mds.apps.getAdapter(id, name);
      }
    });
  }

  @Nullable
  @Override
  public AdapterStatus getAdapterStatus(final Id.Namespace id, final String name) {
    return txnl.executeUnchecked(new TransactionExecutor.Function<AppMds, AdapterStatus>() {
      @Override
      public AdapterStatus apply(AppMds mds) throws Exception {
        return mds.apps.getAdapterStatus(id, name);
      }
    });
  }

  @Nullable
  @Override
  public AdapterStatus setAdapterStatus(final Id.Namespace id, final String name, final AdapterStatus status) {
    return txnl.executeUnchecked(new TransactionExecutor.Function<AppMds, AdapterStatus>() {
      @Override
      public AdapterStatus apply(AppMds mds) throws Exception {
        return mds.apps.setAdapterStatus(id, name, status);
      }
    });
  }

  @Override
  public Collection<AdapterDefinition> getAllAdapters(final Id.Namespace id) {
    return txnl.executeUnchecked(new TransactionExecutor.Function<AppMds, Collection<AdapterDefinition>>() {
      @Override
      public Collection<AdapterDefinition> apply(AppMds mds) throws Exception {
        return mds.apps.getAllAdapters(id);
      }
    });
  }

  @Override
  public void removeAdapter(final Id.Namespace id, final String name) {
    txnl.executeUnchecked(new TransactionExecutor.Function<AppMds, Void>() {
      @Override
      public Void apply(AppMds mds) throws Exception {
        mds.apps.deleteAdapter(id, name);
        return null;
      }
    });
  }

  @Override
  public void removeAllAdapters(final Id.Namespace id) {
    txnl.executeUnchecked(new TransactionExecutor.Function<AppMds, Void>() {
      @Override
      public Void apply(AppMds mds) throws Exception {
        mds.apps.deleteAllAdapters(id);
        return null;
      }
    });
  }

  @Override
  public void setWorkflowProgramStart(final Id.Program programId, final String programRunId, final String workflow,
                                      final String workflowRunId, final String workflowNodeId,
                                      final long startTimeInSeconds, final String adapter, final String twillRunId) {
    txnl.executeUnchecked(new TransactionExecutor.Function<AppMds, Void>() {
      @Override
      public Void apply(AppMds mds) throws Exception {
        mds.apps.recordWorkflowProgramStart(programId, programRunId, workflow, workflowRunId, workflowNodeId,
                                            startTimeInSeconds, adapter, twillRunId);
        return null;
      }
    });
  }

  @VisibleForTesting
  void clear() throws Exception {
    DatasetAdmin admin = dsFramework.getAdmin(appMetaDatasetInstanceId, null);
    if (admin != null) {
      admin.truncate();
    }
  }

  /**
   * @return The {@link Location} of the given program.
   * @throws RuntimeException if program can't be found.
   */
  private Location getProgramLocation(Id.Program id, ProgramType type) throws IOException {
    String appFabricOutputDir = configuration.get(Constants.AppFabric.OUTPUT_DIR,
                                                  System.getProperty("java.io.tmpdir"));
    return Programs.programLocation(namespacedLocationFactory, appFabricOutputDir, id, type);
  }

  private ApplicationSpecification getApplicationSpec(AppMds mds, Id.Application id) {
    ApplicationMeta meta = mds.apps.getApplication(id.getNamespaceId(), id.getId());
    return meta == null ? null : meta.getSpec();
  }

  private static ApplicationSpecification replaceServiceSpec(ApplicationSpecification appSpec,
                                                             String serviceName,
                                                             ServiceSpecification serviceSpecification) {
    return new ApplicationSpecificationWithChangedServices(appSpec, serviceName, serviceSpecification);
  }

  private static final class ApplicationSpecificationWithChangedServices extends ForwardingApplicationSpecification {
    private final String serviceName;
    private final ServiceSpecification serviceSpecification;

    private ApplicationSpecificationWithChangedServices(ApplicationSpecification delegate,
                                                        String serviceName, ServiceSpecification serviceSpecification) {
      super(delegate);
      this.serviceName = serviceName;
      this.serviceSpecification = serviceSpecification;
    }

    @Override
    public Map<String, ServiceSpecification> getServices() {
      Map<String, ServiceSpecification> services = Maps.newHashMap(super.getServices());
      services.put(serviceName, serviceSpecification);
      return services;
    }
  }

  private void replaceAppSpecInProgramJar(Id.Program id, ApplicationSpecification appSpec, ProgramType type) {
    try {
      Location programLocation = getProgramLocation(id, type);
      ArchiveBundler bundler = new ArchiveBundler(programLocation);

      Program program = Programs.create(programLocation);
      String className = program.getMainClassName();

      Location tmpProgramLocation = programLocation.getTempFile("");
      try {
        ProgramBundle.create(id.getApplication(), bundler, tmpProgramLocation, id.getId(), className, type, appSpec);

        Location movedTo = tmpProgramLocation.renameTo(programLocation);
        if (movedTo == null) {
          throw new RuntimeException("Could not replace program jar with the one with updated app spec, " +
                                       "original program file: " + programLocation.toURI() +
                                       ", was trying to replace with file: " + tmpProgramLocation.toURI());
        }
      } finally {
        if (tmpProgramLocation != null && tmpProgramLocation.exists()) {
          tmpProgramLocation.delete();
        }
      }
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }
  }

  private static FlowletDefinition getFlowletDefinitionOrFail(FlowSpecification flowSpec,
                                                              String flowletId, Id.Program id) {
    FlowletDefinition flowletDef = flowSpec.getFlowlets().get(flowletId);
    if (flowletDef == null) {
      throw new NoSuchElementException("no such flowlet @ namespace id: " + id.getNamespaceId() +
                                           ", app id: " + id.getApplication() +
                                           ", flow id: " + id.getId() +
                                           ", flowlet id: " + flowletId);
    }
    return flowletDef;
  }

  private static FlowSpecification getFlowSpecOrFail(Id.Program id, ApplicationSpecification appSpec) {
    FlowSpecification flowSpec = appSpec.getFlows().get(id.getId());
    if (flowSpec == null) {
      throw new NoSuchElementException("no such flow @ namespace id: " + id.getNamespaceId() +
                                           ", app id: " + id.getApplication() +
                                           ", flow id: " + id.getId());
    }
    return flowSpec;
  }

  private static ServiceSpecification getServiceSpecOrFail(Id.Program id, ApplicationSpecification appSpec) {
    ServiceSpecification spec = appSpec.getServices().get(id.getId());
    if (spec == null) {
      throw new NoSuchElementException("no such service @ namespace id: " + id.getNamespaceId() +
                                           ", app id: " + id.getApplication() +
                                           ", service id: " + id.getId());
    }
    return spec;
  }

  private static WorkerSpecification getWorkerSpecOrFail(Id.Program id, ApplicationSpecification appSpec) {
    WorkerSpecification workerSpecification = appSpec.getWorkers().get(id.getId());
    if (workerSpecification == null) {
      throw new NoSuchElementException("no such worker @ namespace id: " + id.getNamespaceId() +
                                         ", app id: " + id.getApplication() +
                                         ", worker id: " + id.getId());
    }
    return workerSpecification;
  }

  private static ApplicationSpecification updateFlowletInstancesInAppSpec(ApplicationSpecification appSpec,
                                                                          Id.Program id, String flowletId, int count) {

    FlowSpecification flowSpec = getFlowSpecOrFail(id, appSpec);
    FlowletDefinition flowletDef = getFlowletDefinitionOrFail(flowSpec, flowletId, id);

    final FlowletDefinition adjustedFlowletDef = new FlowletDefinition(flowletDef, count);
    return replaceFlowletInAppSpec(appSpec, id, flowSpec, adjustedFlowletDef);
  }

  private ApplicationSpecification getAppSpecOrFail(AppMds mds, Id.Program id) {
    ApplicationSpecification appSpec = getApplicationSpec(mds, id.getApplication());
    if (appSpec == null) {
      throw new NoSuchElementException("no such application @ namespace id: " + id.getNamespaceId() +
                                           ", app id: " + id.getApplication().getId());
    }
    return appSpec;
  }

  private static ApplicationSpecification replaceInAppSpec(final ApplicationSpecification appSpec,
                                                    final Id.Program id,
                                                    final FlowSpecification flowSpec,
                                                    final FlowletDefinition adjustedFlowletDef,
                                                    final List<FlowletConnection> connections) {
    // as app spec is immutable we have to do this trick
    return replaceFlowInAppSpec(appSpec, id,
                                new FlowSpecificationWithChangedFlowletsAndConnections(flowSpec,
                                                                                       adjustedFlowletDef,
                                                                                       connections));
  }

  private static class FlowSpecificationWithChangedFlowlets extends ForwardingFlowSpecification {
    private final FlowletDefinition adjustedFlowletDef;

    private FlowSpecificationWithChangedFlowlets(FlowSpecification delegate,
                                                 FlowletDefinition adjustedFlowletDef) {
      super(delegate);
      this.adjustedFlowletDef = adjustedFlowletDef;
    }

    @Override
    public Map<String, FlowletDefinition> getFlowlets() {
      Map<String, FlowletDefinition> flowlets = Maps.newHashMap(super.getFlowlets());
      flowlets.put(adjustedFlowletDef.getFlowletSpec().getName(), adjustedFlowletDef);
      return flowlets;
    }
  }

  private static final class FlowSpecificationWithChangedFlowletsAndConnections
    extends FlowSpecificationWithChangedFlowlets {

    private final List<FlowletConnection> connections;

    private FlowSpecificationWithChangedFlowletsAndConnections(FlowSpecification delegate,
                                                               FlowletDefinition adjustedFlowletDef,
                                                               List<FlowletConnection> connections) {
      super(delegate, adjustedFlowletDef);
      this.connections = connections;
    }

    @Override
    public List<FlowletConnection> getConnections() {
      return connections;
    }
  }

  private static ApplicationSpecification replaceFlowletInAppSpec(final ApplicationSpecification appSpec,
                                                           final Id.Program id,
                                                           final FlowSpecification flowSpec,
                                                           final FlowletDefinition adjustedFlowletDef) {
    // as app spec is immutable we have to do this trick
    return replaceFlowInAppSpec(appSpec, id, new FlowSpecificationWithChangedFlowlets(flowSpec, adjustedFlowletDef));
  }

  private static ApplicationSpecification replaceFlowInAppSpec(final ApplicationSpecification appSpec,
                                                               final Id.Program id,
                                                               final FlowSpecification newFlowSpec) {
    // as app spec is immutable we have to do this trick
    return new ApplicationSpecificationWithChangedFlows(appSpec, id.getId(), newFlowSpec);
  }

  private static final class ApplicationSpecificationWithChangedFlows extends ForwardingApplicationSpecification {
    private final FlowSpecification newFlowSpec;
    private final String flowId;

    private ApplicationSpecificationWithChangedFlows(ApplicationSpecification delegate,
                                                     String flowId, FlowSpecification newFlowSpec) {
      super(delegate);
      this.newFlowSpec = newFlowSpec;
      this.flowId = flowId;
    }

    @Override
    public Map<String, FlowSpecification> getFlows() {
      Map<String, FlowSpecification> flows = Maps.newHashMap(super.getFlows());
      flows.put(flowId, newFlowSpec);
      return flows;
    }
  }

  private static ApplicationSpecification replaceWorkerInAppSpec(final ApplicationSpecification appSpec,
                                                                 final Id.Program id,
                                                                 final WorkerSpecification workerSpecification) {
    return new ApplicationSpecificationWithChangedWorkers(appSpec, id.getId(), workerSpecification);
  }

  private static final class ApplicationSpecificationWithChangedWorkers extends ForwardingApplicationSpecification {
    private final String workerId;
    private final WorkerSpecification workerSpecification;

    private ApplicationSpecificationWithChangedWorkers(ApplicationSpecification delegate, String workerId,
                                                       WorkerSpecification workerSpec) {
      super(delegate);
      this.workerId = workerId;
      this.workerSpecification = workerSpec;
    }

    @Override
    public Map<String, WorkerSpecification> getWorkers() {
      Map<String, WorkerSpecification> workers = Maps.newHashMap(super.getWorkers());
      workers.put(workerId, workerSpecification);
      return workers;
    }
  }

  private static final class AppMds implements Iterable<AppMetadataStore> {
    private final AppMetadataStore apps;

    private AppMds(Table mdsTable) {
      this.apps = new AppMetadataStore(mdsTable);
    }

    @Override
    public Iterator<AppMetadataStore> iterator() {
      return Iterators.singletonIterator(apps);
    }
  }
}
