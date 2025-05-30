package org.example.frontend.httpToSpring;

import lombok.extern.slf4j.Slf4j;
import org.example.frontend.model.JwtStorage;
import org.example.frontend.model.main.User;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ChatApiClient {
  private static final String BASE_URL = "https://localhost:8080/client";
  private static final HttpClient httpClient;

  static {
    try {
      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, new TrustManager[]{new X509TrustManager() {
        public void checkClientTrusted(X509Certificate[] chain, String authType) {}
        public void checkServerTrusted(X509Certificate[] chain, String authType) {}
        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
      }}, new SecureRandom());

      httpClient = HttpClient.newBuilder()
              .sslContext(sslContext)
              .connectTimeout(Duration.ofSeconds(10))
              .build();
    } catch (Exception e) {
      throw new RuntimeException("Failed to initialize HttpClient", e);
    }
  }



  private static HttpRequest.Builder baseRequest(String endpoint) {
    return HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + endpoint))
            .header("Authorization", "Bearer " + JwtStorage.getToken());
  }

  public static List<User> getOnlineUsers() {
    log.info("Getting online users");
    HttpRequest httpRequest = baseRequest( "/allOnline").GET().build();

    try {
      HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

      log.info("status code: {}", response.statusCode());
      JSONArray jsonArray = new JSONArray(response.body());
      List<User> users = new ArrayList<>();
      for (int i = 0; i < jsonArray.length(); i++) {
        JSONObject userObj = jsonArray.getJSONObject(i);
        String username = userObj.getString("username");
        users.add(new User(username));

        log.info("found user: {}", username);
      }
      return users;
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private ChatApiClient() {}
}
