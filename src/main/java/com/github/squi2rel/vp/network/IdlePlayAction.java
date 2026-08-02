package com.github.squi2rel.vp.network;

public enum IdlePlayAction {
    ADD(0),
    REMOVE(1),
    SET_PRIORITY(2),
    CLEAR(3),
    SET_MODE(4),
    ADJUST_PRIORITY(5);

    private final int id;

    IdlePlayAction(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static IdlePlayAction fromId(int id) {
        for (IdlePlayAction action : values()) {
            if (action.id == id) return action;
        }
        return null;
    }
}
