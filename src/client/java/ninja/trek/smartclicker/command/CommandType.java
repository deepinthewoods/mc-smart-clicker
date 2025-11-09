package ninja.trek.smartclicker.command;

public enum CommandType {
    LEFT_CLICK("Left Click", false),
    RIGHT_CLICK("Right Click", false),
    LEFT_HOLD("Left Hold", false),
    RIGHT_HOLD("Right Hold", false),
    BELT_SELECT("Belt Select", true),
    PAN_MOUSE("Pan Mouse", true),
    TILT_MOUSE("Tilt Mouse", true),
    FACE("Face Direction", true),
    JUMP("Jump", false),
    CROUCH("Crouch", true),
    MOVE("Move", true),
    SET_YAW("setYaw", true),
    YAW("yaw", true),
    SWAP_TOOL("swap tool <", true);

    private final String displayName;
    private final boolean hasParameter;

    CommandType(String displayName, boolean hasParameter) {
        this.displayName = displayName;
        this.hasParameter = hasParameter;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean hasParameter() {
        return hasParameter;
    }

    public String getDefaultParameter() {
        return switch (this) {
            case BELT_SELECT -> "0";
            case PAN_MOUSE, TILT_MOUSE -> "0.0";
            case FACE -> "N";
            case CROUCH -> "ON";
            case MOVE -> "w";
            case SET_YAW, YAW -> "0.0";
            case SWAP_TOOL -> "10";
            default -> "";
        };
    }
}
