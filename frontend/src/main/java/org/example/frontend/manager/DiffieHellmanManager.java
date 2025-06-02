package org.example.frontend.manager;

import org.example.frontend.utils.DiffieHellman;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DiffieHellmanManager {
  private static final Map<String, DiffieHellman> dhMap = new ConcurrentHashMap<>();

  public static void put(String roomId, DiffieHellman dh) {
    dhMap.put(roomId, dh);
  }

  public static DiffieHellman get(String roomId) {
    return dhMap.get(roomId);
  }

  public static void remove(String roomId) {
    dhMap.remove(roomId);
  }

  private DiffieHellmanManager() {}
}
