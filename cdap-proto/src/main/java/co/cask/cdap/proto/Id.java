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

package co.cask.cdap.proto;

import com.google.common.base.CharMatcher;
import com.google.common.base.Charsets;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;

import javax.annotation.Nullable;

/**
 * Contains collection of classes representing different types of Ids.
 */
public abstract class Id {

  public static String getType(Class<? extends Id> type) {
    return type.getSimpleName().toLowerCase();
  }

  private static final CharMatcher namespaceMatcher =
    CharMatcher.inRange('A', 'Z')
    .or(CharMatcher.inRange('a', 'z'))
    .or(CharMatcher.inRange('0', '9'))
    .or(CharMatcher.is('_'));
  // Allow hyphens for other ids.
  private static final CharMatcher idMatcher = namespaceMatcher.or(CharMatcher.is('-'));
  // Allow '.' and '$' for dataset ids since they can be fully qualified class names
  private static final CharMatcher datasetIdCharMatcher = idMatcher.or(CharMatcher.is('.')).or(CharMatcher.is('$'));

  private static boolean isValidNamespaceId(String name) {
    return namespaceMatcher.matchesAllOf(name);
  }

  private static boolean isValidId(String name) {
    return idMatcher.matchesAllOf(name);
  }

  private static boolean isValidDatasetId(String datasetId) {
    return datasetIdCharMatcher.matchesAllOf(datasetId);
  }

  public String getIdType() {
    return getType(this.getClass());
  }

  protected String getIdForRep() {
    return getId();
  }

  public String getIdRep() {
    Id parent = getParent();
    if (parent == null) {
      return getIdType() + ":" + getIdForRep();
    } else {
      return parent.getIdRep() + "/" + getIdType() + ":" + getIdForRep();
    }
  }

  @Override
  public String toString() {
    return getIdRep();
  }

  @Nullable
  protected abstract Id getParent();

  public abstract String getId();

  /**
   * Indicates that the ID belongs to a namespace.
   */
  public abstract static class NamespacedId extends Id {
    public abstract Namespace getNamespace();
  }

  /**
   * Uniquely identifies a Query Handle.
   */
  public static final class QueryHandle extends Id {
    private final String id;

    private QueryHandle(String id) {
      Preconditions.checkNotNull(id, "id cannot be null.");
      this.id = id;
    }

    public static QueryHandle from(String id) {
      return new QueryHandle(id);
    }

    @Nullable
    @Override
    protected Id getParent() {
      return null;
    }

    @Override
    public String getId() {
      return id;
    }
  }

  /**
   * Uniquely identifies a System Service.
   */
  public static final class SystemService extends Id {
    private final String id;

    private SystemService(String id) {
      Preconditions.checkNotNull(id, "id cannot be null.");
      this.id = id;
    }

    public static SystemService from(String id) {
      return new SystemService(id);
    }

    @Nullable
    @Override
    protected Id getParent() {
      return null;
    }

    @Override
    public String getId() {
      return id;
    }
  }

  /**
   * Uniquely identifies a Namespace.
   */
  public static final class Namespace extends Id {
    public static final Namespace DEFAULT = Id.Namespace.from("default");

    private final String id;

    public Namespace(String id) {
      Preconditions.checkNotNull(id, "Namespace '" + id + "' cannot be null.");
      Preconditions.checkArgument(isValidNamespaceId(id), "Namespace '" + id + "' has an incorrect format.");
      this.id = id;
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      return id.equals(((Namespace) o).id);
    }

    @Override
    public int hashCode() {
      return id.hashCode();
    }

    public static Namespace from(String namespace) {
      return new Namespace(namespace);
    }

    @Nullable
    @Override
    protected Id getParent() {
      return null;
    }

    // TODO: remove (use super toString() which returns getIdRep())
    @Override
    public String toString() {
      return id;
    }
  }

  /**
   * Uniquely identifies an Adapter Type.
   */
  public static final class AdapterType extends NamespacedId {
    private final Namespace namespace;
    private final String adapterTypeId;

    public AdapterType(final Namespace namespace, final String adapterTypeId) {
      Preconditions.checkNotNull(namespace, "namespace cannot be null.");
      Preconditions.checkNotNull(adapterTypeId, "adapterTypeId cannot be null.");
      this.namespace = namespace;
      this.adapterTypeId = adapterTypeId;
    }

