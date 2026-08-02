package com.github.squi2rel.vp;

import com.github.squi2rel.vp.video.VideoArea;

import java.util.ArrayList;

public class ServerConfig {
    public static final int CURRENT_DATA_VERSION = 3;
    public int dataVersion = CURRENT_DATA_VERSION;
    public long saveGeneration;
    public ArrayList<VideoArea> areas = new ArrayList<>();
    public String remoteControlName = "minecraft:iron_ingot";
    public float remoteControlId = -1;
    public float remoteControlRange = 64;
    public float noControlRange = 16;
}
