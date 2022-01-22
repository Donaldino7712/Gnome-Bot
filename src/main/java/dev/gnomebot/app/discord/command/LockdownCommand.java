package dev.gnomebot.app.discord.command;

import dev.gnomebot.app.Assets;
import dev.gnomebot.app.data.GnomeAuditLogEntry;
import dev.gnomebot.app.discord.DM;
import dev.gnomebot.app.discord.EmbedColors;
import dev.gnomebot.app.discord.Emojis;
import dev.gnomebot.app.discord.MemberHandler;
import dev.gnomebot.app.discord.legacycommand.DiscordCommandException;
import discord4j.core.object.entity.Member;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author LatvianModder
 */
public class LockdownCommand extends ApplicationCommands {
	@RootCommand
	public static final CommandBuilder COMMAND = root("lockdown")
			.add(sub("enable")
					.description("Enables lockdown mode")
					.add(time("kick_time", false))
					.run(LockdownCommand::enable)
			)
			.add(sub("disable")
					.description("Disables lockdown mode")
					.run(LockdownCommand::disable)
			);

	private static void enable(ApplicationCommandEventWrapper event) throws DiscordCommandException {
		event.context.checkSenderAdmin();

		boolean wasOff = !event.context.gc.lockdownMode.get();

		long sec = Math.min(event.get("kick_time").asSeconds().orElse(300L), 86400L);

		if (wasOff) {
			event.acknowledge();
		} else {
			event.acknowledgeEphemeral();
		}

		if (wasOff) {
			event.context.gc.lockdownMode.set(true);
			event.context.gc.lockdownMode.save();

			event.context.gc.auditLog(GnomeAuditLogEntry.builder(GnomeAuditLogEntry.Type.LOCKDOWN_ENABLED)
					.source(event.context.sender)
			);

			if (event.context.channelInfo == null || !event.context.gc.adminLogChannel.is(event.context.channelInfo.id)) {
				event.context.gc.adminLogChannelEmbed(spec -> {
					spec.title("Lockdown mode enabled!");
					spec.description(Emojis.ALERT.asFormat());
					spec.thumbnail(Assets.EMERGENCY.getPath());
					spec.author(event.context.sender.getUsername(), null, event.context.sender.getAvatarUrl());
				});
			}

			if (sec > 0L) {
				long time = Instant.now().getEpochSecond() - sec;
				List<Member> list = event.context.gc.getGuild().getMembers().filter(member -> member.getJoinTime().isPresent() && member.getJoinTime().get().getEpochSecond() > time).toStream().collect(Collectors.toList());

				list.forEach(m -> {
					if (event.context.gc.logLeavingChannel.isSet()) {
						MemberHandler.lockdownKicks.add(m.getId());
					}

					if (!event.context.gc.lockdownModeText.isEmpty()) {
						DM.send(event.context.handler, m, event.context.gc.lockdownModeText.get(), false);
					}

					m.kick("Lockdown Mode").subscribe();
				});
			}

			event.embedResponse(spec -> {
				spec.color(EmbedColors.RED);
				spec.title("Lockdown mode enabled!");
				spec.description(Emojis.ALERT.asFormat());
				spec.thumbnail(Assets.EMERGENCY.getPath());
			});
		} else {
			event.respond("Lockdown mode is already enabled!");
		}
	}

	private static void disable(ApplicationCommandEventWrapper event) throws DiscordCommandException {
		event.context.checkSenderAdmin();

		boolean wasOn = event.context.gc.lockdownMode.get();

		if (wasOn) {
			if (event.context.channelInfo == null || !event.context.gc.adminLogChannel.is(event.context.channelInfo.id)) {
				event.acknowledgeEphemeral();
			} else {
				event.acknowledge();
			}

			event.context.gc.lockdownMode.set(false);
			event.context.gc.lockdownMode.save();

			event.context.gc.auditLog(GnomeAuditLogEntry.builder(GnomeAuditLogEntry.Type.LOCKDOWN_DISABLED)
					.source(event.context.sender)
			);

			if (event.context.channelInfo == null || !event.context.gc.adminLogChannel.is(event.context.channelInfo.id)) {
				event.context.gc.adminLogChannelEmbed(spec -> {
					spec.title("Lockdown mode disabled!");
					spec.color(EmbedColors.GREEN);
					spec.description(Emojis.ALERT.asFormat());
					spec.thumbnail(Assets.EMERGENCY.getPath());
					spec.author(event.context.sender.getUsername(), null, event.context.sender.getAvatarUrl());
				});
			}

			event.embedResponse(spec -> {
				spec.color(EmbedColors.GREEN);
				spec.title("Lockdown mode disabled!");
				spec.description(Emojis.ALERT.asFormat());
				spec.thumbnail(Assets.EMERGENCY.getPath());
			});
		} else {
			event.respond("Lockdown mode is already disabled!");
		}
	}
}