    @Override
    public Namespace getNamespace() {
      return namespace;
    }

    public String getNamespaceId() {
      return namespace.getId();
    }

    @Override
    public String getId() {
      return adapterTypeId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      AdapterType that = (AdapterType) o;
      return namespace.equals(that.namespace) && adapterTypeId.equals(that.adapterTypeId);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(namespace, adapterTypeId);
    }

    public static AdapterType from(Namespace id, String adapterTypeId) {
      return new AdapterType(id, adapterTypeId);
    }

    public static AdapterType from(String namespaceId, String adapterTypeId) {
      return new AdapterType(Namespace.from(namespaceId), adapterTypeId);
    }

    @Override
    public Id getParent() {
      return namespace;
    }
  }

  /**
   * Uniquely identifies an Application Template.
   */
  public static final class ApplicationTemplate extends Id {
    private final String template;

    public ApplicationTemplate(final String template) {
      Preconditions.checkNotNull(template, "template cannot be null.");
      this.template = template;
    }

    @Nullable
    @Override
    protected Id getParent() {
      return null;
    }

    @Override
    public String getId() {
      return template;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      Application that = (Application) o;
      return template.equals(that.applicationId);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(template);
    }

    public static ApplicationTemplate from(String template) {
      return new ApplicationTemplate(template);
    }
  }

  /**
   * Uniquely identifies an Application.
   */
  public static final class Application extends NamespacedId {
    private final Namespace namespace;
    private final String applicationId;

    public Application(final Namespace namespace, final String applicationId) {
      Preconditions.checkNotNull(namespace, "Namespace cannot be null.");
      Preconditions.checkNotNull(applicationId, "Application cannot be null.");
      Preconditions.checkArgument(isValidId(applicationId), "Invalid Application ID.");
      this.namespace = namespace;
      this.applicationId = applicationId;
    }

    @Override
    public Namespace getNamespace() {
      return namespace;
    }

    public String getNamespaceId() {
      return namespace.getId();
    }

    @Override
    public String getId() {
      return applicationId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      Application that = (Application) o;
      return namespace.equals(that.namespace) && applicationId.equals(that.applicationId);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(namespace, applicationId);
    }

    public static Application from(Namespace id, String applicationId) {
      return new Application(id, applicationId);
    }

    public static Application from(String namespaceId, String applicationId) {
      return new Application(Namespace.from(namespaceId), applicationId);
    }

    public static Application from(Adapter adapter, String adapterSpecType) {
      return new Application(adapter.getNamespace(), adapterSpecType);
    }

    @Override
    public Id getParent() {
      return namespace;
    }

    public static Application fromStrings(String[] strings, int position) {
      Preconditions.checkArgument(position == 1);
      String[] tokens = strings[position].split(":");
      Preconditions.checkArgument(tokens.length == 2);

      String[] nextTokens = strings[position - 1].split(":");
      Preconditions.checkArgument(nextTokens.length == 2);
      return from(Namespace.from(nextTokens[1]), tokens[1]);
    }

    public static Application fromStrings(String[] strings) {
      return fromStrings(strings, strings.length - 1);
    }
  }

  /**
   * Uniquely identifies an Adapter.
   */
  public static final class Adapter extends NamespacedId {
    private final Namespace namespace;
    private final String adapterId;

    public Adapter(final Namespace namespace, final String adapterId) {
      Preconditions.checkNotNull(namespace, "namespace cannot be null.");
      Preconditions.checkNotNull(adapterId, "adapterId cannot be null.");
      this.namespace = namespace;
      this.adapterId = adapterId;
    }

    @Override
    public Namespace getNamespace() {
      return namespace;
    }

    public String getNamespaceId() {
      return namespace.getId();
    }

    @Override
    public String getId() {
      return adapterId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      Adapter that = (Adapter) o;
      return namespace.equals(that.namespace) && adapterId.equals(that.adapterId);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(namespace, adapterId);
    }

    public static Adapter from(Namespace id, String adapterId) {
      return new Adapter(id, adapterId);
    }

    public static Adapter from(String namespaceId, String adapterId) {
      return new Adapter(Namespace.from(namespaceId), adapterId);
    }

