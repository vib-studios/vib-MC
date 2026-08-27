package net.vibmc.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonTextTest {
    @Test
    void escapesUntrustedTextForJsonComponents() {
        assertEquals("{\"text\":\"quote: \\\" slash: \\\\ line\\n tab\\t\\u0001\"}",
                JsonText.component("quote: \" slash: \\ line\n tab\t\u0001"));
    }

    @Test
    void extractsTextComponentsForConsoleLogging() {
        assertEquals("§aSet anmvc's game mode to creative.",
                JsonText.toConsoleText("{\"text\":\"§aSet anmvc's game mode to creative.\"}"));
    }

    @Test
    void handlesNullAsAJsonNull() {
        assertEquals("null", JsonText.quote(null));
    }
}
