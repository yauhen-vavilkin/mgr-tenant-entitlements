package org.folio.entitlement.integration.folio;

import static java.net.http.HttpResponse.BodyHandlers.ofString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.common.utils.OkapiHeaders.TENANT;
import static org.folio.entitlement.integration.folio.FolioTenantApiClient.EGRESS_USE_GATEWAY_HEADER;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestUtils.JACKSON3_OBJECT_MAPPER;
import static org.folio.entitlement.support.TestUtils.asJsonString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import org.folio.common.domain.model.error.Parameter;
import org.folio.entitlement.integration.IntegrationException;
import org.folio.entitlement.integration.folio.configuration.FolioClientConfigurationProperties;
import org.folio.entitlement.integration.folio.model.ModuleRequest;
import org.folio.entitlement.integration.folio.model.TenantAttributes;
import org.folio.entitlement.support.TestUtils;
import org.folio.entitlement.utils.JsonConverter;
import org.folio.test.types.UnitTest;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class FolioTenantApiClientTest {

  private static final String MODULE_ID = "mod-test-2.1";
  private static final String MODULE_LOCATION = "http://mod-test:8081";

  @InjectMocks private FolioTenantApiClient folioTenantApiClient;
  @Mock private HttpClient httpClient;
  @Mock private HttpResponse<String> httpResponse;
  @Mock private FolioClientConfigurationProperties folioClientConfigurationProperties;
  @Captor private ArgumentCaptor<HttpRequest> httpRequestCaptor;
  @Spy private final JsonConverter jsonConverter = new JsonConverter(JACKSON3_OBJECT_MAPPER);

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void install_positive() throws IOException, InterruptedException {
    when(folioClientConfigurationProperties.getReadTimeout()).thenReturn(Duration.ofSeconds(1));
    when(httpClient.send(httpRequestCaptor.capture(), eq(ofString()))).thenReturn(httpResponse);
    when(httpResponse.statusCode()).thenReturn(204);

    var parameters = List.of(new Parameter().key("loadReference").value("true"));
    folioTenantApiClient.install(moduleRequest(parameters));

    verifyHttpRequest(installRequest(), httpRequestCaptor.getValue(), 82);
    verify(jsonConverter).toJson(tenantAttributesForInstall());
  }

  @Test
  void install_positive_statusEqualToCreated() throws IOException, InterruptedException {
    when(folioClientConfigurationProperties.getReadTimeout()).thenReturn(Duration.ofSeconds(1));
    when(httpClient.send(httpRequestCaptor.capture(), eq(ofString()))).thenReturn(httpResponse);
    when(httpResponse.statusCode()).thenReturn(201);

    var parameters = List.of(new Parameter().key("loadReference").value("true"));
    folioTenantApiClient.install(moduleRequest(parameters));

    verifyHttpRequest(installRequest(), httpRequestCaptor.getValue(), 82);
    verify(jsonConverter).toJson(tenantAttributesForInstall());
  }

  @Test
  void install_negative_invalidResponse() throws IOException, InterruptedException {
    when(folioClientConfigurationProperties.getReadTimeout()).thenReturn(Duration.ofSeconds(1));
    when(httpClient.send(httpRequestCaptor.capture(), eq(ofString()))).thenReturn(httpResponse);
    when(httpResponse.statusCode()).thenReturn(500);
    when(httpResponse.body()).thenReturn("Failed to install tenant");

    var parameters = List.of(new Parameter().key("loadReference").value("true"));
    var moduleRequest = moduleRequest(parameters);
    assertThatThrownBy(() -> folioTenantApiClient.install(moduleRequest))
      .isInstanceOf(IntegrationException.class)
      .hasMessage("Failed to perform doPostTenant call")
      .extracting(e -> ((IntegrationException) e).getCauseHttpStatus()).isEqualTo(500);

    verifyHttpRequest(installRequest(), httpRequestCaptor.getValue(), 82);
    verify(jsonConverter).toJson(tenantAttributesForInstall());
  }

  @Test
  void install_negative_ioException() throws IOException, InterruptedException {
    when(folioClientConfigurationProperties.getReadTimeout()).thenReturn(Duration.ofSeconds(1));
    when(httpClient.send(httpRequestCaptor.capture(), eq(ofString()))).thenThrow(IOException.class);

    var parameters = List.of(new Parameter().key("loadReference").value("true"));
    var moduleRequest = moduleRequest(parameters);
    assertThatThrownBy(() -> folioTenantApiClient.install(moduleRequest))
      .isInstanceOf(IntegrationException.class)
      .hasMessage("[IOException] Failed to perform request [method: POST, uri: http://mod-test:8081/_/tenant]");

    verifyHttpRequest(installRequest(), httpRequestCaptor.getValue(), 82);
    verify(jsonConverter).toJson(tenantAttributesForInstall());
  }

  @Test
  void install_negative_interruptedException() throws IOException, InterruptedException {
    when(folioClientConfigurationProperties.getReadTimeout()).thenReturn(Duration.ofSeconds(1));
    when(httpClient.send(httpRequestCaptor.capture(), eq(ofString()))).thenThrow(InterruptedException.class);

    var parameters = List.of(new Parameter().key("loadReference").value("true"));
    var moduleRequest = moduleRequest(parameters);
    assertThatThrownBy(() -> folioTenantApiClient.install(moduleRequest))
      .isInstanceOf(IntegrationException.class)
      .hasMessage("Request has been interrupted [method: POST, uri: http://mod-test:8081/_/tenant]");

    verifyHttpRequest(installRequest(), httpRequestCaptor.getValue(), 82);
    verify(jsonConverter).toJson(tenantAttributesForInstall());
  }

  @Test
  void uninstall_positive() throws IOException, InterruptedException {
    when(folioClientConfigurationProperties.getReadTimeout()).thenReturn(Duration.ofSeconds(1));
    when(httpClient.send(httpRequestCaptor.capture(), eq(ofString()))).thenReturn(httpResponse);
    when(httpResponse.statusCode()).thenReturn(204);

    var moduleRequest = moduleRequest(true);
    folioTenantApiClient.uninstall(moduleRequest);

    verifyHttpRequest(uninstallRequest(), httpRequestCaptor.getValue(), 43);
    verify(jsonConverter).toJson(tenantAttributesForUninstall());
  }

  @Test
  void uninstallLegacy_positive() throws IOException, InterruptedException {
    when(folioClientConfigurationProperties.getReadTimeout()).thenReturn(Duration.ofSeconds(1));
    when(httpClient.send(httpRequestCaptor.capture(), eq(ofString()))).thenReturn(httpResponse);
    when(httpResponse.statusCode()).thenReturn(204);

    folioTenantApiClient.uninstallLegacy(moduleRequest(false));

    verifyHttpRequest(uninstallLegacyRequest(), httpRequestCaptor.getValue(), 43);
    verify(jsonConverter).toJson(tenantAttributesForUninstall());
  }

  @Test
  void disableLegacy_positive() throws IOException, InterruptedException {
    when(folioClientConfigurationProperties.getReadTimeout()).thenReturn(Duration.ofSeconds(1));
    when(httpClient.send(httpRequestCaptor.capture(), eq(ofString()))).thenReturn(httpResponse);
    when(httpResponse.statusCode()).thenReturn(204);

    folioTenantApiClient.disableLegacy(moduleRequest(false));

    verifyHttpRequest(disableLegacyRequest(), httpRequestCaptor.getValue(), 44);
    verify(jsonConverter).toJson(tenantAttributesForUninstall(false));
  }

  @Test
  void uninstall_negative_invalidResponse() throws IOException, InterruptedException {
    when(folioClientConfigurationProperties.getReadTimeout()).thenReturn(Duration.ofSeconds(1));
    when(httpClient.send(httpRequestCaptor.capture(), eq(ofString()))).thenReturn(httpResponse);
    when(httpResponse.statusCode()).thenReturn(500);
    when(httpResponse.body()).thenReturn("Failed to install tenant");

    var moduleRequest = moduleRequest(true);
    assertThatThrownBy(() -> folioTenantApiClient.uninstall(moduleRequest))
      .isInstanceOf(IntegrationException.class)
      .hasMessage("Failed to perform doPostTenant call")
      .extracting(e -> ((IntegrationException) e).getCauseHttpStatus()).isEqualTo(500);

    verifyHttpRequest(uninstallRequest(), httpRequestCaptor.getValue(), 43);
    verify(jsonConverter).toJson(tenantAttributesForUninstall());
  }

  @Test
  void uninstall_negative_ioException() throws IOException, InterruptedException {
    when(folioClientConfigurationProperties.getReadTimeout()).thenReturn(Duration.ofSeconds(1));
    when(httpClient.send(httpRequestCaptor.capture(), eq(ofString()))).thenThrow(IOException.class);

    var moduleRequest = moduleRequest(true);
    assertThatThrownBy(() -> folioTenantApiClient.uninstall(moduleRequest))
      .isInstanceOf(IntegrationException.class)
      .hasMessage("[IOException] Failed to perform request [method: POST, uri: http://mod-test:8081/_/tenant]");

    verifyHttpRequest(uninstallRequest(), httpRequestCaptor.getValue(), 43);
    verify(jsonConverter).toJson(tenantAttributesForUninstall());
  }

  @Test
  void uninstall_negative_interruptedException() throws IOException, InterruptedException {
    when(folioClientConfigurationProperties.getReadTimeout()).thenReturn(Duration.ofSeconds(1));
    when(httpClient.send(httpRequestCaptor.capture(), eq(ofString()))).thenThrow(InterruptedException.class);

    var moduleRequest = moduleRequest(true);
    assertThatThrownBy(() -> folioTenantApiClient.uninstall(moduleRequest))
      .isInstanceOf(IntegrationException.class)
      .hasMessage("Request has been interrupted [method: POST, uri: http://mod-test:8081/_/tenant]");

    verifyHttpRequest(uninstallRequest(), httpRequestCaptor.getValue(), 43);
    verify(jsonConverter).toJson(tenantAttributesForUninstall());
  }

  private static HttpRequest installRequest() {
    return HttpRequest.newBuilder()
      .uri(URI.create("http://mod-test:8081/_/tenant"))
      .POST(BodyPublishers.ofString(asJsonString(tenantAttributesForInstall())))
      .header(CONTENT_TYPE, APPLICATION_JSON_VALUE)
      .header(TENANT, TENANT_NAME)
      .header(EGRESS_USE_GATEWAY_HEADER, "true")
      .version(Version.HTTP_1_1)
      .build();
  }

  private static HttpRequest uninstallRequest() {
    return HttpRequest.newBuilder()
      .uri(URI.create("http://mod-test:8081/_/tenant"))
      .POST(BodyPublishers.ofString(asJsonString(tenantAttributesForUninstall())))
      .header(CONTENT_TYPE, APPLICATION_JSON_VALUE)
      .header(TENANT, TENANT_NAME)
      .header(EGRESS_USE_GATEWAY_HEADER, "true")
      .version(Version.HTTP_1_1)
      .build();
  }

  private static HttpRequest uninstallLegacyRequest() {
    return HttpRequest.newBuilder()
      .uri(URI.create("http://mod-test:8081/_/tenant"))
      .method("DELETE", BodyPublishers.ofString(asJsonString(tenantAttributesForUninstall())))
      .header(CONTENT_TYPE, APPLICATION_JSON_VALUE)
      .header(TENANT, TENANT_NAME)
      .header(EGRESS_USE_GATEWAY_HEADER, "true")
      .version(Version.HTTP_1_1)
      .build();
  }

  private static HttpRequest disableLegacyRequest() {
    return HttpRequest.newBuilder()
      .uri(URI.create("http://mod-test:8081/_/tenant/disable"))
      .POST(BodyPublishers.ofString(asJsonString(tenantAttributesForUninstall(false))))
      .header(CONTENT_TYPE, APPLICATION_JSON_VALUE)
      .header(TENANT, TENANT_NAME)
      .header(EGRESS_USE_GATEWAY_HEADER, "true")
      .version(Version.HTTP_1_1)
      .build();
  }

  @NonNull
  private static TenantAttributes tenantAttributesForInstall() {
    var tenantAttributes = new TenantAttributes();
    tenantAttributes.setModuleTo(MODULE_ID);
    tenantAttributes.setParameters(List.of(new Parameter().key("loadReference").value("true")));
    return tenantAttributes;
  }

  private static TenantAttributes tenantAttributesForUninstall() {
    return tenantAttributesForUninstall(true);
  }

  private static TenantAttributes tenantAttributesForUninstall(boolean purge) {
    var tenantAttributes = new TenantAttributes();
    tenantAttributes.setModuleFrom(MODULE_ID);
    tenantAttributes.setPurge(purge);
    return tenantAttributes;
  }

  private static void verifyHttpRequest(HttpRequest expectedRequest, HttpRequest httpRequest, long contentLength) {
    assertThat(httpRequest).isEqualTo(expectedRequest);
    assertThat(httpRequest.bodyPublisher()).isPresent()
      .get().extracting(BodyPublisher::contentLength)
      .isEqualTo(contentLength);
  }

  private static ModuleRequest moduleRequest(boolean purge) {
    return moduleRequest(purge, Collections.emptyList());
  }

  private static ModuleRequest moduleRequest(List<Parameter> parameters) {
    return moduleRequest(false, parameters);
  }

  private static ModuleRequest moduleRequest(boolean purge, List<Parameter> parameters) {
    return ModuleRequest.builder()
      .moduleId(MODULE_ID)
      .location(MODULE_LOCATION)
      .tenantId(TENANT_ID)
      .tenantName(TENANT_NAME)
      .purge(purge)
      .tenantParameters(parameters)
      .build();
  }
}