    public static Adapter fromStrings(String[] strings, int position) {
      Preconditions.checkArgument(position == 1);
      String[] tokens = strings[position].split(":");
      Preconditions.checkArgument(tokens.length == 2);

      String[] nextTokens = strings[position - 1].split(":");
      Preconditions.checkArgument(nextTokens.length == 2);
      return from(Namespace.from(nextTokens[1]), tokens[1]);
    }

    public static Adapter fromStrings(String[] strings) {
      return fromStrings(strings, strings.length - 1);
    }

    @Override
    public Id getParent() {
      return namespace;
    }
  }


  /**
   * Uniquely identifies a Program run.
   */
  public static class Run extends NamespacedId {

    private final Program program;
    private final String id;

    public Run(Program program, String id) {
      this.program = program;
      this.id = id;
    }

    public Program getProgram() {
      return program;
    }

    @Override
    public Namespace getNamespace() {
      return program.getNamespace();
    }

    @Nullable
    @Override
    protected Id getParent() {
      return program;
    }

    @Override
    public String getId() {
      return id;
    }
  }

  /**
   * Uniquely identifies a Program.
   */
  public static class Program extends NamespacedId {
    private final Application application;
    private final ProgramType type;
    private final String id;

    public Program(Application application, ProgramType type, final String id) {
      Preconditions.checkNotNull(application, "application cannot be null.");
      Preconditions.checkNotNull(application, "type cannot be null.");
      Preconditions.checkNotNull(id, "id cannot be null.");
      this.application = application;
      this.type = type;
      this.id = id;
    }

    @Override
    public String getId() {
      return id;
    }

    public ProgramType getType() {
      return type;
    }

    public String getApplicationId() {
      return application.getId();
    }

    public String getNamespaceId() {
      return application.getNamespaceId();
    }

    public Namespace getNamespace() {
      return application.getNamespace();
    }

    public Application getApplication() {
      return application;
    }

    @Override
    protected String getIdForRep() {
      return type.getCategoryName() + ":" + id;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      Program program = (Program) o;
      return application.equals(program.application) && id.equals(program.id);
    }

    @Override
    public int hashCode() {
      int result = application.hashCode();
      result = 31 * result + id.hashCode();
      return result;
    }

    public static Program from(Application appId, ProgramType type, String pgmId) {
      return new Program(appId, type, pgmId);
    }

    public static Program from(Id.Namespace namespaceId, String appId, ProgramType type, String pgmId) {
      return new Program(new Application(namespaceId, appId), type, pgmId);
    }

    public static Program from(String namespaceId, String appId, ProgramType type, String pgmId) {
      return new Program(new Application(new Namespace(namespaceId), appId), type, pgmId);
    }

    /**
     * @param strings from {@link Id#toString()}, split by "/"
     * @param position index into the string where parsing should begin
     * @return the {@link Program}
     */
    public static Program fromStrings(String[] strings, int position) {
      Preconditions.checkArgument(position >= 1);

      String[] tokens = strings[position].split(":");
      Preconditions.checkArgument(tokens.length == 3);
      ProgramType programType = ProgramType.valueOfCategoryName(tokens[1]);
      String programId = tokens[2];
      return from(Application.fromStrings(strings, position - 1), programType, programId);
    }

    /**
     * @param strings from {@link Id#toString()}, split by "/"
     * @return the {@link Program}
     */
    public static Program fromStrings(String[] strings) {
      return fromStrings(strings, strings.length - 1);
    }

    @Override
    public Id getParent() {
      return application;
    }
  }

  /**
   * Uniquely identifies a Worker.
   */
  public static class Worker extends Program {

    private Worker(Application application, String id) {
      super(application, ProgramType.WORKER, id);
    }

    public static Worker from(Application application, String id) {
      return new Worker(application, id);
    }

    public static Worker from(Namespace namespace, String appId, String id) {
      return new Worker(new Application(namespace, appId), id);
    }
  }

  /**
   * Uniquely identifies a Service.
   */
  public static class Service extends Program {

    private Service(Application application, String id) {
      super(application, ProgramType.SERVICE, id);
    }

    public static Service from(Application application, String id) {
      return new Service(application, id);
    }
  }

  /**
   * Uniquely identifies a Flow.
   */
  public static class Flow extends Program {

    private Flow(Application application, String id) {
      super(application, ProgramType.FLOW, id);
    }

    public static Flow from(Application application, String flowId) {
      return new Flow(application, flowId);
    }

