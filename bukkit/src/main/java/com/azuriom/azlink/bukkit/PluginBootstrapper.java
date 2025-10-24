package com.azuriom.azlink.bukkit;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.keys.DialogKeys;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class PluginBootstrapper implements PluginBootstrap {

    @Override
    public void bootstrap(BootstrapContext context) {
        TextComponent.Builder builderComponent = Component.text();
        builderComponent.append(Component.text("Exit" + NamedTextColor.RED));
        TextComponent component = builderComponent.build();
        context.getLifecycleManager().registerEventHandler(RegistryEvents.DIALOG.compose()
                .newHandler(event -> event.registry().register(
                        DialogKeys.create(Key.key("papermc:custom_dialog")),
                        builder -> builder
                                // Build your dialog here ...
                                .base(DialogBase.builder(Component.text("Title")).build())
                                .type(DialogType.serverLinks(
                                        ActionButton.builder(component).build(), 2, 20
                                ))
                )));
    }
}
