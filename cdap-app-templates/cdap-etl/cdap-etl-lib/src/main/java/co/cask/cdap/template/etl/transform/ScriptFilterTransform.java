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

package co.cask.cdap.template.etl.transform;

import co.cask.cdap.api.annotation.Description;
import co.cask.cdap.api.annotation.Name;
import co.cask.cdap.api.annotation.Plugin;
import co.cask.cdap.api.data.format.StructuredRecord;
import co.cask.cdap.api.metrics.Metrics;
import co.cask.cdap.api.templates.plugins.PluginConfig;
import co.cask.cdap.template.etl.api.Emitter;
import co.cask.cdap.template.etl.api.Transform;
import co.cask.cdap.template.etl.api.TransformContext;
import co.cask.cdap.template.etl.common.StructuredRecordSerializer;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

/**
 * Filters records using custom javascript provided by the config.
 */
@Plugin(type = "transform")
@Name("ScriptFilter")
@Description("A transform plugin that filters records using a custom javascript provided in the plugin's config.")
public class ScriptFilterTransform extends Transform<StructuredRecord, StructuredRecord> {
  private static final String SCRIPT_DESCRIPTION = "Javascript that must implement a shouldFilter function that " +
    "takes a Json object representation of the input record, " +
    "and returns true if the input record should be filtered and false if not. " +
    "For example, 'function shouldFilter(input) { return input.count > 100'; } " +
    "will filter out any records whose count field is greater than 100.";
  private static final Gson GSON = new GsonBuilder()
    .registerTypeAdapter(StructuredRecord.class, new StructuredRecordSerializer())
    .create();
  private static final String FUNCTION_NAME = "dont_name_your_function_this";
  private static final String VARIABLE_NAME = "dont_name_your_variable_this";

  private final ScriptFilterConfig scriptFilterConfig;

  private ScriptEngine engine;
  private Invocable invocable;
  private Metrics metrics;

  public ScriptFilterTransform(ScriptFilterConfig scriptFilterConfig) {
    this.scriptFilterConfig = scriptFilterConfig;
  }

  @Override
  public void initialize(TransformContext context) {
    ScriptEngineManager manager = new ScriptEngineManager();
    engine = manager.getEngineByName("JavaScript");
    String scriptStr = scriptFilterConfig.script;
    Preconditions.checkArgument(!Strings.isNullOrEmpty(scriptStr), "Filter script must be specified.");

    // this is pretty ugly, but doing this so that we can pass the 'input' json into the shouldFilter function.
    // that is, we want people to implement
    // function shouldFilter(input) { ... }
    // rather than function shouldFilter() { ... } and have them access a global variable in the function
    try {
      String script = String.format("function %s() { return shouldFilter(%s); }\n%s",
                                    FUNCTION_NAME, VARIABLE_NAME, scriptStr);
      engine.eval(script);
    } catch (ScriptException e) {
      throw new IllegalArgumentException("Invalid script.", e);
    }
    invocable = (Invocable) engine;
    metrics = context.getMetrics();
  }

  @Override
  public void transform(StructuredRecord input, Emitter<StructuredRecord> emitter) {
    try {
      engine.eval(String.format("var %s = %s;", VARIABLE_NAME, GSON.toJson(input)));
      Boolean shouldFilter = (Boolean) invocable.invokeFunction(FUNCTION_NAME);
      if (!shouldFilter) {
        emitter.emit(input);
      } else {
        metrics.count("filtered", 1);
      }
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid filter condition.", e);
    }
  }

  /**
   * {@link PluginConfig} class for {@link ScriptFilterTransform}
   */
  public static class ScriptFilterConfig extends PluginConfig {
    @Description(SCRIPT_DESCRIPTION)
    String script;
  }
}
