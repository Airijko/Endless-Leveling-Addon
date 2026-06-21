package com.airijko.endlessleveling.events;

import com.airijko.endlessleveling.managers.ExampleFeatureManager;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;

public class ExampleEvent {

    public static void onPlayerReady(PlayerReadyEvent event) {
        if (!ExampleFeatureManager.get().isExampleEventsEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        if (player == null
                || com.airijko.endlessleveling.util.WorldContextUtil.isEndlessDisabled(player.getWorld())) {
            return;
        }
        player.getPlayerRef().sendMessage(Message.raw("Welcome " + player.getPlayerRef().getUsername()));
    }
}
