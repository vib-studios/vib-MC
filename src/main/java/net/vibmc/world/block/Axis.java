package net.vibmc.world.block;

/** The three block axes, as encoded in the low six bits of a pre-1.13 log data nibble. */
public enum Axis {
    X, Y, Z;

    /** The axis a log state points along, from its data value. */
    public static Axis fromLogData(int data) {
        switch (data & 0xC) {
            case 0x4: return X;
            case 0x8: return Z;
            default: return Y;
        }
    }
}