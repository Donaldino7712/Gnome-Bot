package dev.gnomebot.app;

import dev.gnomebot.app.data.GuildCollections;
import dev.latvian.apps.webutils.ansi.Ansi;
import dev.latvian.apps.webutils.ansi.AnsiComponent;
import discord4j.core.object.entity.Guild;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public record BrainEventType(String name, String symbol, Function<Object, AnsiComponent> color) {
	public static final BrainEventType MESSAGE_CREATED_NO_ROLE = new BrainEventType("message_created_no_role", "■", Ansi::lightGray);
	public static final BrainEventType MESSAGE_CREATED_ANY_ROLE = new BrainEventType("message_created_any_role", "■", Ansi::yellow);
	public static final BrainEventType UNKNOWN_MESSAGE = new BrainEventType("unknown_message", "■", Ansi::teal);
	public static final BrainEventType MESSAGE_CREATED_ADMIN = new BrainEventType("message_created_admin", "■", Ansi::purple);
	public static final BrainEventType MESSAGE_CREATED_BOT = new BrainEventType("message_created_bot", "■", Ansi::green);
	public static final BrainEventType MESSAGE_EDITED = new BrainEventType("message_edited", "■", Ansi::orange);
	public static final BrainEventType MESSAGE_DELETED = new BrainEventType("message_deleted", "■", Ansi::red);
	public static final BrainEventType SUSPICIOUS_MESSAGE = new BrainEventType("suspicious_message", "■", Ansi::darkRed);
	public static final BrainEventType COMMAND_SUCCESS = new BrainEventType("command_success", "◆", Ansi::blue);
	public static final BrainEventType COMMAND_FAIL = new BrainEventType("command_fail", "◆", Ansi::red);
	public static final BrainEventType REACTION_ADDED = new BrainEventType("reaction_added", "\uD83D\uDDF8", Ansi::green); // 🗸
	public static final BrainEventType REACTION_REMOVED = new BrainEventType("reaction_removed", "\uD83D\uDDF8", Ansi::red); // 🗸
	public static final BrainEventType VOICE_JOINED = new BrainEventType("voice_joined", "♪", Ansi::green);
	public static final BrainEventType VOICE_LEFT = new BrainEventType("voice_left", "♪", Ansi::red);
	public static final BrainEventType VOICE_CHANGED = new BrainEventType("voice_changed", "♪", Ansi::yellow);
	public static final BrainEventType REFRESHED_GUILD_CACHE = new BrainEventType("refreshed_guild_cache", "\uD83D\uDE7E", Ansi::lightGray); // 🙾
	public static final BrainEventType REFRESHED_CHANNEL_CACHE = new BrainEventType("refreshed_channel_cache", "\uD83D\uDE7E", Ansi::magenta); // 🙾
	public static final BrainEventType REFRESHED_PINGS = new BrainEventType("refreshed_pings", "\uD83D\uDE7E", Ansi::green); // 🙾
	public static final BrainEventType REFRESHED_ROLE_CACHE = new BrainEventType("refreshed_role_cache", "\uD83D\uDE7E", Ansi::yellow); // 🙾
	public static final BrainEventType MEMBER_JOINED = new BrainEventType("member_joined", "⬤", Ansi::blue);
	public static final BrainEventType MEMBER_LEFT = new BrainEventType("member_left", "⬤", Ansi::red);
	public static final BrainEventType MEMBER_MUTED = new BrainEventType("member_muted", "☠", Ansi::red);
	public static final BrainEventType MEMBER_BANNED = new BrainEventType("member_banned", "☠", Ansi::darkRed);
	public static final BrainEventType WEB_REQUEST = new BrainEventType("web_request", "◆", Ansi::cyan);
	public static final BrainEventType PRESENCE_UPDATED = new BrainEventType("presence_updated", "◆", Ansi::lightGray);
	public static final BrainEventType AUDIT_LOG = new BrainEventType("audit_log", "◆", Ansi::yellow);

	public BrainEvent build(long guild) {
		return new BrainEvent(this, guild);
	}

	public BrainEvent build(GuildCollections gc) {
		return build(gc.guildId);
	}

	public BrainEvent build(@Nullable Guild guild) {
		return build(guild == null ? 0L : guild.getId().asLong());
	}
}
