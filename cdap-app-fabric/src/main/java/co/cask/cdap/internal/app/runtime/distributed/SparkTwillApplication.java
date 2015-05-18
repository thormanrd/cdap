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
package co.cask.cdap.internal.app.runtime.distributed;

import co.cask.cdap.api.spark.SparkSpecification;
import co.cask.cdap.app.program.Program;
import co.cask.cdap.proto.ProgramType;
import org.apache.twill.api.EventHandler;
import org.apache.twill.api.ResourceSpecification;
import org.apache.twill.api.TwillApplication;
import org.apache.twill.api.TwillSpecification;
import org.apache.twill.filesystem.Location;

import java.io.File;

/**
 * {@link TwillApplication} to run {@link MapReduceTwillRunnable}
 */
public final class SparkTwillApplication implements TwillApplication {

  static final File SPARK_JAR_FILE =
                        new File ("/usr/lib/spark/lib/spark-assembly-1.2.0-cdh5.3.0-hadoop2.5.0-cdh5.3.0.jar");

  private final SparkSpecification spec;
  private final Program program;
  private final File hConfig;
  private final File cConfig;
  private final EventHandler eventHandler;

  public SparkTwillApplication(Program program, SparkSpecification spec,
                               File hConfig, File cConfig, EventHandler eventHandler) {
    this.spec = spec;
    this.program = program;
    this.hConfig = hConfig;
    this.cConfig = cConfig;
    this.eventHandler = eventHandler;
  }

  @Override
  public TwillSpecification configure() {
    // These resources are for the container that runs the spark driver that will launch the actual mapred job.
    // It does not need much memory.  Memory for mappers and reduces are specified in the MapReduceSpecification,
    // which is configurable by the author of the job.
    ResourceSpecification resourceSpec = ResourceSpecification.Builder.with()
      .setVirtualCores(1)
      .setMemory(512, ResourceSpecification.SizeUnit.MEGA)
      .setInstances(1)
      .build();

    Location programLocation = program.getJarLocation();

    return TwillSpecification.Builder.with()
      .setName(String.format("%s.%s.%s.%s",
                             ProgramType.SPARK.name().toLowerCase(),
                             program.getAccountId(), program.getApplicationId(), spec.getName()))
      .withRunnable()
      .add(spec.getName(),
           new SparkTwillRunnable(spec.getName(), "hConf.xml", "cConf.xml"),
           resourceSpec)
      .withLocalFiles()
      .add(programLocation.getName(), programLocation.toURI())
      .add("hConf.xml", hConfig.toURI())
      .add("cConf.xml", cConfig.toURI())
      .add(SPARK_JAR_FILE.getName(), SPARK_JAR_FILE.toURI()).apply()
      .anyOrder().withEventHandler(eventHandler).build();
  }
}
