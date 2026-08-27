package net.vibmc.network.handler;

import com.github.retrooper.packetevents.protocol.player.TextureProperty;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;

final class SessionAuthentication {
    private static final Pattern ID = Pattern.compile("\\\"id\\\"\\s*:\\s*\\\"([0-9a-fA-F]{32})\\\"");
    private static final Pattern PROPERTY = Pattern.compile(
            "\\{\\s*\\\"name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"\\s*,\\s*"
                    + "\\\"value\\\"\\s*:\\s*\\\"([^\\\"]+)\\\""
                    + "(?:\\s*,\\s*\\\"signature\\\"\\s*:\\s*\\\"([^\\\"]+)\\\")?\\s*}");
    private static final int MAX_RESPONSE_BYTES = 65536;
    private static final KeyPair KEY_PAIR = createKeyPair();

    private SessionAuthentication() {}

    static KeyPair keyPair() {
        return KEY_PAIR;
    }

    static byte[] rsaDecrypt(byte[] encrypted, PrivateKey privateKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        return cipher.doFinal(encrypted);
    }

    static AuthenticatedProfile authenticate(String username, byte[] sharedSecret) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        digest.update("".getBytes(StandardCharsets.ISO_8859_1));
        digest.update(sharedSecret);
        digest.update(KEY_PAIR.getPublic().getEncoded());
        String serverHash = new BigInteger(digest.digest()).toString(16);

        String query = "username=" + URLEncoder.encode(username, "UTF-8")
                + "&serverId=" + URLEncoder.encode(serverHash, "UTF-8");
        URL endpoint = URI.create("https://sessionserver.mojang.com/session/minecraft/hasJoined?" + query)
                .toURL();
        HttpURLConnection connection = (HttpURLConnection) endpoint.openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("User-Agent", "vib-MC/0.0.4");
        int status = connection.getResponseCode();
        if (status == 204) {
            throw new IOException("session was not authenticated");
        }
        if (status != 200) {
            throw new IOException("session server returned HTTP " + status);
        }

        StringBuilder json = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                connection.getInputStream(), StandardCharsets.UTF_8))) {
            char[] buffer = new char[4096];
            int read;
            int total = 0;
            while ((read = reader.read(buffer)) != -1) {
                total += read;
                if (total > MAX_RESPONSE_BYTES) {
                    throw new IOException("session response was too large");
                }
                json.append(buffer, 0, read);
            }
        } finally {
            connection.disconnect();
        }
        Matcher id = ID.matcher(json);
        if (!id.find()) {
            throw new IOException("session response had no UUID");
        }
        return new AuthenticatedProfile(parseUuid(id.group(1)), parseProperties(json.toString()));
    }

    static List<TextureProperty> parseProperties(String json) {
        List<TextureProperty> properties = new ArrayList<>();
        Matcher matcher = PROPERTY.matcher(json == null ? "" : json);
        while (matcher.find()) {
            properties.add(new TextureProperty(matcher.group(1), matcher.group(2), matcher.group(3)));
        }
        return properties;
    }

    private static UUID parseUuid(String value) {
        return UUID.fromString(value.substring(0, 8) + "-" + value.substring(8, 12)
                + "-" + value.substring(12, 16) + "-" + value.substring(16, 20)
                + "-" + value.substring(20));
    }

    private static KeyPair createKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(1024);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    static final class AuthenticatedProfile {
        final UUID uuid;
        final List<TextureProperty> properties;

        AuthenticatedProfile(UUID uuid, List<TextureProperty> properties) {
            this.uuid = uuid;
            this.properties = properties;
        }
    }
}
