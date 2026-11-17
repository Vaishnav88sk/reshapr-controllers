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

import io.reshapr.client.ApiClient;
import io.reshapr.client.ApiException;
import io.reshapr.client.api.DefaultApi;
import io.reshapr.client.model.ArtifactType;
import io.reshapr.kubernetes.api.model.ServiceRef;
import io.reshapr.kubernetes.api.model.Status;
import io.reshapr.kubernetes.api.outputfilters.v1alpha1.JsonPatchOp;
import io.reshapr.kubernetes.api.outputfilters.v1alpha1.JsonPatchOperation;
import io.reshapr.kubernetes.api.outputfilters.v1alpha1.ToolOutputFilterItem;
import io.reshapr.kubernetes.api.outputfilters.v1alpha1.ToolsOutputFilters;
import io.reshapr.kubernetes.api.outputfilters.v1alpha1.ToolsOutputFiltersSpec;
import io.reshapr.kubernetes.operator.auth.ReshaprAnnotations;
import io.reshapr.kubernetes.operator.auth.ReshaprApiClientFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ToolsOutputFiltersReconciler}.
 * @author vaishnav
 */
class ToolsOutputFiltersReconcilerTest {

   private final ToolsOutputFiltersReconciler reconciler = new ToolsOutputFiltersReconciler();
   private final ObjectMapper objectMapper = new ObjectMapper();

   private ToolsOutputFilters buildToolsOutputFilters() {
      ServiceRef serviceRef = new ServiceRef();
      serviceRef.setName("open-meteo-api");
      serviceRef.setVersion("1.0");

      JsonPatchOperation addOp = new JsonPatchOperation();
      addOp.setOp(JsonPatchOp.add);
      addOp.setPath("/units");
      addOp.setValue("metric");

      JsonPatchOperation removeOp = new JsonPatchOperation();
      removeOp.setOp(JsonPatchOp.remove);
      removeOp.setPath("/hourly_units");

      ToolOutputFilterItem forecastFilter = new ToolOutputFilterItem();
      forecastFilter.setJsonRetain(List.of("/current_weather", "/hourly/temperature_2m"));
      forecastFilter.setJsonPatches(List.of(addOp, removeOp));
      forecastFilter.setCompact(Boolean.TRUE);
      forecastFilter.setConvertToToon(Boolean.TRUE);

      ToolsOutputFiltersSpec spec = new ToolsOutputFiltersSpec();
      spec.setService(serviceRef);
      spec.setFilters(Map.of("get_v1_forecast", forecastFilter));

      ObjectMeta meta = new ObjectMeta();
      meta.setName("open-meteo-filters");
      ToolsOutputFilters resource = new ToolsOutputFilters();
      resource.setMetadata(meta);
      resource.setSpec(spec);
      return resource;
   }

   @Test
   void testArtifactTypeAndName() {
      ToolsOutputFilters resource = buildToolsOutputFilters();
      assertEquals(ArtifactType.RESHAPR_TOOLS_OUTPUT_FILTERS, reconciler.getArtifactType());
      assertEquals("open-meteo-filters", reconciler.getArtifactName(resource));
      assertEquals("open-meteo-api", reconciler.getServiceRef(resource).getName());
   }

   @Test
   void testArtifactContent() throws Exception {
      JsonNode root = objectMapper.readTree(reconciler.getArtifactContent(buildToolsOutputFilters()));
      assertEquals("reshapr.io/v1alpha1", root.get("apiVersion").asText());
      assertEquals("ToolsOutputFilters", root.get("kind").asText());
      assertEquals("open-meteo-api", root.get("service").get("name").asText());
      assertEquals("1.0", root.get("service").get("version").asText());

      JsonNode filter = root.get("filters").get("get_v1_forecast");
      assertEquals("/current_weather", filter.get("jsonRetain").get(0).asText());
      assertEquals("/hourly/temperature_2m", filter.get("jsonRetain").get(1).asText());

      JsonNode addPatch = filter.get("jsonPatches").get(0);
      assertEquals("add", addPatch.get("op").asText());
      assertEquals("/units", addPatch.get("path").asText());
      assertEquals("metric", addPatch.get("value").asText());

      JsonNode removePatch = filter.get("jsonPatches").get(1);
      assertEquals("remove", removePatch.get("op").asText());
      assertEquals("/hourly_units", removePatch.get("path").asText());
      assertFalse(removePatch.has("value"));
      assertFalse(removePatch.has("from"));

      assertTrue(filter.get("compact").asBoolean());
      assertTrue(filter.get("convertToToon").asBoolean());
   }

   @Test
   void testArtifactContentOmitsOptionalFieldsAndSupportsObjectCompact() throws Exception {
      ToolsOutputFilters resource = buildToolsOutputFilters();
      ToolOutputFilterItem minimal = new ToolOutputFilterItem();
      minimal.setCompact(Map.of());
      resource.getSpec().setFilters(Map.of("listPastries", minimal));

      JsonNode filter = objectMapper.readTree(reconciler.getArtifactContent(resource))
            .get("filters").get("listPastries");
      assertTrue(filter.get("compact").isObject());
      assertFalse(filter.has("jsonRetain"));
      assertFalse(filter.has("jsonPatches"));
      assertFalse(filter.has("convertToToon"));
   }

