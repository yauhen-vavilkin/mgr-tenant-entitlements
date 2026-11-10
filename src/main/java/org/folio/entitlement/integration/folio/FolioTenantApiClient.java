package org.folio.entitlement.integration.folio;

import static java.lang.String.format;
import static java.net.http.HttpClient.Version.HTTP_1_1;
import static org.folio.common.utils.OkapiHeaders.TENANT;
import static org.folio.entitlement.utils.TenantApiUtils.DISABLE_TENANT_ENDPOINT;
import static org.folio.entitlement.utils.TenantApiUtils.TENANT_ENDPOINT;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.common.domain.model.error.Parameter;
import org.folio.entitlement.integration.IntegrationException;
import org.folio.entitlement.integration.folio.configuration.FolioClientConfigurationProperties;
import org.folio.entitlement.integration.folio.model.ModuleRequest;
import org.folio.entitlement.integration.folio.model.TenantAttributes;
import org.folio.entitlement.utils.JsonConverter;
import org.springframework.http.HttpMethod;

@Log4j2
@RequiredArgsConstructor
public class FolioTenantApiClient {

  public static final String POST = HttpMethod.POST.name();
  public static final String DELETE = HttpMethod.DELETE.name();

  /**
   * Instructs the module sidecar to route its outbound calls through the API gateway so calls made while handling a
   * tenant API reach the updated module versions during an upgrade.
   */
  public static final String EGRESS_USE_GATEWAY_HEADER = "x-okapi-egress-use-gateway";

  private final HttpClient httpClient;
  private final JsonConverter jsonConverter;
  private final FolioClientConfigurationProperties clientConfiguration;

  /**
   * Sends a tenant installation request for module using provided module location.
   *
   * @param request - module request body with essential parameters
   */
  public void install(ModuleRequest request) {
    sendHttpRequest(request, createInstallAttributes(request.getModuleId(), request.getTenantParameters()));
  }

  /**
   * Sends a tenant uninstallation request for module using provided module location.
   *
   * @param request - module request body with essential parameters
   */
  public void uninstall(ModuleRequest request) {
    sendHttpRequest(request, createUninstallAttributes(request.getModuleId(), request.isPurge()));
  }

  /**
   * Sends a legacy tenant purge request for module using provided module location.
   *
   * @param request - module request body with essential parameters
   */
  public void uninstallLegacy(ModuleRequest request) {
    var tenantAttributes = createUninstallAttributes(request.getModuleId(), true);
    sendHttpRequest(request, tenantAttributes, DELETE, TENANT_ENDPOINT);
  }

  /**
   * Sends a legacy tenant disable request for module using provided module location.
   *
   * @param request - module request body with essential parameters
   */
  public void disableLegacy(ModuleRequest request) {
    var tenantAttributes = createUninstallAttributes(request.getModuleId(), false);
    sendHttpRequest(request, tenantAttributes, POST, DISABLE_TENANT_ENDPOINT);
  }

  private void sendHttpRequest(ModuleRequest request, TenantAttributes requestBody) {
    sendHttpRequest(request, requestBody, POST, TENANT_ENDPOINT);
  }

  private void sendHttpRequest(ModuleRequest request, TenantAttributes requestBody, String httpMethod,
    String endpoint) {
    var jsonBody = jsonConverter.toJson(requestBody);
    var httpRequest = createHttpRequest(request, jsonBody, httpMethod, endpoint);
    var httpResponse = sendRequest(httpRequest);
    var statusCode = httpResponse.statusCode();
    if (statusCode > 300) {
      var responseBody = httpResponse.body();
      log.info("Module responded with error code: uri = {}, code = {}, responseBody = {},\n"
        + "moduleId = {}, applicationId = {}, tenant = {}",
        httpRequest.uri().getPath(), statusCode, responseBody,
        request.getModuleId(), request.getApplicationId(), request.getTenantName());

      throw new IntegrationException("Failed to perform doPostTenant call",
        List.of(new Parameter().key("cause").value(format("%s: %s", statusCode, responseBody))), statusCode);
    }

    if (statusCode != NO_CONTENT.value()) {
      log.debug("Module responded with code: {}, expected '[204] No Content' [moduleId='{}', url={}, tenant='{}']",
        statusCode, request.getModuleId(), request.getLocation(), request.getTenantName());
    }
  }

  private HttpRequest createHttpRequest(ModuleRequest request, String jsonBody, String httpMethod, String endpoint) {
    return HttpRequest.newBuilder()
      .uri(URI.create(request.getLocation() + endpoint))
      .method(httpMethod, BodyPublishers.ofString(jsonBody))
      .header(CONTENT_TYPE, APPLICATION_JSON_VALUE)
      .header(TENANT, request.getTenantName())
      .header(EGRESS_USE_GATEWAY_HEADER, "true")
      .timeout(clientConfiguration.getReadTimeout())
      .version(HTTP_1_1)
      .build();
  }

  private HttpResponse<String> sendRequest(HttpRequest request) {
    try {
      return httpClient.send(request, BodyHandlers.ofString());
    } catch (IOException error) {
      throw new IntegrationException(format("[%s] Failed to perform request [method: %s, uri: %s]",
        error.getClass().getSimpleName(), request.method(), request.uri()), error);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new IntegrationException(format("Request has been interrupted [method: %s, uri: %s]",
        request.method(), request.uri()), error);
    }
  }

  private static TenantAttributes createInstallAttributes(String moduleId, List<Parameter> parameters) {
    var tenantAttributes = new TenantAttributes();
    tenantAttributes.setModuleTo(moduleId);
    tenantAttributes.setParameters(parameters);
    return tenantAttributes;
  }

  private static TenantAttributes createUninstallAttributes(String moduleId, Boolean purge) {
    var tenantAttributes = new TenantAttributes();
    tenantAttributes.setPurge(purge);
    tenantAttributes.setModuleFrom(moduleId);
    return tenantAttributes;
  }
}
