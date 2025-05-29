package org.example.frontend.httpToSpring;

import lombok.extern.slf4j.Slf4j;
import org.example.frontend.model.JwtStorage;
import org.example.frontend.model.main.User;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ChatApiClient {
  private static final String BASE_URL = "http://localhost:8080/client";
  private static final HttpClient httpClient = HttpClient.newHttpClient();

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
        //users.add(new User(jsonArray.getString(i)));
        //log.info(jsonArray.getString(i));
        JSONObject userObj = jsonArray.getJSONObject(i);
        // Извлекаем имя пользователя
        String username = userObj.getString("username");
        // Создаём и добавляем пользователя
        users.add(new User(username));

        log.info("finded user: {}", username);
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
