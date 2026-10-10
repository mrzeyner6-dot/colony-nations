package com.mrzeyner.colonynations.command;

import com.mrzeyner.colonynations.Config;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Every player-facing chat line from this mod is built here.
 * Tone colors and the prefix live in code; translation files stay plain text.
 */
public final class Messages {
    private static final int PREFIX = 0xB07CFF;
    private static final int TEXT = 0xD8D2E8;
    private static final int VALUE = 0xE0C3FF;
    private static final int SUCCESS = 0xC9A6FF;
    private static final int ERROR = 0xFF7A90;

    private static final float CHIME_VOLUME = 0.4F;
    private static final float CHIME_PITCH = 1.4F;
    private static final float BASS_VOLUME = 0.4F;
    private static final float BASS_PITCH = 0.8F;
    private static final float LEVELUP_VOLUME = 0.3F;
    private static final float LEVELUP_PITCH = 1.2F;

    private Messages() {
    }

    public static int success(CommandSourceStack source, String key, Object... args) {
        source.sendSuccess(() -> prefixed(SUCCESS, false, key, args), true);
        play(source, SoundEvents.NOTE_BLOCK_CHIME.value(), CHIME_VOLUME, CHIME_PITCH);
        return 1;
    }

    public static int error(CommandSourceStack source, String key, Object... args) {
        source.sendFailure(prefixed(ERROR, false, key, args));
        play(source, SoundEvents.NOTE_BLOCK_BASS.value(), BASS_VOLUME, BASS_PITCH);
        return 0;
    }

    public static int info(CommandSourceStack source, String key, Object... args) {
        source.sendSuccess(() -> prefixed(TEXT, false, key, args), false);
        return 1;
    }

    public static void header(CommandSourceStack source, String key, Object... args) {
        source.sendSuccess(() -> prefixed(PREFIX, true, key, args), false);
    }

    public static void line(CommandSourceStack source, String key, Object... args) {
        MutableComponent body = Component.translatable(key, values(args)).withStyle(style -> style.withColor(TEXT).withBold(false));
        Component message = Component.literal("  ").append(body);
        source.sendSuccess(() -> message, false);
    }

    public static int event(CommandSourceStack source, String key, Object... args) {
        source.sendSuccess(() -> prefixed(SUCCESS, false, key, args), true);
        play(source, SoundEvents.PLAYER_LEVELUP, LEVELUP_VOLUME, LEVELUP_PITCH);
        return 1;
    }

    public static void event(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(prefixed(SUCCESS, false, key, args));
        play(player, SoundEvents.PLAYER_LEVELUP, LEVELUP_VOLUME, LEVELUP_PITCH);
    }

    /**
     * A square painted with a nation's own color, for the info color line.
     */
    public static Component swatch(int rgb) {
        return Component.literal("\u25A0").withStyle(style -> style.withColor(rgb & 0xFFFFFF).withBold(false));
    }

    /**
     * A nested translation that keeps the surrounding line's color.
     */
    public static Component plain(String key) {
        return Component.translatable(key);
    }

    private static Component prefixed(int bodyColor, boolean bold, String key, Object... args) {
        MutableComponent body = Component.translatable(key, values(args))
                .withStyle(style -> style.withColor(bodyColor).withBold(bold));
        return Component.literal("\u2726 ").withStyle(style -> style.withColor(PREFIX).withBold(bold)).append(body);
    }

    private static Object[] values(Object... args) {
        Object[] styled = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            styled[i] = value(args[i]);
        }
        return styled;
    }

    private static Object value(Object arg) {
        if (arg instanceof Component component) {
            return component;
        }
        String text = String.valueOf(arg);
        return Component.literal(text).withStyle(style -> style.withColor(VALUE).withBold(false));
    }

    private static void play(CommandSourceStack source, SoundEvent sound, float volume, float pitch) {
        if (source.getEntity() instanceof ServerPlayer player) {
            play(player, sound, volume, pitch);
        }
    }

    private static void play(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        if (!Config.CHAT_SOUNDS.get()) {
            return;
        }
        player.playNotifySound(sound, SoundSource.PLAYERS, volume, pitch);
    }
}
