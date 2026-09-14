package com.matchforge.command;

public enum CommandType {
    NEW_ORDER(1), CANCEL(2), MODIFY(3);

    private final int code;
    CommandType(int code) { this.code = code; }
    public int code() { return code; }

    public static CommandType fromCode(int code) {
        for (CommandType t : values()) {
            if (t.code == code) return t;
        }
        throw new IllegalArgumentException("Unknown command type code " + code);
    }
}
