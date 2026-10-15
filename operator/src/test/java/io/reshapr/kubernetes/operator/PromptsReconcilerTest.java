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
package io.reshapr.kubernetes.operator;

import io.reshapr.client.model.ArtifactType;
import io.reshapr.kubernetes.api.model.ServiceRef;
import io.reshapr.kubernetes.api.model.Status;
import io.reshapr.kubernetes.api.prompts.v1alpha1.PromptArgumentItem;
import io.reshapr.kubernetes.api.prompts.v1alpha1.PromptItem;
import io.reshapr.kubernetes.api.prompts.v1alpha1.Prompts;
import io.reshapr.kubernetes.api.prompts.v1alpha1.PromptsSpec;
import io.reshapr.kubernetes.api.prompts.v1alpha1.PromptsStatus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PromptsReconciler}.
 * Validates artifact type, content serialization and status update logic
 * without requiring a live Kubernetes cluster or control-plane connection.
 *
 * @author vaishnav
 */
class PromptsReconcilerTest {

   private PromptsReconciler reconciler;
   private final ObjectMapper objectMapper = new ObjectMapper();

   @BeforeEach
   void setUp() {
      reconciler = new PromptsReconciler();
   }

   private Prompts buildPrompts(String name, String serviceName, String serviceVersion, Map<String, PromptItem> prompts) {
      ServiceRef serviceRef = new ServiceRef();
      serviceRef.setName(serviceName);
      serviceRef.setVersion(serviceVersion);

      PromptsSpec spec = new PromptsSpec();
      spec.setService(serviceRef);
      spec.setPrompts(prompts);

      ObjectMeta meta = new ObjectMeta();
      meta.setName(name);
      meta.setNamespace("test-ns");

      Prompts cr = new Prompts();
      cr.setMetadata(meta);
      cr.setSpec(spec);
      return cr;
   }

   private PromptItem buildPromptItem(String title, String result, PromptArgumentItem... args) {
      PromptItem item = new PromptItem();
      item.setTitle(title);
      item.setResult(result);
      if (args.length > 0) {
         item.setArguments(List.of(args));
      }
      return item;
   }

   private PromptArgumentItem buildArgument(String name, boolean required) {
      PromptArgumentItem arg = new PromptArgumentItem();
      arg.setName(name);
      arg.setRequired(required);
      return arg;
   }

   @Test
   void artifactType_isReshaprPrompts() {
      assertEquals(ArtifactType.RESHAPR_PROMPTS, reconciler.getArtifactType());
   }

   @Test
   void getServiceRef_returnsServiceFromSpec() {
      Prompts prompts = buildPrompts("p-01", "Open-Meteo", "1.0", null);
      ServiceRef ref = reconciler.getServiceRef(prompts);
      assertNotNull(ref);
      assertEquals("Open-Meteo", ref.getName());
      assertEquals("1.0", ref.getVersion());
   }

   @Test
   void getServiceRef_returnsNull_whenSpecIsNull() {
      assertNull(reconciler.getServiceRef(new Prompts()));
   }

   @Test
   void getArtifactName_returnsMetadataName() {
      assertEquals("p-02", reconciler.getArtifactName(buildPrompts("p-02", "svc", "v1", null)));
   }

   @Test
   void getArtifactContent_includesHeaderServiceAndPrompts() throws Exception {
      Map<String, PromptItem> items = new LinkedHashMap<>();
      items.put("travel_advice", buildPromptItem("Travel advice",
            "Use the forecast tool for ${city} and tell me what to pack.", buildArgument("city", true)));

      JsonNode root = objectMapper.readTree(reconciler.getArtifactContent(buildPrompts("p-03", "Open-Meteo", "1.0", items)));

      assertEquals("reshapr.io/v1alpha1", root.get("apiVersion").asText());
      assertEquals("Prompts", root.get("kind").asText());
      assertEquals("Open-Meteo", root.get("service").get("name").asText());
      assertEquals("1.0", root.get("service").get("version").asText());

      JsonNode prompt = root.get("prompts").get("travel_advice");
      assertEquals("Travel advice", prompt.get("title").asText());
      assertEquals("Use the forecast tool for ${city} and tell me what to pack.", prompt.get("result").asText());
      assertEquals(1, prompt.get("arguments").size());
      assertEquals("city", prompt.get("arguments").get(0).get("name").asText());
      assertTrue(prompt.get("arguments").get(0).get("required").asBoolean());
   }

   @Test
   void getArtifactContent_omitsNullFields() throws Exception {
      Map<String, PromptItem> items = Map.of("simple", buildPromptItem(null, "Hello"));

      JsonNode prompt = objectMapper.readTree(reconciler.getArtifactContent(buildPrompts("p-04", "svc", "v1", items)))
            .get("prompts").get("simple");

      assertEquals("Hello", prompt.get("result").asText());
      assertFalse(prompt.has("title"));
      assertFalse(prompt.has("description"));
      assertFalse(prompt.has("arguments"));
   }

   @Test
   void getArtifactContent_omitsPrompts_whenAbsent() throws Exception {
      JsonNode root = objectMapper.readTree(reconciler.getArtifactContent(buildPrompts("p-05", "svc", "v1", null)));
      assertFalse(root.has("prompts"));
   }

   @Test
   void getArtifactContent_handlesNullSpec() throws Exception {
      JsonNode root = objectMapper.readTree(reconciler.getArtifactContent(new Prompts()));
      assertEquals("Prompts", root.get("kind").asText());
      assertFalse(root.has("service"));
      assertFalse(root.has("prompts"));
   }

   @Test
   void updateStatus_setsAllFields() {
      Prompts prompts = buildPrompts("p-06", "svc", "v1", null);
      reconciler.updateStatus(prompts, "svc-123", "art-456", Status.READY, "Synchronized");

      PromptsStatus status = prompts.getStatus();
      assertNotNull(status);
      assertEquals(Status.READY, status.getState());
      assertEquals("svc-123", status.getServiceId());
      assertEquals("art-456", status.getArtifactId());
      assertEquals("Synchronized", status.getMessage());
   }

   @Test
   void updateStatus_doesNotOverwrite_whenFieldsAreNull() {
      Prompts prompts = buildPrompts("p-07", "svc", "v1", null);
      reconciler.updateStatus(prompts, "svc-123", "art-456", Status.READY, "OK");
      reconciler.updateStatus(prompts, null, null, null, null);

      PromptsStatus status = prompts.getStatus();
      assertEquals("svc-123", status.getServiceId());
      assertEquals("art-456", status.getArtifactId());
      assertEquals(Status.READY, status.getState());
      assertEquals("OK", status.getMessage());
   }
}
