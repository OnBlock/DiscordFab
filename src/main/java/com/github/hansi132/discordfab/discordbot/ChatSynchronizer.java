package com.github.hansi132.discordfab.discordbot;

import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.github.hansi132.discordfab.DiscordFab;
import com.github.hansi132.discordfab.discordbot.api.text.DiscordCompatibleTextFormat;
import com.github.hansi132.discordfab.discordbot.config.section.chatsync.ChatSynchronizerConfigSection;
import com.github.hansi132.discordfab.discordbot.user.DiscordBroadcaster;
import com.github.hansi132.discordfab.discordbot.util.DatabaseUtils;
import com.google.common.collect.Maps;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kilocraft.essentials.api.text.TextFormat;
import org.kilocraft.essentials.api.user.User;
import org.kilocraft.essentials.chat.ServerChat;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class ChatSynchronizer {
    private static final DiscordFab DISCORD_FAB = DiscordFab.getInstance();
    private static final ChatSynchronizerConfigSection CONFIG = DISCORD_FAB.getConfig().chatSynchronizer;
    private final Map<UUID, net.dv8tion.jda.api.entities.User> map = Maps.newHashMap();
    private final DiscordBroadcaster discordBroadcaster;
    private final Guild guild = DISCORD_FAB.getGuild();

    public ChatSynchronizer() {
        this.discordBroadcaster = new DiscordBroadcaster();
    }

    public void onGameChat(@NotNull final User user, @NotNull final String string) {
        WebhookMessageBuilder builder = new WebhookMessageBuilder();

        if (DatabaseUtils.isLinked(user.getUuid())) {
            net.dv8tion.jda.api.entities.User discordUser = this.getJDAUser(user.getUuid());
            if (discordUser.getAvatarUrl() != null) {
                builder.setAvatarUrl(discordUser.getAvatarUrl());
                builder.setUsername(discordUser.getName());
            }
        } else {
            builder.setAvatarUrl(CONFIG.defaultAvatarURL.isEmpty() ? CONFIG.defaultAvatarURL : getMCAvatarURL(user.getUuid()));
            builder.setUsername(user.getUsername());
        }

        String content = TextFormat.clearColorCodes(string.replaceAll("@", ""));
        builder.setContent(content);

        this.discordBroadcaster.send(builder.build());
    }

    public void onDiscordChat(final Member member, @NotNull final String string) {
        ServerChat.Channel.PUBLIC.send(
                new LiteralText("")
                        .append(new LiteralText(ServerChat.Channel.PUBLIC.getPrefix()).styled((style) ->
                                style.setHoverEvent(
                                        new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                                new LiteralText("From Discord").formatted(Formatting.BLUE))
                                )
                        ))
                        .append(" ")
                        .append(DiscordCompatibleTextFormat.clearAllDiscord(
                                string.replace("%USER_RANKED_DISPLAYNAME%", member.getEffectiveName()))
                        )
        );
    }

    private net.dv8tion.jda.api.entities.User getJDAUser(@NotNull final UUID uuid) {
        if (this.map.containsKey(uuid)) {
            return this.map.get(uuid);
        }

        long discordId = DatabaseUtils.getLinkedUserId(uuid);
        net.dv8tion.jda.api.entities.User user = Objects.requireNonNull(guild.getMemberById(discordId)).getUser();
        this.map.put(uuid, user);
        return user;
    }

    private static String getMCAvatarURL(@NotNull final UUID uuid) {
        AvatarRenderType renderType = AvatarRenderType.getByName(CONFIG.renderOptions.renderType);
        if (renderType == null) {
            renderType = AvatarRenderType.AVATAR;
        }

        return "https://crafatar.com/" + renderType.code + "/" + uuid.toString() + "?size=" + CONFIG.renderOptions.size +
                (CONFIG.renderOptions.showOverlay ? "&overlay" : "");
    }

    private enum AvatarRenderType {
        AVATAR("avatars"),
        HEAD("renders/head"),
        BODY("renders/body");

        private final String code;

        AvatarRenderType(final String code) {
            this.code = code;
        }

        @Nullable
        public static AvatarRenderType getByName(@NotNull final String name) {
            for (AvatarRenderType value : values()) {
                if (name.equalsIgnoreCase(value.name())) {
                    return value;
                }
            }

            return null;
        }
    }
}