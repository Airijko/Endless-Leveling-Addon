package com.airijko.endlessleveling.commands;

import com.airijko.endlessleveling.compatibility.EndlessLevelingCompatibility;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

public class ExampleCommand extends AbstractCommand {

    public ExampleCommand(String name, String description) {
        super(name, description);
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        boolean compatibilityAvailable = EndlessLevelingCompatibility.isAvailable();
        context.sendMessage(Message.raw("Hello from ExampleCommand!"));
        context.sendMessage(Message.raw("Endless Leveling compatibility: "
                + (compatibilityAvailable ? "available" : "missing")));
        return CompletableFuture.completedFuture(null);
    }

}
