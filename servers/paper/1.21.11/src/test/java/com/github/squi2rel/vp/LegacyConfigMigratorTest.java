package com.github.squi2rel.vp;

import com.github.squi2rel.vp.video.IdlePlayEntry;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyConfigMigratorTest {
    @Test
    void migratesIdleUrlsToVersionThreeWithoutInventingOwners() {
        JsonObject input = JsonParser.parseString("""
                {
                  "dataVersion": 2,
                  "areas": [{
                    "name": "area",
                    "screens": [{
                      "name": "screen",
                      "vertices": [{"x":0,"y":0,"z":0}],
                      "idlePlayUrls": ["https://example.com/video"]
                    }]
                  }]
                }
                """).getAsJsonObject();

        LegacyConfigMigrator.Result result = LegacyConfigMigrator.migrate(input);
        JsonObject migrated = result.root();
        JsonObject screen = migrated.getAsJsonArray("areas").get(0).getAsJsonObject()
                .getAsJsonArray("screens").get(0).getAsJsonObject();
        JsonObject entry = screen.getAsJsonArray("idlePlayEntries").get(0).getAsJsonObject();

        assertTrue(result.migrated());
        assertEquals(ServerConfig.CURRENT_DATA_VERSION, migrated.get("dataVersion").getAsInt());
        assertFalse(screen.has("idlePlayUrls"));
        assertEquals("https://example.com/video", entry.get("url").getAsString());
        assertEquals(IdlePlayEntry.UNKNOWN_UUID.toString(), entry.get("addedBy").getAsString());
        assertEquals("", entry.get("addedByName").getAsString());
        assertEquals(0, entry.get("priority").getAsInt());
    }
}
