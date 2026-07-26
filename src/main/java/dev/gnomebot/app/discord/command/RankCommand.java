package dev.gnomebot.app.discord.command;

import dev.gnomebot.app.discord.MessageHandler;
import dev.gnomebot.app.server.handler.ActivityHandlers;
import dev.gnomebot.app.util.EmbedBuilder;
import dev.latvian.apps.webutils.FormattingUtils;

public class RankCommand extends ApplicationCommands {
	public static final ChatInputInteractionBuilder COMMAND = chatInputInteraction("rank")
		.description("Rank")
		.add(time("timespan", true, false))
		.add(user("member"))
		.add(channel("channel"))
		.run(RankCommand::run);

	private static void run(ChatInputInteractionEventWrapper event) {
		event.acknowledge();

		// event.respond("Currently this command is out of order! Sorry for inconvenience!");

		var m = event.get("member").asMember().orElse(event.context.sender);
		var days = event.get("timespan").asDays().orElse(90L);
		var channel = event.get("channel").asChannelInfo().orElse(null);

		event.context.handler.app.queueBlockingTask(_ -> {
			try {
				var memberMap = event.context.gc.getMembers();
				var leaderboardData = ActivityHandlers.data(event.context.gc, days * ActivityHandlers.MS_IN_DAY, channel == null ? 0L : channel.id);
				var id = m.getId().asLong();
				int rank = 1;

				for (var entry : leaderboardData) {
					try {
						var member = memberMap.get(entry.id);

						if (member == null || member.isBot()) {
							continue;
						}
					} catch (Exception ex) {
						continue;
					}

					if (entry.id == id) {
						// event.response().createFollowupMessage("**Rank:**  #0   |   **XP:**  0").subscribe();

						var embed = EmbedBuilder.create();
						embed.author(m.getDisplayName(), m.getAvatarUrl());

						if (rank == 69) {
							embed.inlineField("Rank", "#69, nice");
						} else {
							embed.inlineField("Rank", "#" + rank);
						}

						embed.inlineField("XP", FormattingUtils.format(entry.value));

						if (event.context.gc.isMM() && event.context.gc.regularMessages.get() > 0 && !event.context.gc.regularRole.is(m)) {
							var totalMessages = event.context.gc.members.findFirst(m).totalMessages();

							if (totalMessages < event.context.gc.regularMessages.get()) {
								if (totalMessages < MessageHandler.MM_MEMBER) {
									embed.inlineField("Member Rank", ((long) (totalMessages * 10000D / (double) MessageHandler.MM_MEMBER) / 100D) + "%");
								} else {
									embed.inlineField("Regular Rank", ((long) (totalMessages * 10000D / (double) event.context.gc.regularMessages.get()) / 100D) + "%");
								}
							}
						}

						event.respond(embed);
						return;
					}

					rank++;
				}

				event.respond(EmbedBuilder.create()
					.author(m.getDisplayName(), m.getAvatarUrl())
					.inlineField("Rank", "Unranked")
					.inlineField("XP", "0")
				);
			} catch (Exception ex) {
				ex.printStackTrace();
				event.respond("Failed to connect to API!");
			}
		});
	}
}
