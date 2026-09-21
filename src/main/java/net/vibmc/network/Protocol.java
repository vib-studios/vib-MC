package net.vibmc.network;

/** Protocol constants for the natively supported Minecraft 1.12.2 (protocol 340). */
public final class Protocol {
    public static final int VERSION = 340;
    public static final String NAME = "1.12.2";
    public static final int MAX_STRING_LENGTH = 32767;
    public static final int MAX_PACKET_LENGTH = 1 << 21;

    private Protocol() {}
}