package dev.gnomebot.app.discord;

import dev.gnomebot.app.util.SnowFlake;
import discord4j.core.object.emoji.Emoji;

import java.util.regex.Pattern;

public interface Emojis {
	Pattern GUILD_EMOJI_PATTERN = Pattern.compile("<a?:\\w+:\\d+>");
	Pattern GUILD_EMOJI_PATTERN_GROUPS = Pattern.compile("<a?:(\\w+):(\\d+)>");

	static Emoji custom(long id, String name, boolean animated) {
		return Emoji.custom(SnowFlake.convert(id), name, animated);
	}

	static Emoji unicode(String name) {
		return Emoji.unicode(name);
	}

	Emoji YES = custom(1295675217863250023L, "Yes", false);
	Emoji NO = custom(1295675226037682207L, "No", false);

	static Emoji yesNo(boolean yes) {
		return yes ? YES : NO;
	}

	Emoji POLICE_CAR = unicode("🚓");
	Emoji NO_ENTRY = unicode("🚫");
	Emoji STAR = unicode("⭐");
	Emoji WAVE = unicode("👋");
	Emoji BOOT = unicode("\uD83D\uDC62");
	Emoji VOTENONE = custom(1295674246898516008L, "RemoveVote", false);
	Emoji VOTEUP = custom(1295674258202300468L, "Upvote", false);
	Emoji VOTEDOWN = custom(1295674268121567292L, "Downvote", false);
	Emoji DOWNLOAD = custom(1295682331016826900L, "Download", false);
	Emoji GNOME_PING = custom(1295673389284982784L, "Pinged", false);
	Emoji GNOME_HAHA_YES = custom(1295673202768478261L, "GnomeYes", false);
	Emoji GNOME_HAHA_NO = custom(1295673187543023679L, "GnomeNo", false);
	Emoji GNOME_SHERLOCK = custom(1295672789176418394L, "SherlockGnomes", false);
	Emoji RAGE = unicode("\uD83D\uDE21");
	Emoji STOP = unicode("\uD83D\uDED1");
	Emoji REFRESH = unicode("\uD83D\uDD04");
	Emoji ALERT = custom(1295672502961438822L, "Alert", true);
	Emoji WARNING = custom(1295672318416388127L, "Warning", false);
	Emoji PENCIL = unicode("\uD83D\uDCDD");
	Emoji PASTE = custom(1330932829370056788L, "Paste", false);

	Emoji[] NUMBERS = {
		Emoji.unicode("1️⃣"),
		Emoji.unicode("2️⃣"),
		Emoji.unicode("3️⃣"),
		Emoji.unicode("4️⃣"),
		Emoji.unicode("5️⃣"),
		Emoji.unicode("6️⃣"),
		Emoji.unicode("7️⃣"),
		Emoji.unicode("8️⃣"),
		Emoji.unicode("9️⃣"),
		Emoji.unicode("\uD83D\uDD1F"),
	};

	String[] NUMBER_STRINGS = {
		":one:",
		":two:",
		":three:",
		":four:",
		":five:",
		":six:",
		":seven:",
		":eight:",
		":nine:",
		":keycap_ten:",
	};

	static String stripEmojis(String content) {
		return GUILD_EMOJI_PATTERN.matcher(content).replaceAll("").trim();
	}
}
