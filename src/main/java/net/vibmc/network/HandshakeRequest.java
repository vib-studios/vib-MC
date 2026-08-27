package net.vibmc.network;

public final class HandshakeRequest {
    private final int protocolVersion;
    private final String address;
    private final int port;
    private final int nextState;

    public HandshakeRequest(int protocolVersion, String address, int port, int nextState) {
        this.protocolVersion = protocolVersion;
        this.address = address;
        this.port = port;
        this.nextState = nextState;
    }

    public int protocolVersion() { return protocolVersion; }
    public String address() { return address; }
    public int port() { return port; }
    public int nextState() { return nextState; }
}
