/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tritium.ncm.math;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Operations to simplify common {@link MessageDigest} tasks. This class is immutable and thread-safe. However the MessageDigest instances it
 * creates generally won't be.
 * <p>
 * The {@link MessageDigestAlgorithms} class provides constants for standard digest algorithms that can be used with the {@link #getDigest(String)} method and
 * other methods that require the Digest algorithm name.
 * </p>
 * <p>
 * Note: The class has shorthand methods for all the algorithms present as standard in Java 6. This approach requires lots of methods for each algorithm, and
 * quickly becomes unwieldy. The following code works with all algorithms:
 * </p>
 *
 * <pre>
 * import static org.apache.commons.codec.digest.MessageDigestAlgorithms.SHA_224;
 * ...
 * byte [] digest = new DigestUtils(SHA_224).digest(dataToDigest);
 * String hdigest = new DigestUtils(SHA_224).digestAsHex(new File("pom.xml"));
 * </pre>
 *
 * @see MessageDigestAlgorithms
 */
public class DigestUtils {

    /**
     * Package-private for tests.
     */
    static final int BUFFER_SIZE = 1024;

    /**
     * Reads through an InputStream and returns the digest for the data
     *
     * @param messageDigest The MessageDigest to use (for example MD5)
     * @param data          Data to digest
     * @return the digest
     * @throws IOException On error reading from the stream
     * @since 1.11 (was private)
     */
    public static byte[] digest(final MessageDigest messageDigest, final InputStream data) throws IOException {
        return updateDigest(messageDigest, data).digest();
    }

    /**
     * Gets a {@code MessageDigest} for the given {@code algorithm}.
     *
     * @param algorithm the name of the algorithm requested. See
     *                  <a href="https://docs.oracle.com/javase/6/docs/technotes/guides/security/crypto/CryptoSpec.html#AppA">Appendix A in the Java
     *                  Cryptography Architecture Reference Guide</a> for information about standard algorithm names.
     * @return A digest instance.
     * @see MessageDigest#getInstance(String)
     * @throws IllegalArgumentException when a {@link NoSuchAlgorithmException} is caught.
     */
    public static MessageDigest getDigest(final String algorithm) {
        try {
            return getMessageDigest(algorithm);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Gets an MD5 MessageDigest.
     *
     * @return An MD5 digest instance.
     * @throws IllegalArgumentException when a {@link NoSuchAlgorithmException} is caught, which should never happen because MD5 is a built-in algorithm
     * @see MessageDigestAlgorithms#MD5
     */
    public static MessageDigest getMd5Digest() {
        return getDigest(MessageDigestAlgorithms.MD5);
    }

    /**
     * Gets a {@code MessageDigest} for the given {@code algorithm}.
     *
     * @param algorithm the name of the algorithm requested. See
     *                  <a href="https://docs.oracle.com/javase/6/docs/technotes/guides/security/crypto/CryptoSpec.html#AppA"> Appendix A in the Java
     *                  Cryptography Architecture Reference Guide</a> for information about standard algorithm names.
     * @return A digest instance.
     * @see MessageDigest#getInstance(String)
     * @throws NoSuchAlgorithmException if no Provider supports a MessageDigestSpi implementation for the specified algorithm.
     */
    private static MessageDigest getMessageDigest(final String algorithm) throws NoSuchAlgorithmException {
        return MessageDigest.getInstance(algorithm);
    }

    /**
     * Gets an SHA-1 digest.
     *
     * @return An SHA-1 digest instance.
     * @throws IllegalArgumentException when a {@link NoSuchAlgorithmException} is caught, which should never happen because SHA-1 is a built-in algorithm
     * @see MessageDigestAlgorithms#SHA_1
     * @since 1.7
     */
    public static MessageDigest getSha1Digest() {
        return getDigest(MessageDigestAlgorithms.SHA_1);
    }

    /**
     * Gets an SHA-1 digest.
     *
     * @return An SHA-1 digest instance.
     * @throws IllegalArgumentException when a {@link NoSuchAlgorithmException} is caught
     * @deprecated (1.11) Use {@link #getSha1Digest()}
     */
    @Deprecated
    public static MessageDigest getShaDigest() {
        return getSha1Digest();
    }

    /**
     * Calculates the MD5 digest and returns the value as a 16 element {@code byte[]}.
     *
     * @param data Data to digest
     * @return MD5 digest
     */
    public static byte[] md5(final byte[] data) {
        return getMd5Digest().digest(data);
    }

    /**
     * Calculates the MD5 digest and returns the value as a 16 element {@code byte[]}.
     *
     * @param data Data to digest; converted to bytes using {@link StringUtils#getBytesUtf8(String)}
     * @return MD5 digest
     */
    public static byte[] md5(final String data) {
        return md5(StringUtils.getBytesUtf8(data));
    }

    /**
     * Calculates the MD5 digest and returns the value as a 32 character hexadecimal string.
     *
     * @param data Data to digest
     * @return MD5 digest as a hexadecimal string
     */
    public static String md5Hex(final String data) {
        return Hex.encodeHexString(md5(data));
    }

    /**
     * Calculates the SHA-1 digest and returns the value as a {@code byte[]}.
     *
     * @param data Data to digest
     * @return SHA-1 digest
     * @deprecated (1.11) Use {@link #sha1(byte[])}
     */
    @Deprecated
    public static byte[] sha(final byte[] data) {
        return sha1(data);
    }

    /**
     * Calculates the SHA-1 digest and returns the value as a {@code byte[]}.
     *
     * @param data Data to digest
     * @return SHA-1 digest
     * @throws IOException On error reading from the stream
     * @since 1.4
     * @deprecated (1.11) Use {@link #sha1(InputStream)}
     */
    @Deprecated
    public static byte[] sha(final InputStream data) throws IOException {
        return sha1(data);
    }

    /**
     * Calculates the SHA-1 digest and returns the value as a {@code byte[]}.
     *
     * @param data Data to digest
     * @return SHA-1 digest
     * @deprecated (1.11) Use {@link #sha1(String)}
     */
    @Deprecated
    public static byte[] sha(final String data) {
        return sha1(data);
    }

    /**
     * Calculates the SHA-1 digest and returns the value as a {@code byte[]}.
     *
     * @param data Data to digest
     * @return SHA-1 digest
     * @since 1.7
     */
    public static byte[] sha1(final byte[] data) {
        return getSha1Digest().digest(data);
    }

    /**
     * Calculates the SHA-1 digest and returns the value as a {@code byte[]}.
     *
     * @param data Data to digest
     * @return SHA-1 digest
     * @throws IOException On error reading from the stream
     * @since 1.7
     */
    public static byte[] sha1(final InputStream data) throws IOException {
        return digest(getSha1Digest(), data);
    }

    /**
     * Calculates the SHA-1 digest and returns the value as a {@code byte[]}.
     *
     * @param data Data to digest; converted to bytes using {@link StringUtils#getBytesUtf8(String)}
     * @return SHA-1 digest
     */
    public static byte[] sha1(final String data) {
        return sha1(StringUtils.getBytesUtf8(data));
    }

    /**
     * Calculates the SHA-1 digest and returns the value as a hexadecimal string.
     *
     * @param data Data to digest
     * @return SHA-1 digest as a hexadecimal string
     * @since 1.7
     */
    public static String sha1Hex(final byte[] data) {
        return Hex.encodeHexString(sha1(data));
    }

    /**
     * Calculates the SHA-1 digest and returns the value as a hexadecimal string.
     *
     * @param data Data to digest
     * @return SHA-1 digest as a hexadecimal string
     * @throws IOException On error reading from the stream
     * @since 1.7
     */
    public static String sha1Hex(final InputStream data) throws IOException {
        return Hex.encodeHexString(sha1(data));
    }

    /**
     * Calculates the SHA-1 digest and returns the value as a hexadecimal string.
     *
     * @param data Data to digest
     * @return SHA-1 digest as a hexadecimal string
     * @since 1.7
     */
    public static String sha1Hex(final String data) {
        return Hex.encodeHexString(sha1(data));
    }
    /**
     * Calculates the SHA-1 digest and returns the value as a hexadecimal string.
     *
     * @param data Data to digest
     * @return SHA-1 digest as a hexadecimal string
     * @deprecated (1.11) Use {@link #sha1Hex(byte[])}
     */
    @Deprecated
    public static String shaHex(final byte[] data) {
        return sha1Hex(data);
    }

    /**
     * Calculates the SHA-1 digest and returns the value as a hexadecimal string.
     *
     * @param data Data to digest
     * @return SHA-1 digest as a hexadecimal string
     * @throws IOException On error reading from the stream
     * @since 1.4
     * @deprecated (1.11) Use {@link #sha1Hex(InputStream)}
     */
    @Deprecated
    public static String shaHex(final InputStream data) throws IOException {
        return sha1Hex(data);
    }

    /**
     * Calculates the SHA-1 digest and returns the value as a hexadecimal string.
     *
     * @param data Data to digest
     * @return SHA-1 digest as a hexadecimal string
     * @deprecated (1.11) Use {@link #sha1Hex(String)}
     */
    @Deprecated
    public static String shaHex(final String data) {
        return sha1Hex(data);
    }

    /**
     * Reads through an InputStream and updates the digest for the data
     *
     * @param digest      The MessageDigest to use (for example MD5)
     * @param inputStream Data to digest
     * @return the digest
     * @throws IOException On error reading from the stream
     * @since 1.8
     */
    public static MessageDigest updateDigest(final MessageDigest digest, final InputStream inputStream) throws IOException {
        final byte[] buffer = new byte[BUFFER_SIZE];
        int read = inputStream.read(buffer, 0, BUFFER_SIZE);

        while (read > -1) {
            digest.update(buffer, 0, read);
            read = inputStream.read(buffer, 0, BUFFER_SIZE);
        }

        return digest;
    }


    /**
     * Preserves binary compatibility only. As for previous versions does not provide useful behavior
     *
     * @deprecated since 1.11; only useful to preserve binary compatibility
     */
    @Deprecated
    public DigestUtils() {
    }

}
