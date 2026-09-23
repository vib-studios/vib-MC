package net.vibmc.registry;

import com.github.retrooper.packetevents.protocol.player.ClientVersion;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps PacketEvents ClientVersion to ViaVersion data tokens.
 * Used to select the correct identifier/mapping files.
 */
public final class MappingVersions {
    private MappingVersions() {}

    // ViaVersion version tokens that exist as identifiers-*.nbt
    private static final Map<ClientVersion, String> VERSION_TOKENS;
    static {
        Map<ClientVersion, String> map = new HashMap<>();
        map.put(ClientVersion.V_1_8, "1.8");
        map.put(ClientVersion.V_1_9, "1.9");
        map.put(ClientVersion.V_1_9_1, "1.9");
        map.put(ClientVersion.V_1_9_2, "1.9");
        map.put(ClientVersion.V_1_9_3, "1.9.4");
        map.put(ClientVersion.V_1_10, "1.10");
        map.put(ClientVersion.V_1_11, "1.11");
        map.put(ClientVersion.V_1_11_1, "1.11");
        map.put(ClientVersion.V_1_12, "1.12");
        map.put(ClientVersion.V_1_12_1, "1.12");
        map.put(ClientVersion.V_1_12_2, "1.12");
        map.put(ClientVersion.V_1_13, "1.13");
        map.put(ClientVersion.V_1_13_1, "1.13");
        map.put(ClientVersion.V_1_13_2, "1.13.2");
        map.put(ClientVersion.V_1_14, "1.14");
        map.put(ClientVersion.V_1_14_1, "1.14");
        map.put(ClientVersion.V_1_14_2, "1.14");
        map.put(ClientVersion.V_1_14_3, "1.14");
        map.put(ClientVersion.V_1_14_4, "1.14");
        map.put(ClientVersion.V_1_15, "1.15");
        map.put(ClientVersion.V_1_15_1, "1.15");
        map.put(ClientVersion.V_1_15_2, "1.15");
        map.put(ClientVersion.V_1_16, "1.16");
        map.put(ClientVersion.V_1_16_1, "1.16");
        map.put(ClientVersion.V_1_16_2, "1.16.2");
        map.put(ClientVersion.V_1_16_3, "1.16.2");
        map.put(ClientVersion.V_1_16_4, "1.16.2");
        map.put(ClientVersion.V_1_17, "1.17");
        map.put(ClientVersion.V_1_17_1, "1.17");
        map.put(ClientVersion.V_1_18, "1.18");
        map.put(ClientVersion.V_1_18_2, "1.18");
        map.put(ClientVersion.V_1_19, "1.19");
        map.put(ClientVersion.V_1_19_1, "1.19");
        map.put(ClientVersion.V_1_19_3, "1.19.3");
        map.put(ClientVersion.V_1_19_4, "1.19.4");
        map.put(ClientVersion.V_1_20, "1.20");
        map.put(ClientVersion.V_1_20_2, "1.20.2");
        map.put(ClientVersion.V_1_20_3, "1.20.3");
        map.put(ClientVersion.V_1_20_5, "1.20.5");
        map.put(ClientVersion.V_1_21, "1.21");
        map.put(ClientVersion.V_1_21_2, "1.21.2");
        map.put(ClientVersion.V_1_21_4, "1.21.4");
        map.put(ClientVersion.V_1_21_5, "1.21.5");
        map.put(ClientVersion.V_1_21_6, "1.21.6");
        map.put(ClientVersion.V_1_21_7, "1.21.7");
        map.put(ClientVersion.V_1_21_9, "1.21.9");
        map.put(ClientVersion.V_1_21_11, "1.21.11");
        map.put(ClientVersion.V_26_1, "26.1");
        map.put(ClientVersion.V_26_2, "26.2");
        map.put(ClientVersion.V_26_3, "26.3");
        VERSION_TOKENS = Collections.unmodifiableMap(map);
    }

    public static String token(ClientVersion version) {
        String t = VERSION_TOKENS.get(version);
        if (t != null) return t;
        // Fallback to release name
        String release = version.getReleaseName();
        if (release != null) {
            // release may contain slash for combined versions like "1.20/1.20.1"
            String first = release.split("/")[0].trim();
            return first;
        }
        throw new IllegalArgumentException("No ViaVersion token for " + version);
    }

    public static boolean hasIdentifiers(String token) {
        // We vendored identifiers for these tokens
        return token.equals("1.8") || token.equals("1.9") || token.equals("1.9.4") ||
                token.equals("1.10") || token.equals("1.11") || token.equals("1.12") ||
                token.equals("1.13") || token.equals("1.13.2") || token.equals("1.14") ||
                token.equals("1.15") || token.equals("1.16") || token.equals("1.16.2") ||
                token.equals("1.17") || token.equals("1.18") || token.equals("1.19") ||
                token.equals("1.19.3") || token.equals("1.19.4") || token.equals("1.20") ||
                token.equals("1.20.2") || token.equals("1.20.3") || token.equals("1.20.5") ||
                token.equals("1.21") || token.equals("1.21.2") || token.equals("1.21.4") ||
                token.equals("1.21.5") || token.equals("1.21.6") || token.equals("1.21.7") ||
                token.equals("1.21.9") || token.equals("1.21.11") || token.equals("26.1") ||
                token.equals("26.2") || token.equals("26.3");
    }
}
