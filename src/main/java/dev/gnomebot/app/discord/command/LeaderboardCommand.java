package dev.gnomebot.app.discord.command;

import dev.gnomebot.app.data.ChannelInfo;
import dev.gnomebot.app.discord.CachedRole;
import dev.gnomebot.app.util.URLRequest;
import dev.gnomebot.app.util.Utils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

/**
 * @author LatvianModder
 */
public class LeaderboardCommand extends ApplicationCommands {
	public static class LeaderboardCommandEntry {
		public String name;
		public String id;
		public int rank;
		public String xp;
		public int color;
	}

	@RootCommand
	public static final CommandBuilder COMMAND = root("leaderboard")
			.description("Leaderboard")
			.add(time("timespan", true))
			.add(integer("limit"))
			.add(channel("channel"))
			.add(role("role"))
			.run(LeaderboardCommand::run);

	private static void run(ApplicationCommandEventWrapper event) throws Exception {
		event.acknowledge();

		long limit = Math.max(1L, Math.min(event.get("limit").asLong(20L), 10000L));

		if (limit > 100L) {
			event.context.checkSenderAdmin();
		}

		long days = event.get("timespan").asDays().orElse(90L);
		ChannelInfo channelInfo = event.get("channel").asChannelInfo().orElse(null);
		CachedRole role = event.get("role").asRole().orElse(null);

		String url = "api/guild/activity/leaderboard-image/" + event.context.gc.guildId.asString() + "/" + days + "?limit=" + limit;

		if (channelInfo != null) {
			url += "&channel=" + channelInfo.id.asString();
		}

		if (role != null) {
			url += "&role=" + role.id.asString();
		}

		URLRequest<BufferedImage> req = Utils.internalRequest(url).toImage();

		try {
			ByteArrayOutputStream imageData = new ByteArrayOutputStream();
			ImageIO.write(req.block(), "PNG", imageData);
			event.respondFile(builder -> {
			}, "leaderboard-" + event.context.gc.guildId.asString() + "-" + days, "png", imageData.toByteArray(), false);
		} catch (Exception ex) {
			ex.printStackTrace();
			event.respond(req.getFullUrl());
		}
	}
}