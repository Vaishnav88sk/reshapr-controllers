/*
 * Copyright The Reshapr Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.reshapr.kubernetes.api.outputfilters.v1alpha1;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;
import io.fabric8.crd.generator.annotation.SchemaFrom;
import io.fabric8.generator.annotation.Required;
import io.sundr.builder.annotations.Buildable;

/**
 * A JSON Patch operation as per RFC 6902.
 * @author vaishnav
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "op", "path", "from", "value" })
@Buildable(editableEnabled = false, builderPackage = "io.fabric8.kubernetes.api.builder")
public class JsonPatchOperation {

   @JsonPropertyDescription("The JSON Patch operation to perform.")
   @Required
   private JsonPatchOp op;

   @JsonPropertyDescription("Target JSON Pointer path (must start with '/').")
   @Required
   private String path;

   @JsonPropertyDescription("Source JSON Pointer path for 'move' and 'copy' operations (must start with '/').")
   private String from;

   @JsonPropertyDescription("Value to apply for 'add', 'replace', or 'test' operations.")
   @SchemaFrom(type = JsonNode.class)
   private Object value;

   public JsonPatchOp getOp() {
      return op;
   }

   public void setOp(JsonPatchOp op) {
      this.op = op;
   }

   public String getPath() {
      return path;
   }

   public void setPath(String path) {
      this.path = path;
   }

   public String getFrom() {
      return from;
   }

   public void setFrom(String from) {
      this.from = from;
   }

   public Object getValue() {
      return value;
   }

   public void setValue(Object value) {
      this.value = value;
   }
}