   @Test
   void testArtifactContentWithoutSpec() throws Exception {
      ToolsOutputFilters resource = buildToolsOutputFilters();
      resource.setSpec(null);

      JsonNode root = objectMapper.readTree(reconciler.getArtifactContent(resource));
      assertEquals("ToolsOutputFilters", root.get("kind").asText());
      assertFalse(root.has("service"));
      assertFalse(root.has("filters"));
      assertNull(reconciler.getServiceRef(resource));
      assertFalse(reconciler.isKeepOnDelete(resource));
   }

   @Test
   void testUpdateStatus() {
      ToolsOutputFilters resource = buildToolsOutputFilters();
      assertNull(reconciler.getArtifactId(resource));

      reconciler.updateStatus(resource, "svc", "art", Status.READY, "ok");
      assertEquals(Status.READY, resource.getStatus().getState());
      assertEquals("svc", resource.getStatus().getServiceId());
      assertEquals("art", resource.getStatus().getArtifactId());
      assertEquals("ok", resource.getStatus().getMessage());
      assertEquals("art", reconciler.getArtifactId(resource));

      reconciler.updateStatus(resource, null, null, Status.ERROR, "boom");
      assertEquals(Status.ERROR, resource.getStatus().getState());
      assertEquals("svc", resource.getStatus().getServiceId());
      assertEquals("art", resource.getStatus().getArtifactId());
      assertEquals("boom", resource.getStatus().getMessage());
   }

   @Test
   void testReconcilerImplementsCleanerForArtifactDeletion() {
      assertTrue(Cleaner.class.isAssignableFrom(ToolsOutputFiltersReconciler.class),
            "ToolsOutputFiltersReconciler must implement Cleaner to delete artifacts from control plane on CR deletion");
   }

   @Test
   void testCleanup_deletesArtifactWhenPresent() throws Exception {
      ReshaprApiClientFactory factory = mock(ReshaprApiClientFactory.class);
      ApiClient apiClient = mock(ApiClient.class);
      when(factory.createAuthenticatedApiClient(any(), any())).thenReturn(apiClient);

      ToolsOutputFiltersReconciler customReconciler = new ToolsOutputFiltersReconciler(factory, null, objectMapper);
      ToolsOutputFilters resource = buildToolsOutputFilters();
      resource.getMetadata().setAnnotations(Map.of(
            ReshaprAnnotations.INSTANCE, "reshapr-ctrl.reshapr-system",
            ReshaprAnnotations.ORGANIZATION, "reshapr"
      ));
      customReconciler.updateStatus(resource, "svc-1", "art-123", Status.READY, "ok");

      try (MockedConstruction<DefaultApi> mocked = Mockito.mockConstruction(DefaultApi.class)) {
         DeleteControl control = customReconciler.cleanup(resource, null);

         assertTrue(control.isRemoveFinalizer());
         DefaultApi mockedApi = mocked.constructed().get(0);
         verify(mockedApi).deleteArtifact("art-123");
      }
   }

   @Test
   void testCleanup_handles404Cleanly() throws Exception {
      ReshaprApiClientFactory factory = mock(ReshaprApiClientFactory.class);
      ApiClient apiClient = mock(ApiClient.class);
      when(factory.createAuthenticatedApiClient(any(), any())).thenReturn(apiClient);

      ToolsOutputFiltersReconciler customReconciler = new ToolsOutputFiltersReconciler(factory, null, objectMapper);
      ToolsOutputFilters resource = buildToolsOutputFilters();
      resource.getMetadata().setAnnotations(Map.of(
            ReshaprAnnotations.INSTANCE, "reshapr-ctrl.reshapr-system",
            ReshaprAnnotations.ORGANIZATION, "reshapr"
      ));
      customReconciler.updateStatus(resource, "svc-1", "art-123", Status.READY, "ok");

      try (MockedConstruction<DefaultApi> mocked = Mockito.mockConstruction(DefaultApi.class,
            (mock, ctx) -> doThrow(new ApiException(404, "Not Found")).when(mock).deleteArtifact("art-123"))) {
         DeleteControl control = customReconciler.cleanup(resource, null);

         assertTrue(control.isRemoveFinalizer());
         DefaultApi mockedApi = mocked.constructed().get(0);
         verify(mockedApi).deleteArtifact("art-123");
      }
   }

   @Test
   void testCleanup_honorsKeepOnDelete() throws Exception {
      ReshaprApiClientFactory factory = mock(ReshaprApiClientFactory.class);
      ToolsOutputFiltersReconciler customReconciler = new ToolsOutputFiltersReconciler(factory, null, objectMapper);
      ToolsOutputFilters resource = buildToolsOutputFilters();
      resource.getSpec().setKeepOnDelete(true);
      customReconciler.updateStatus(resource, "svc-1", "art-123", Status.READY, "ok");

      try (MockedConstruction<DefaultApi> mocked = Mockito.mockConstruction(DefaultApi.class)) {
         DeleteControl control = customReconciler.cleanup(resource, null);

         assertTrue(control.isRemoveFinalizer());
         assertEquals(0, mocked.constructed().size(), "DefaultApi should not be initialized when keepOnDelete is true");
      }
   }
}

