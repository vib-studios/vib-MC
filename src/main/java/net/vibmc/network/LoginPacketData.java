package net.vibmc.network;

public final class LoginPacketData {
    public enum Type { START, ENCRYPTION_RESPONSE, UNKNOWN }
    private final Type type;
    private final String username;
    private final byte[] encryptedSecret;
    private final byte[] encryptedToken;

    public LoginPacketData(Type type, String username, byte[] encryptedSecret, byte[] encryptedToken) {
        this.type = type;
        this.username = username;
        this.encryptedSecret = encryptedSecret == null ? null : encryptedSecret.clone();
        this.encryptedToken = encryptedToken == null ? null : encryptedToken.clone();
    }
    public Type type() { return type; }
    public String username() { return username; }
    public byte[] encryptedSecret() { return encryptedSecret == null ? null : encryptedSecret.clone(); }
    public byte[] encryptedToken() { return encryptedToken == null ? null : encryptedToken.clone(); }
}
