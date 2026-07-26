package dev.gnomebot.app.discord.command;

import dev.gnomebot.app.server.handler.ActivityHandlers;
import dev.gnomebot.app.util.MessageBuilder;
import dev.gnomebot.app.util.URLRequest;
import dev.latvian.apps.tinyhttp.http.response.HTTPStatus;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;

public class LeaderboardCommand extends ApplicationCommands {
	public static final ChatInputInteractionBuilder COMMAND = chatInputInteraction("leaderboard")
		.description("Leaderboard")
		.add(time("timespan", true, false))
		.add(integer("limit"))
		.add(channel("channel"))
		.add(role("role"))
		.run(LeaderboardCommand::run);

	private static void run(ChatInputInteractionEventWrapper event) throws Exception {
		event.acknowledge();

		int limit = Math.clamp(event.get("limit").asInt(20), 1, 10000);

		if (limit > 100) {
			event.context.checkSenderAdmin();
		}

		var days = event.get("timespan").asDays().orElse(90L);
		var time = days * ActivityHandlers.MS_IN_DAY;
		var channelInfo = event.get("channel").asChannelInfo().orElse(null);
		var role = event.get("role").asRole().orElse(null);

		var image = ActivityHandlers.image(event.context.gc, time, channelInfo == null ? 0L : channelInfo.id, role == null ? 0L : role.id, limit);

		try {
			var imageData = new ByteArrayOutputStream();
			ImageIO.write(image, "PNG", imageData);
			event.respond(MessageBuilder.create().addFile("leaderboard.png", imageData.toByteArray()));
		} catch (URLRequest.UnsuccesfulRequestException ex) {
			if (ex.status == HTTPStatus.BAD_REQUEST) {
				event.respond("This leaderboard has no data!");
			}
		} catch (Exception ex) {
			ex.printStackTrace();
			event.respond("Error");
		}
	}
}
