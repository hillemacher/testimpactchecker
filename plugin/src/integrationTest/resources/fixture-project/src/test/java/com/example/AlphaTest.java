package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

@ImpactTest
class AlphaTest {

  @Test
  void alphaGreets() {
    assertEquals("hello alpha", new Alpha().greet());
  }
}
