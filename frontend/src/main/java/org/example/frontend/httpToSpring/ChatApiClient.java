package org.example.frontend.httpToSpring;

import org.example.frontend.model.JwtStorage;
import org.example.frontend.model.main.User;
import org.json.JSONArray;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class ChatApiClient {
  private static final String BASE_URL = "http://localhost:8080/api";
  private static final HttpClient httpClient = HttpClient.newHttpClient();

  private static HttpRequest.Builder baseRequest(String endpoint) {
    return HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + endpoint))
            .header("Authorization", "Bearer " + JwtStorage.getToken());
  }

  public static List<User> getOnlineUsers() {
    HttpRequest httpRequest = baseRequest(BASE_URL + "/online").GET().build();

    try {
      HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
      JSONArray jsonArray = new JSONArray(response.body());
      List<User> users = new ArrayList<>();
      for (int i = 0; i < jsonArray.length(); i++) {
        users.add(new User(jsonArray.getString(i)));
      }
      return users;
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  public static List<User> getOnlineUsersTest() {
    return List.of(
            new User("Alice"),
            new User("Bob"),
            new User("Max"),
            new User("Carol")
    );
  }

  private ChatApiClient() {}
}
