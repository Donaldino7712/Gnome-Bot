package dev.gnomebot.app.discord.command;

import dev.gnomebot.app.AppPaths;
import dev.gnomebot.app.data.Currency;
import dev.gnomebot.app.discord.DiscordHandler;
import dev.gnomebot.app.discord.command.admin.GnomeAdminCommand;
import dev.gnomebot.app.discord.legacycommand.GnomeException;
import dev.gnomebot.app.util.UUIDWrapper;
import dev.latvian.apps.ansi.log.Log;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.discordjson.json.ApplicationCommandData;
import discord4j.discordjson.json.ApplicationCommandRequest;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class ApplicationCommands {
	public static final UUID INVALID_APPLICATION_COMMAND = new UUID(0L, 0L);

	public record ApplicationCommandKey(InteractionType<?> type, String name) {
		public ApplicationCommandKey(ApplicationCommandData data) {
			this(Objects.requireNonNull(InteractionType.TYPES_BY_ID.get(data.type().toOptional().orElse(-1))), data.name());
		}

		@Override
		public String toString() {
			return type + "/" + name;
		}

		public static boolean validData(ApplicationCommandData applicationCommandData) {
			var type = applicationCommandData.type().toOptional();
			return type.isPresent() && InteractionType.TYPES_BY_ID.containsKey(type.get());
		}
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void add(ApplicationCommandInteractionBuilder c) {
		if (c.interactionType.builders.containsKey(c.name)) {
			throw new RuntimeException("Interaction already registered! " + c.name);
		}

		if (c.interactionType.builders.size() >= c.interactionType.limit) {
			throw new RuntimeException("Can't add any more interactions to this type! " + c.name);
		}

		c.commandHash = c.createHash();
		c.interactionType.builders.put(c.name, c);
	}

	public static void findCommands() {
		for (var type : InteractionType.TYPES.values()) {
			type.builders.clear();
		}

		add(QuoteCommands.MESSAGE_INTERACTION);
		add(ReportCommand.MESSAGE_INTERACTION);
		add(GnomeMessageInteraction.MESSAGE_INTERACTION);

		add(ReportCommand.USER_INTERACTION);
		add(GnomeMemberInteraction.USER_INTERACTION);

		add(GnomeCommand.COMMAND);
		add(GnomeAdminCommand.COMMAND);

		add(AboutCommand.COMMAND);
		add(AvatarCommand.COMMAND);
		add(BigEmojiCommand.COMMAND);
		add(ChannelCommands.COMMAND);
		add(CursePointCalculatorCommand.COMMAND);
		add(DecideCommand.COMMAND);
		add(DefineCommand.COMMAND);
		add(FeedbackCommand.COMMAND);
		add(PollCommand.COMMAND);
		add(LeaderboardCommand.COMMAND);
		add(MacroCommands.COMMAND);
		add(MathCommand.COMMAND);
		add(ModmailCommand.COMMAND);
		add(ModnamesCommand.COMMAND);
		add(ModpackCommand.COMMAND);
		add(PasteCommands.COMMAND);
		add(PingsCommands.COMMAND);
		add(RandomGibberishCommand.COMMAND);
		add(RankCommand.COMMAND);
		add(RemindMeCommand.COMMAND);
		add(ReportCommand.COMMAND);
		add(TimestampCommand.COMMAND);
		add(WebhookCommands.COMMAND);
		add(WhoisCommand.COMMAND);

		for (var type : InteractionType.TYPES.values()) {
			Log.info("Found " + type.builders.size() + " " + type.name.replace('_', ' ') + " commands");
		}
	}

	public static void updateCommands(DiscordHandler handler) throws Exception {
		boolean save;

		if (Files.exists(AppPaths.COMMANDS_FILE)) {
			save = updateChangedCommands(handler, Files.readAllLines(AppPaths.COMMANDS_FILE));
		} else {
			updateAllCommands(handler);
			save = true;
		}

		if (save) {
			List<String> lines1 = new ArrayList<>();
			lines1.add("# Do not modify or delete this file!");
			lines1.add("# This file is automatically generated by the bot and is used to keep track of registered commands.");

			for (var type : InteractionType.TYPES.values()) {
				lines1.add("");
				lines1.add("> " + type.name);

				for (var builder : type.builders.values().stream().sorted((o1, o2) -> o1.name.compareToIgnoreCase(o2.name)).toList()) {
					lines1.add(UUIDWrapper.toString(builder.commandHash) + ":" + builder.name);
				}
			}

			Files.write(AppPaths.COMMANDS_FILE, lines1);
		}
	}

	private static void updateAllCommands(DiscordHandler handler) {
		var list = new ArrayList<ApplicationCommandRequest>();

		for (var type : InteractionType.TYPES.values()) {
			for (var entry : type.builders.values()) {
				list.add(entry.createRootRequest());
			}
		}

		Log.warn("Bulk updating all commands...");

		handler.client.getRestClient()
				.getApplicationService()
				.bulkOverwriteGlobalApplicationCommand(handler.selfId, list)
				.blockFirst()
		;

		Log.success("Bulk updated " + list.size() + " application commands!");
	}

	private static boolean updateChangedCommands(DiscordHandler handler, List<String> lines) {
		var oldCommands = new HashMap<ApplicationCommandKey, UUID>();
		var newCommands = new HashMap<ApplicationCommandKey, ApplicationCommandInteractionBuilder<?, ?, ?>>();

		for (var type : InteractionType.TYPES.values()) {
			for (var builder : type.builders.values()) {
				newCommands.put(new ApplicationCommandKey(type, builder.name), builder);
			}
		}

		InteractionType<?> currentType = null;

		for (var line : lines) {
			if (line.startsWith("> ")) {
				currentType = InteractionType.TYPES.get(line.substring(2));
			} else if (!line.isEmpty() && currentType != null) {
				var s = line.split(":", 2);

				if (s.length == 2) {
					var id = UUIDWrapper.fromString(s[0]);

					if (id != null && !s[1].isBlank()) {
						oldCommands.put(new ApplicationCommandKey(currentType, s[1]), id);
					}
				}
			}
		}

		var globalCommands = handler.client.getRestClient()
				.getApplicationService()
				.getGlobalApplicationCommands(handler.selfId)
				.toStream()
				.filter(ApplicationCommandKey::validData)
				.collect(Collectors.toMap(ApplicationCommandKey::new, data -> data.id().asLong()));

		var changed = 0;

		for (var entry : oldCommands.entrySet()) {
			if (!newCommands.containsKey(entry.getKey())) {
				var id = globalCommands.get(entry.getKey());

				if (id != null) {
					Log.warn("Deleting " + entry.getKey() + "/" + id);

					handler.client.getRestClient()
							.getApplicationService()
							.deleteGlobalApplicationCommand(handler.selfId, id)
							.block()
					;
				} else {
					Log.warn("Deleting " + entry.getKey() + "/unknown");
				}

				changed++;
			}
		}

		for (var entry : newCommands.entrySet()) {
			if (!entry.getValue().commandHash.equals(oldCommands.get(entry.getKey()))) {
				var id = globalCommands.get(entry.getKey());

				if (id != null) {
					Log.warn("Updating " + entry.getKey() + "/" + id);
				} else {
					Log.warn("Creating " + entry.getKey());
				}

				handler.client.getRestClient()
						.getApplicationService()
						.createGlobalApplicationCommand(handler.selfId, entry.getValue().createRootRequest())
						.block()
				;

				changed++;
			}
		}

		if (changed > 0) {
			Log.success("Updated " + changed + " application commands!");
		} else {
			Log.info("There were no application command updates");
		}

		return changed > 0;
	}

	public static GnomeException error(String message) {
		return new GnomeException(message);
	}

	public static GnomeException wip() {
		return error("WIP!");
	}

	public static ChatInputInteractionBuilder chatInputInteraction(String name) {
		return new ChatInputInteractionBuilder(ApplicationCommandOption.Type.UNKNOWN, name);
	}

	public static UserInteractionBuilder userInteraction(String name) {
		return new UserInteractionBuilder(ApplicationCommandOption.Type.UNKNOWN, name);
	}

	public static MessageInteractionBuilder messageInteraction(String name) {
		return new MessageInteractionBuilder(ApplicationCommandOption.Type.UNKNOWN, name);
	}

	public static ChatInputInteractionBuilder sub(String name) {
		return new ChatInputInteractionBuilder(ApplicationCommandOption.Type.SUB_COMMAND, name);
	}

	public static ChatInputInteractionBuilder subGroup(String name) {
		return new ChatInputInteractionBuilder(ApplicationCommandOption.Type.SUB_COMMAND_GROUP, name);
	}

	public static ChatInputInteractionBuilder basic(ApplicationCommandOption.Type type, String name) {
		return new ChatInputInteractionBuilder(type, name);
	}

	public static ChatInputInteractionBuilder string(String name) {
		return basic(ApplicationCommandOption.Type.STRING, name);
	}

	public static ChatInputInteractionBuilder integer(String name) {
		return basic(ApplicationCommandOption.Type.INTEGER, name);
	}

	public static ChatInputInteractionBuilder bool(String id) {
		return basic(ApplicationCommandOption.Type.BOOLEAN, id);
	}

	public static ChatInputInteractionBuilder user(String id) {
		return basic(ApplicationCommandOption.Type.STRING, id).suggest(event -> event.context.gc.usernameSuggestions(event));
	}

	public static ChatInputInteractionBuilder realUser(String id) {
		return basic(ApplicationCommandOption.Type.USER, id);
	}

	public static ChatInputInteractionBuilder channel(String id) {
		return basic(ApplicationCommandOption.Type.CHANNEL, id);
	}

	public static ChatInputInteractionBuilder role(String id) {
		return basic(ApplicationCommandOption.Type.ROLE, id);
	}

	public static ChatInputInteractionBuilder mentionable(String id) {
		return basic(ApplicationCommandOption.Type.MENTIONABLE, id);
	}

	public static ChatInputInteractionBuilder time(String id, boolean all, boolean shortTime) {
		return basic(ApplicationCommandOption.Type.STRING, id).suggest(event -> {
			if (all) {
				event.suggest("all");
			}

			if (shortTime) {
				event.suggest("30 seconds");
				event.suggest("1 minute");
				event.suggest("10 minutes");
				event.suggest("30 minutes");
				event.suggest("1 hour");
				event.suggest("3 hours");
				event.suggest("6 hours");
			}

			event.suggest("1 day");
			event.suggest("1 week");
			event.suggest("1 month");
			event.suggest("3 months");
			event.suggest("1 year");
		});
	}

	public static ChatInputInteractionBuilder number(String id) {
		return basic(ApplicationCommandOption.Type.NUMBER, id);
	}

	public static ChatInputInteractionBuilder zone(String id) {
		return string(id);
	}

	public static ChatInputInteractionBuilder currency(String id) {
		return string(id).suggest(event -> {
			for (var currency : Currency.ALL.getNonnull().values()) {
				event.suggest(currency.name, currency.id);
			}
		});
	}
}