    public static Flow from(String appId, String flowId) {
      return new Flow(Id.Application.from(Namespace.DEFAULT, appId), flowId);
    }

    public static Flow from(String namespaceId, String appId, String flowId) {
      return new Flow(Id.Application.from(namespaceId, appId), flowId);
    }

    /**
     * Uniquely identifies a Flowlet.
     */
    public static class Flowlet extends NamespacedId {

      private final Flow flow;
      private final String id;

      private Flowlet(Flow flow, String id) {
        Preconditions.checkArgument(flow != null, "flow cannot be null");
        Preconditions.checkArgument(id != null, "id cannot be null");
        this.flow = flow;
        this.id = id;
      }

      public static Flowlet from(Flow flow, String id) {
        return new Flowlet(flow, id);
      }

      public static Flowlet from(Application app, String flowId, String id) {
        return new Flowlet(new Flow(app, flowId), id);
      }

      @Override
      public Namespace getNamespace() {
        return flow.getNamespace();
      }

      @Nullable
      @Override
      protected Id getParent() {
        return flow;
      }

      @Override
      public String getId() {
        return id;
      }

      public Flow getFlow() {
        return flow;
      }

      /**
       * Uniquely identifies a Flowlet Queue.
       */
      public static final class Queue extends NamespacedId {

        private final Flowlet producer;
        private final String id;

        public Queue(Flowlet producer, String id) {
          this.producer = producer;
          this.id = id;
        }

        public Flowlet getProducer() {
          return producer;
        }

        public String getId() {
          return id;
        }

        @Nullable
        @Override
        protected Id getParent() {
          return producer;
        }

        @Override
        public Namespace getNamespace() {
          return producer.getNamespace();
        }
      }
    }
  }

  /**
   * Represents ID of a Schedule.
   */
  public static class Schedule extends NamespacedId {

    private final Application application;
    private final String id;

    private Schedule(Application application, String id) {
      Preconditions.checkArgument(application != null, "application cannot be null.");
      Preconditions.checkArgument(id != null && !id.isEmpty(), "id cannot be null or empty.");
      this.application = application;
      this.id = id;
    }

    @Override
    public Id getParent() {
      return application;
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public Namespace getNamespace() {
      return application.getNamespace();
    }

    @Override
    public String toString() {
      return Objects.toStringHelper(this)
        .add("application", application)
        .add("id", id).toString();
    }

    public static Schedule from(Application application, String id) {
      return new Schedule(application, id);
    }

    public static Schedule from(Namespace namespace, String appId, String id) {
      return new Schedule(Id.Application.from(namespace, appId), id);
    }
  }

  /**
   * Represents ID of a Notification feed.
   */
  public static class NotificationFeed extends NamespacedId {

    private final Namespace namespace;
    private final String category;
    private final String name;

    private final String description;

    /**
     * {@link NotificationFeed} object from an id in the form of "namespace.category.name".
     *
     * @param id id of the notification feed to build
     * @return a {@link NotificationFeed} object which id is the same as {@code id}
     * @throws IllegalArgumentException when the id doesn't match a valid feed id
     */
    public static NotificationFeed fromId(String id) {
      String[] idParts = id.split("\\.");
      if (idParts.length != 3) {
        throw new IllegalArgumentException(String.format("Id %s is not a valid feed id.", id));
      }
      return new NotificationFeed(idParts[0], idParts[1], idParts[2], "");
    }

    private NotificationFeed(String namespace, String category, String name, String description) {
      Preconditions.checkArgument(namespace != null && !namespace.isEmpty(),
                                  "Namespace value cannot be null or empty.");
      Preconditions.checkArgument(category != null && !category.isEmpty(),
                                  "Category value cannot be null or empty.");
      Preconditions.checkArgument(name != null && !name.isEmpty(), "Name value cannot be null or empty.");
      Preconditions.checkArgument(isValidId(namespace) && isValidId(category) && isValidId(name),
                                  "Namespace, category or name has a wrong format.");

      this.namespace = Namespace.from(namespace);
      this.category = category;
      this.name = name;
      this.description = description;
    }

    public String getCategory() {
      return category;
    }

    @Nullable
    @Override
    protected Id getParent() {
      return namespace;
    }

    @Override
    public String getId() {
      return name;
    }

    public String getFeedId() {
      return String.format("%s.%s.%s", namespace.getId(), category, name);
    }

