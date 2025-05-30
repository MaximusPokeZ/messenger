package org.example.frontend.utils;

import lombok.Getter;

import java.math.BigInteger;
import java.security.SecureRandom;

public class DiffieHellman {
  private final BigInteger g;
  private final BigInteger p;
  private final BigInteger privateComponent;
  @Getter
  private BigInteger publicComponent;
  @Getter
  private BigInteger sharedSecret;

  public DiffieHellman(String gStr, String pStr) {
    this.g = new BigInteger(gStr);
    this.p = new BigInteger(pStr);
    this.privateComponent = new BigInteger(256, new SecureRandom());
    this.publicComponent = g.modPow(privateComponent, p);
  }

  public void getKey(BigInteger otherPublicComponent) {
    this.sharedSecret = otherPublicComponent.modPow(privateComponent, p);
  }

}
