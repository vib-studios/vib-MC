package net.vibmc.network.handler;

import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.util.List;

import javax.crypto.Cipher;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SessionAuthenticationTest {
    @Test
    void decryptsStandardLoginEncryptionPayloads() throws Exception {
        KeyPair pair = SessionAuthentication.keyPair();
        byte[] secret = new byte[16];
        for (int index = 0; index < secret.length; index++) secret[index] = (byte) index;
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, pair.getPublic());

        assertArrayEquals(secret,
                SessionAuthentication.rsaDecrypt(cipher.doFinal(secret), pair.getPrivate()));
    }

    @Test
    void parsesForwardedTextureProperties() {
        List<TextureProperty> properties = SessionAuthentication.parseProperties(
                "[{\"name\":\"textures\",\"value\":\"base64-value\",\"signature\":\"signed\"}]");

        assertEquals(1, properties.size());
        assertEquals("textures", properties.get(0).getName());
        assertEquals("base64-value", properties.get(0).getValue());
        assertEquals("signed", properties.get(0).getSignature());
    }
}