    public String getNamespaceId() {
      return namespace.getId();
    }

    public String getName() {
      return name;
    }

    public String getDescription() {
      return description;
    }

    @Override
    public Namespace getNamespace() {
      return namespace;
    }

    /**
     * Builder used to build {@link NotificationFeed}.
     */
    public static final class Builder {
      private String category;
      private String name;
      private String namespaceId;
      private String description;

      public Builder() {
        // No-op
      }

      public Builder(NotificationFeed feed) {
        this.namespaceId = feed.getNamespaceId();
        this.category = feed.getCategory();
        this.name = feed.getName();
        this.description = feed.getDescription();
      }

      public Builder setName(final String name) {
        this.name = name;
        return this;
      }

      public Builder setNamespaceId(final String namespace) {
        this.namespaceId = namespace;
        return this;
      }

      public Builder setDescription(final String description) {
        this.description = description;
        return this;
      }

      public Builder setCategory(final String category) {
        this.category = category;
        return this;
      }

      /**
       * @return a {@link NotificationFeed} object containing all the fields set in the builder.
       * @throws IllegalArgumentException if the namespaceId, category or name is invalid.
       */
      public NotificationFeed build() {
        return new NotificationFeed(namespaceId, category, name, description);
      }
    }

    @Override
    public String toString() {
      return Objects.toStringHelper(this)
        .add("namespace", namespace)
        .add("category", category)
        .add("name", name)
        .add("description", description)
        .toString();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      NotificationFeed that = (NotificationFeed) o;
      return Objects.equal(this.namespace, that.namespace)
        && Objects.equal(this.category, that.category)
        && Objects.equal(this.name, that.name);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(namespace, category, name);
    }
  }

  /**
   * Id.Stream uniquely identifies a stream.
   */
  public static final class Stream extends NamespacedId {
    private final Namespace namespace;
    private final String streamName;
    private transient int hashCode;

    private transient String id;
    private transient byte[] idBytes;

    private Stream(final Namespace namespace, final String streamName) {
      Preconditions.checkNotNull(namespace, "Namespace cannot be null.");
      Preconditions.checkNotNull(streamName, "Stream name cannot be null.");

      Preconditions.checkArgument(isValidId(streamName), "Stream name can only contain alphanumeric, " +
                                    "'-' and '_' characters: %s", streamName);

      this.namespace = namespace;
      this.streamName = streamName;
    }

    @Override
    public Namespace getNamespace() {
      return namespace;
    }

    @Nullable
    @Override
    protected Id getParent() {
      return namespace;
    }

    public String getNamespaceId() {
      return namespace.getId();
    }

    @Override
    public String getId() {
      return streamName;
    }

    public static Stream from(Namespace id, String streamName) {
      return new Stream(id, streamName);
    }

    public static Stream from(String namespaceId, String streamName) {
      return from(Id.Namespace.from(namespaceId), streamName);
    }

    public static Stream fromId(String id) {
      Iterable<String> comps = Splitter.on('.').omitEmptyStrings().split(id);
      Preconditions.checkArgument(2 == Iterables.size(comps));

      String namespace = Iterables.get(comps, 0);
      String streamName = Iterables.get(comps, 1);
      return from(namespace, streamName);
    }

    public String toId() {
      if (id == null) {
        id = String.format("%s.%s", namespace, streamName);
      }
      return id;
    }

    public byte[] toBytes() {
      if (idBytes == null) {
        idBytes = toId().getBytes(Charsets.US_ASCII);
      }
      return idBytes;
    }

    @Override
    public int hashCode() {
      int h = hashCode;
      if (h == 0) {
        h = 31 * namespace.hashCode() + streamName.hashCode();
        hashCode = h;
      }
      return h;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      Stream that = (Stream) o;

      return this.namespace.equals(that.namespace) &&
        this.streamName.equals(that.streamName);
    }

    @Override
    public String toString() {
      return Objects.toStringHelper(this)
        .add("namespace", namespace)
        .add("streamName", streamName)
        .toString();
    }
  }

  /**
   * Dataset Type Id identifies a given dataset module.
   */
  public static final class DatasetType extends NamespacedId {
    private final Namespace namespace;
    private final String typeName;

