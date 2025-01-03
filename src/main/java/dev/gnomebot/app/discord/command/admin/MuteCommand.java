package dev.gnomebot.app.discord.command.admin;

import com.mongodb.client.model.Updates;
import dev.gnomebot.app.data.GnomeAuditLogEntry;
import dev.gnomebot.app.discord.ComponentEventWrapper;
import dev.gnomebot.app.discord.DM;
import dev.gnomebot.app.discord.EmbedColor;
import dev.gnomebot.app.discord.Emojis;
import dev.gnomebot.app.discord.QuoteHandler;
import dev.gnomebot.app.discord.command.ApplicationCommands;
import dev.gnomebot.app.discord.command.ChatInputInteractionEventWrapper;
import dev.gnomebot.app.discord.legacycommand.CommandContext;
import dev.gnomebot.app.server.AuthLevel;
import dev.gnomebot.app.util.EmbedBuilder;
import dev.gnomebot.app.util.MessageBuilder;
import dev.gnomebot.app.util.Utils;
import dev.latvian.apps.webutils.FormattingUtils;
import discord4j.core.object.component.ActionComponent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.component.LayoutComponent;
import discord4j.core.object.component.SelectMenu;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.rest.util.AllowedMentions;
import discord4j.rest.util.Permission;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class MuteCommand extends ApplicationCommands {
	public static void run(ChatInputInteractionEventWrapper event) {
		event.acknowledgeEphemeral();
		event.context.checkGlobalPerms(Permission.MODERATE_MEMBERS);

		if (!event.context.gc.mutedRole.isSet()) {
			throw error("Mute role not set!");
		}

		var user = event.get("user").asUser().orElse(null);
		var seconds = event.get("time").asSeconds().orElse(Integer.MAX_VALUE);

		var reason0 = event.get("reason").asString();
		var reason = reason0.isEmpty() ? "Not specified" : reason0;

		if (user == null) {
			throw error("User not found!");
		} else if (user.isBot() || event.context.gc.getAuthLevel(user.getId().asLong()).is(AuthLevel.ADMIN)) {
			throw error("Nice try.");
		}

		event.context.allowedMentions = AllowedMentions.builder().allowUser(user.getId()).allowUser(event.context.sender.getId()).build();
		var dm = DM.send(event.context.handler, user.getUserData(), "You've been muted on " + event.context.gc + ", reason: " + reason, true).isPresent();

		if (dm) {
			event.context.reply(event.context.sender.getMention() + " muted " + user.getMention());
		} else {
			event.context.reply(event.context.sender.getMention() + " muted " + user.getMention() + ": " + reason);
		}

		var discordMember = event.context.gc.members.findFirst(user.getId().asLong());

		if (discordMember == null) {
			throw error("User not found!");
		}

		event.context.gc.mutedRole.role().ifPresent(r -> r.add(user.getId().asLong(), "Muted"));
		event.context.gc.unmute(user.getId().asLong(), seconds, reason);

		event.context.gc.adminLogChannelEmbed(user.getUserData(), event.context.gc.adminLogChannel, spec -> {
			spec.description("Muted " + user.getMention());
			spec.author(user.getTag() + " was muted", user.getAvatarUrl());
			spec.inlineField("Expires", Utils.formatRelativeDate(Instant.ofEpochSecond(Instant.now().getEpochSecond() + seconds)));
			spec.inlineField("Reason", reason);
			spec.inlineField("DM successful", dm ? "Yes" : "No");
			spec.footer(event.context.sender.getUsername(), event.context.sender.getAvatarUrl());
		});

		event.context.gc.auditLog(GnomeAuditLogEntry.builder(GnomeAuditLogEntry.Type.MUTE)
				.user(user)
				.source(event.context.sender)
				.content(reason)
				.flags(GnomeAuditLogEntry.Flags.DM, dm)
		);

		// m.addReaction(DiscordHandler.EMOJI_COMMAND_ERROR).block();
		// ReactionHandler.addListener();

		event.respond("Muted! DM successful: " + dm);
	}

	public static void mute(CommandContext context, Member m, long seconds, String reason, String auto) {
		var discordMember = context.gc.members.findFirst(m);
		var expires = new Date(System.currentTimeMillis() + seconds * 1000L);
		var expiresInstant = expires.toInstant();

		if (discordMember != null) {
			discordMember.update(Updates.set("muted", expires));
		}

		context.gc.unmute(m.getId().asLong(), seconds, reason);

		Message contextMessage;

		if (context.gc.autoMuteEmbed.get()) {
			var embedBuilder = EmbedBuilder.create();

			embedBuilder.color(EmbedColor.RED);

			if (!auto.isEmpty()) {
				embedBuilder.description(auto);
			} else if (context.sender.getId().equals(m.getId())) {
				embedBuilder.description(context.sender.getMention() + " was auto-muted!");
			} else {
				embedBuilder.description(context.sender.getMention() + " muted " + m.getMention() + "!");
			}

			embedBuilder.inlineField("Expires", Utils.formatRelativeDate(expiresInstant));
			embedBuilder.inlineField("Reason", reason);

			contextMessage = context.reply(embedBuilder);
		} else {
			contextMessage = null;
		}

		if (context.gc.mutedRole.is(m)) {
			return;
		} else {
			context.gc.mutedRole.role().ifPresent(r -> r.add(m.getId().asLong(), "Muted"));
		}

		List<LayoutComponent> adminButtons = new ArrayList<>();

		if (auto.isEmpty()) {
			if (contextMessage != null) {
				adminButtons.add(ActionRow.of(Button.link(QuoteHandler.getMessageURL(context.gc.guildId, context.channelInfo.id, contextMessage.getId().asLong()), "Context")));
			}
		} else {
			adminButtons.add(ActionRow.of(SelectMenu.of(FormattingUtils.trim("punish/" + m.getId().asString() + "/" + ComponentEventWrapper.encode(reason), 100),
					SelectMenu.Option.of("Ban", "ban").withEmoji(Emojis.NO_ENTRY),
					SelectMenu.Option.of("Kick", "kick").withEmoji(Emojis.BOOT),
					// SelectMenu.Option.of("Warn", "warn").withEmoji(Emojis.WARNING),
					SelectMenu.Option.of("Unmute", "unmute").withEmoji(Emojis.YES)
			).withPlaceholder("Select Action").withMinValues(0).withMaxValues(1)));

			if (contextMessage != null) {
				adminButtons.add(ActionRow.of(Button.link(QuoteHandler.getMessageURL(context.gc.guildId, context.channelInfo.id, contextMessage.getId().asLong()), "Context")));
			}
		}

		var embed = EmbedBuilder.create();
		embed.color(EmbedColor.RED);
		embed.author(m.getTag() + " has been muted!", m.getAvatarUrl());
		embed.description(context.sender.getMention() + " muted " + m.getMention());
		embed.inlineField("Reason", reason);
		embed.inlineField("Expires", Utils.formatRelativeDate(expiresInstant));

		if (!auto.isEmpty()) {
			embed.field("Message", context.message.getContent());
		}

		context.gc.adminLogChannel.messageChannel().map(c -> c.createMessage(MessageBuilder.create(embed).components(adminButtons)).subscribe(m1 -> {
			List<ActionComponent> replyButtons = new ArrayList<>();

			if (context.gc.muteAppealChannel.isSet()) {
				replyButtons.add(Button.link(QuoteHandler.getChannelURL(context.gc.guildId, context.gc.muteAppealChannel.get()), "Appeal"));
			}

			replyButtons.add(Button.link(QuoteHandler.getMessageURL(context.gc.guildId, context.gc.adminLogChannel.get(), m1.getId().asLong()), "Take Action"));

			if (contextMessage != null) {
				contextMessage.edit(MessageBuilder.create().addComponent(ActionRow.of(replyButtons)).toMessageEditSpec()).subscribe();
			}
		}));

		List<ActionComponent> dmButtons = new ArrayList<>();

		if (contextMessage != null) {
			dmButtons.add(Button.link(QuoteHandler.getMessageURL(context.gc.guildId, context.channelInfo.id, contextMessage.getId().asLong()), "Context"));
		}

		if (context.gc.muteAppealChannel.isSet()) {
			dmButtons.add(Button.link(QuoteHandler.getChannelURL(context.gc.guildId, context.gc.muteAppealChannel.get()), "Appeal"));
		}

		var dm = DM.send(context.handler, m.getUserData(), MessageBuilder.create()
						.addEmbed(embed)
						.components(dmButtons.isEmpty() ? null : Collections.singletonList(ActionRow.of(dmButtons)))
				, true).isPresent();

		context.gc.auditLog(GnomeAuditLogEntry.builder(GnomeAuditLogEntry.Type.MUTE)
				.user(m)
				.source(context.handler.selfId)
				.content(reason)
				.flags(GnomeAuditLogEntry.Flags.DM, dm)
		);
	}
}
