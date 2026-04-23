package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

@ImpactTest
class BetaTest {

  @Test
  void betaGreets() {
    assertEquals("hello beta", new Beta().greet());
  }
}