    private DatasetType(Namespace namespace, String typeName) {
      Preconditions.checkNotNull(namespace, "Namespace cannot be null.");
      Preconditions.checkNotNull(typeName, "Dataset type id cannot be null.");
      Preconditions.checkArgument(isValidDatasetId(typeName), "Invalid characters found in dataset type Id. '" +
        typeName + "'. Module id can contain alphabets, numbers or _, -, . or $ characters");
      this.namespace = namespace;
      this.typeName = typeName;
    }

    @Override
    public Namespace getNamespace() {
      return namespace;
    }

    public String getNamespaceId() {
      return namespace.getId();
    }

    public String getTypeName() {
      return typeName;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      DatasetType that = (DatasetType) o;
      return namespace.equals(that.namespace) && typeName.equals(that.typeName);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(namespace, typeName);
    }

    @Override
    public String toString() {
      return Objects.toStringHelper(this)
        .add("namespace", namespace)
        .add("typeName", typeName)
        .toString();
    }

    public static DatasetType from(Namespace id, String typeId) {
      return new DatasetType(id, typeId);
    }

    public static DatasetType from(String namespaceId, String typeId) {
      return new DatasetType(Namespace.from(namespaceId), typeId);
    }

    @Nullable
    @Override
    protected Id getParent() {
      return namespace;
    }

    @Override
    public String getId() {
      return typeName;
    }
  }

  /**
   * Dataset Module Id identifies a given dataset module.
   */
  public static final class DatasetModule extends NamespacedId {
    private final Namespace namespace;
    private final String moduleId;

    private DatasetModule(Namespace namespace, String moduleId) {
      Preconditions.checkNotNull(namespace, "Namespace cannot be null.");
      Preconditions.checkNotNull(moduleId, "Dataset module id cannot be null.");
      Preconditions.checkArgument(isValidDatasetId(moduleId), "Invalid characters found in dataset module Id. '" +
        moduleId + "'. Module id can contain alphabets, numbers or _, -, . or $ characters");
      this.namespace = namespace;
      this.moduleId = moduleId;
    }

    @Override
    public Namespace getNamespace() {
      return namespace;
    }

    public String getNamespaceId() {
      return namespace.getId();
    }

    @Nullable
    @Override
    protected Id getParent() {
      return namespace;
    }

    public String getId() {
      return moduleId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      DatasetModule that = (DatasetModule) o;
      return namespace.equals(that.namespace) && moduleId.equals(that.moduleId);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(namespace, moduleId);
    }

    @Override
    public String toString() {
      return Objects.toStringHelper(this)
       .add("namespace", namespace)
       .add("module", moduleId)
       .toString();
    }

    public static DatasetModule from(Namespace id, String moduleId) {
      return new DatasetModule(id, moduleId);
    }

    public static DatasetModule from(String namespaceId, String moduleId) {
      return new DatasetModule(Namespace.from(namespaceId), moduleId);
    }
  }

  /**
   * Dataset Instance Id identifies a given dataset instance.
   */
  public static final class DatasetInstance extends NamespacedId {
    private final Namespace namespace;
    private final String instanceId;

    private DatasetInstance(Namespace namespace, String instanceId) {
      Preconditions.checkNotNull(namespace, "Namespace cannot be null.");
      Preconditions.checkNotNull(instanceId, "Dataset instance id cannot be null.");
      Preconditions.checkArgument(isValidDatasetId(instanceId), "Invalid characters found in dataset instance id. '" +
        instanceId + "'. Instance id can contain alphabets, numbers or _, -, . or $ characters");
      this.namespace = namespace;
      this.instanceId = instanceId;
    }

    @Override
    public Namespace getNamespace() {
      return namespace;
    }

    public String getNamespaceId() {
      return namespace.getId();
    }

    @Nullable
    @Override
    protected Id getParent() {
      return namespace;
    }

    public String getId() {
      return instanceId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      DatasetInstance that = (DatasetInstance) o;
      return namespace.equals(that.namespace) && instanceId.equals(that.instanceId);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(namespace, instanceId);
    }

    @Override
    public String toString() {
      return Objects.toStringHelper(this)
        .add("namespace", namespace)
        .add("instance", instanceId)
        .toString();
    }

    public static DatasetInstance from(Namespace id, String instanceId) {
      return new DatasetInstance(id, instanceId);
    }

    public static DatasetInstance from(String namespaceId, String instanceId) {
      return new DatasetInstance(Namespace.from(namespaceId), instanceId);
    }
  }
}
