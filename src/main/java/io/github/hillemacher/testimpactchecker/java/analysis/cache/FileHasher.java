package io.github.hillemacher.testimpactchecker.java.analysis.cache;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Computes stable content fingerprints used to key the main-source index cache. */
public final class FileHasher {

  private static final int BUFFER_SIZE = 8192;

  private FileHasher() {}

  /**
   * Returns the lowercase-hex SHA-256 of the file's contents.
   *
   * @throws IOException when the file cannot be read
   */
  public static String sha256(final Path file) throws IOException {
    final MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
    try (final InputStream in = Files.newInputStream(file)) {
      final byte[] buffer = new byte[BUFFER_SIZE];
      int read;
      while ((read = in.read(buffer)) != -1) {
        digest.update(buffer, 0, read);
      }
    }
    return HexFormat.of().formatHex(digest.digest());
  }
}
