package dev.gnomebot.app.discord;

import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Updates;
import dev.gnomebot.app.App;
import dev.gnomebot.app.data.ChannelInfo;
import dev.gnomebot.app.data.DiscordFeedback;
import dev.gnomebot.app.data.DiscordPoll;
import dev.gnomebot.app.data.GuildCollections;
import dev.gnomebot.app.data.Macro;
import dev.gnomebot.app.data.Vote;
import dev.gnomebot.app.discord.command.ApplicationCommandEventWrapper;
import dev.gnomebot.app.discord.command.ApplicationCommands;
import dev.gnomebot.app.discord.command.ChatCommandSuggestion;
import dev.gnomebot.app.discord.command.ChatCommandSuggestionEvent;
import dev.gnomebot.app.discord.command.CommandBuilder;
import dev.gnomebot.app.discord.command.ModpackCommand;
import dev.gnomebot.app.discord.legacycommand.DiscordCommandException;
import dev.gnomebot.app.script.event.ButtonEventJS;
import dev.gnomebot.app.util.EmbedBuilder;
import dev.gnomebot.app.util.MessageBuilder;
import dev.gnomebot.app.util.OngoingAction;
import dev.gnomebot.app.util.ThreadMessageRequest;
import dev.gnomebot.app.util.Utils;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.component.TextInput;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.BanQuerySpec;
import discord4j.core.spec.EmbedCreateFields;
import discord4j.core.spec.InteractionPresentModalSpec;
import discord4j.core.spec.MessageEditSpec;
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData;
import discord4j.rest.util.Permission;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class InteractionHandler {
	public static void applicationCommand(DiscordHandler handler, ApplicationCommandInteractionEvent event) {
		GuildCollections gc = event.getInteraction().getGuildId().map(handler.app.db::guild).orElse(null);

		if (gc == null) {
			event.reply("DM interactions aren't supported!").withEphemeral(true).subscribe();
			return;
		}

		CommandBuilder command = ApplicationCommands.COMMANDS.get(event.getCommandName());
		List<ApplicationCommandInteractionOption> options = event instanceof ChatInputInteractionEvent ? ((ChatInputInteractionEvent) event).getOptions() : new ArrayList<>();

		while (command != null && options.size() == 1 && options.get(0).getValue().isEmpty()) {
			command = command.getSub(options.get(0).getName());
			options = options.get(0).getOptions();
		}

		try {
			if (command != null) {
				ApplicationCommandEventWrapper w = new ApplicationCommandEventWrapper(gc, event, options);

				try {
					command.callback.run(w);
				} catch (DiscordCommandException ex) {
					w.acknowledgeEphemeral();
					w.respond(ex.getMessage());
				} catch (Exception ex) {
					w.acknowledgeEphemeral();
					w.respond(ex.toString());
					ex.printStackTrace();
				}
			} else {
				Macro macro = gc.getMacro(event.getCommandName());

				if (macro != null) {
					event.reply(macro.createMessage(false).ephemeral(false).toInteractionApplicationCommandCallbackSpec()).subscribe();
					macro.update(Updates.inc("uses", 1));
				} else {
					App.error("Weird interaction data from " + event.getInteraction().getUser().getUsername() + ": " + event.getInteraction().getData());
					event.reply("Command not found!").withEphemeral(true).subscribe();
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public static void button(DiscordHandler handler, ButtonInteractionEvent event) {
		GuildCollections gc = event.getInteraction().getGuildId().map(handler.app.db::guild).orElse(null);

		if (gc != null) {
			Member member = event.getInteraction().getMember().orElse(null);

			if (member != null) {
				String customId = event.getCustomId();

				if (gc.discordJS.onButton.hasListeners() && gc.discordJS.onButton.post(new ButtonEventJS(customId, gc.getWrappedGuild().getUser(member.getId().asString())), true)) {
					event.deferEdit().subscribe();
					return;
				}

				ComponentEventWrapper eventWrapper = new ComponentEventWrapper(gc, event, customId);

				try {
					try {
						button(eventWrapper);
					} catch (DiscordCommandException ex) {
						App.error("Error in " + eventWrapper + ": " + ex.getMessage());
						eventWrapper.respond(ex.getMessage());
					} catch (Exception ex) {
						App.error("Error in " + eventWrapper + ": " + ex);
						eventWrapper.respond("Error: " + ex);
					}
				} catch (Exception ex) {
				}
			}
		}
	}

	public static void selectMenu(DiscordHandler handler, SelectMenuInteractionEvent event) {
		GuildCollections gc = event.getInteraction().getGuildId().map(handler.app.db::guild).orElse(null);

		if (gc != null) {
			Member member = event.getInteraction().getMember().orElse(null);

			if (member != null) {
				String customId = event.getCustomId();

				ComponentEventWrapper eventWrapper = new ComponentEventWrapper(gc, event, customId);

				try {
					try {
						selectMenu(eventWrapper, event.getValues());
					} catch (DiscordCommandException ex) {
						eventWrapper.respond(ex.getMessage());
					} catch (Exception ex) {
						eventWrapper.respond("Error: " + ex);
					}
				} catch (Exception ex) {
				}
			}
		}
	}

	public static void modalSubmitInteraction(DiscordHandler handler, ModalSubmitInteractionEvent event) {
		GuildCollections gc = event.getInteraction().getGuildId().map(handler.app.db::guild).orElse(null);

		if (gc != null) {
			Member member = event.getInteraction().getMember().orElse(null);

			if (member != null) {
				String customId = event.getCustomId();

				if (gc.discordJS.onButton.hasListeners() && gc.discordJS.onButton.post(new ButtonEventJS(customId, gc.getWrappedGuild().getUser(member.getId().asString())), true)) {
					event.deferEdit().subscribe();
					return;
				}

				ModalEventWrapper eventWrapper = new ModalEventWrapper(gc, event, customId);

				try {
					try {
						modalSubmit(eventWrapper);
					} catch (DiscordCommandException ex) {
						App.error("Error in " + eventWrapper + ": " + ex.getMessage());
						eventWrapper.respond(ex.getMessage());
					} catch (Exception ex) {
						App.error("Error in " + eventWrapper + ": " + ex);
						eventWrapper.respond("Error: " + ex);
					}
				} catch (Exception ex) {
				}
			}
		}
	}

	public static void chatInputAutoComplete(DiscordHandler handler, ChatInputAutoCompleteEvent event) {
		GuildCollections gc = event.getInteraction().getGuildId().map(handler.app.db::guild).orElse(null);

		if (gc == null) {
			return;
		}

		CommandBuilder command = ApplicationCommands.COMMANDS.get(event.getCommandName());
		List<ApplicationCommandInteractionOption> options = event.getOptions();

		while (command != null && options.size() == 1 && options.get(0).getValue().isEmpty()) {
			command = command.getSub(options.get(0).getName());
			options = options.get(0).getOptions();
		}

		try {
			if (command != null) {
				ChatCommandSuggestionEvent eventWrapper = new ChatCommandSuggestionEvent(gc, event, options);

				if (eventWrapper.focused != null) {
					// App.info(eventWrapper.focused.name + " " + command + " " + optionsToString(new StringBuilder(), options));
					CommandBuilder sub = command.getSub(eventWrapper.focused.name);

					if (sub != null && sub.suggestions != null) {
						sub.suggestions.getSuggestions(eventWrapper);

						if (eventWrapper.suggestions.isEmpty()) {
							event.respondWithSuggestions(Collections.emptyList()).subscribe();
						} else {
							eventWrapper.suggestions.sort(ChatCommandSuggestion::compareTo);
							String search = eventWrapper.transformSearch.apply(eventWrapper.focused.asString());

							List<ApplicationCommandOptionChoiceData> list = new ArrayList<>();

							for (ChatCommandSuggestion data : eventWrapper.suggestions) {
								if (list.size() == 25) {
									break;
								} else if (search.isEmpty() || data.match().startsWith(search)) {
									list.add(data.build());
								}
							}

							for (ChatCommandSuggestion data : eventWrapper.suggestions) {
								if (list.size() == 25) {
									break;
								} else if (!search.isEmpty() && !data.match().startsWith(search) && data.match().contains(search)) {
									list.add(data.build());
								}
							}

							event.respondWithSuggestions(list).subscribe();
						}
					}
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private static void button(ComponentEventWrapper event) throws DiscordCommandException {
		switch (event.path[0]) {
			case "none" -> event.acknowledge();
			case "unmute" -> unmute(event, Snowflake.of(event.path[1]));
			case "macro" -> macro(event, event.path[1]);
			case "feedback" -> feedback(event, Integer.parseInt(event.path[1]), event.path[2].equals("upvote") ? Vote.UP : event.path[2].equals("downvote") ? Vote.DOWN : Vote.NONE);
			case "warn" -> warn(event, Snowflake.of(event.path[1]), event.path[2], Confirm.of(event.path, 3));
			case "kick" -> kick(event, Snowflake.of(event.path[1]), event.path[2], Confirm.of(event.path, 3));
			case "ban" -> ban(event, Snowflake.of(event.path[1]), event.path[2], Confirm.of(event.path, 3));
			case "refresh_modpack" -> refreshModpack(event);
			case "stop" -> stopOngoingAction(event, event.path[1]);
			case "modal_test" -> modalTest(event);
			default -> {
				App.info(event.context.sender.getTag() + " clicked " + event.context.gc + "/" + Arrays.asList(event.path));
				throw new DiscordCommandException("Unknown button ID: " + Arrays.asList(event.path));
			}
		}
	}

	private static void selectMenu(ComponentEventWrapper event, List<String> values) throws DiscordCommandException {
		switch (event.path[0]) {
			case "none" -> event.acknowledge();
			case "poll" -> poll(event, Integer.parseInt(event.path[1]), values.get(0));
			case "report" -> ReportHandler.report(event, Snowflake.of(event.path[1]), Snowflake.of(event.path[2]), values.get(0));
			default -> {
				App.info(event.context.sender.getTag() + " selected " + event.context.gc + "/" + Arrays.asList(event.path) + "/" + values);
				throw new DiscordCommandException("Unknown select menu ID: " + Arrays.asList(event.path) + "/" + values);
			}
		}
	}

	private static void modalSubmit(ModalEventWrapper event) throws DiscordCommandException {
		switch (event.path[0]) {
			case "none" -> event.acknowledge();
			case "modal_test" -> event.respond("Modal: " + event);
			case "modmail" -> modmail(event);
			case "report" -> report(event, Snowflake.of(event.path[1]), Snowflake.of(event.path[2]));
			case "feedback" -> feedback(event);
			case "add_macro" -> addMacro(event, event.path[1]);
			case "edit_macro" -> editMacro(event, event.path[1]);
			default -> {
				App.warn(event.context.sender.getTag() + " submitted unknown modal " + event.context.gc + "/" + event);
				throw new DiscordCommandException("Unknown modal ID: " + event);
			}
		}
	}

	// Actions //
	private static void feedback(ComponentEventWrapper event, int number, Vote vote) throws DiscordCommandException {
		DiscordFeedback feedback = event.context.gc.feedback.query().eq("number", number).first();

		if (feedback == null) {
			event.acknowledge();
			return;
		}

		Message m = event.context.channelInfo.getMessage(Snowflake.of(feedback.getUID()));

		if (!feedback.getStatus().canEdit()) {
			throw new DiscordCommandException("You can't vote for this suggestion, it's already decided on!");
		}

		if (event.context.gc.feedbackVoteRole.is(event.context.sender)) {
			event.acknowledge();

			if (feedback.setVote(event.context.sender.getId().asString(), vote)) {
				EmbedCreateFields.Footer footer = Utils.getFooter(m);
				m.edit(MessageEditSpec.builder().addEmbed(feedback.edit(event.context.gc, footer)).build()).subscribe();
			}
		} else {
			throw new DiscordCommandException("You can't vote for this suggestion, you have to have " + event.context.gc.regularRole + " role!");
		}
	}

	private static void warn(ComponentEventWrapper event, Snowflake other, String reason, Confirm confirm) throws DiscordCommandException {
		event.context.checkSenderAdmin();
		//other.kick(reason).subscribe();
		Utils.editComponents(event.event.getMessage().orElse(null), Collections.singletonList(ActionRow.of(Button.danger("none", Emojis.WARNING, "Warned by " + event.context.sender.getUsername() + "!")).getData()));
		event.respond("Warned <@" + other.asString() + ">");
	}

	private static void kick(ComponentEventWrapper event, Snowflake other, String reason, Confirm confirm) throws DiscordCommandException {
		event.context.checkSenderAdmin();
		event.context.gc.getGuild().kick(other, reason).subscribe();
		Utils.editComponents(event.event.getMessage().orElse(null), Collections.singletonList(ActionRow.of(Button.danger("none", Emojis.WARNING, "Kicked by " + event.context.sender.getUsername() + "!")).getData()));
		event.respond("Kicked <@" + other.asString() + ">");
	}

	private static void ban(ComponentEventWrapper event, Snowflake other, String reason, Confirm confirm) throws DiscordCommandException {
		event.context.checkSenderAdmin();
		event.context.gc.getGuild().ban(other, BanQuerySpec.builder().deleteMessageDays(1).reason(reason).build()).subscribe();
		Utils.editComponents(event.event.getMessage().orElse(null), Collections.singletonList(ActionRow.of(Button.danger("none", Emojis.WARNING, "Banned by " + event.context.sender.getUsername() + "!")).getData()));
		event.respond("Banned <@" + other.asString() + ">");
	}

	private static void unmute(ComponentEventWrapper event, Snowflake other) throws DiscordCommandException {
		event.context.checkSenderAdmin();
		event.context.gc.unmute(other, 0L);
		Utils.editComponents(event.event.getMessage().orElse(null), Collections.singletonList(ActionRow.of(Button.secondary("none", Emojis.CHECKMARK, "Unmuted by " + event.context.sender.getUsername() + "!")).getData()));
		event.respond("Unmuted <@" + other.asString() + ">");
	}

	private static void macro(ComponentEventWrapper event, String id) throws DiscordCommandException {
		Macro macro = event.context.gc.getMacro(id.toLowerCase());

		if (macro == null) {
			throw new DiscordCommandException("Macro '" + id + "' not found!");
		}

		event.respond(macro.createMessage(false).ephemeral(true));
	}

	private static void poll(ComponentEventWrapper event, int number, String value) {
		DiscordPoll poll = event.context.gc.polls.query().eq("number", number).first();

		if (poll == null) {
			event.acknowledge();
			return;
		}

		Message m = event.context.channelInfo.getMessage(Snowflake.of(poll.getUID()));

		if (value.equals("vote/none")) {
			event.acknowledge();

			if (poll.setVote(event.context.sender.getId().asString(), -1)) {
				EmbedCreateFields.Footer footer = Utils.getFooter(m);
				m.edit(MessageEditSpec.builder().addEmbed(poll.edit(event.context.gc, footer)).build()).subscribe();
			}
		} else if (value.startsWith("vote/")) {
			event.acknowledge();

			if (poll.setVote(event.context.sender.getId().asString(), Integer.parseInt(value.substring(5)))) {
				EmbedCreateFields.Footer footer = Utils.getFooter(m);
				m.edit(MessageEditSpec.builder().addEmbed(poll.edit(event.context.gc, footer)).build()).subscribe();
			}
		}
	}

	private static void refreshModpack(ComponentEventWrapper event) {
		if (event.context.message.getInteraction().isPresent() && event.context.sender.getId().equals(event.context.message.getInteraction().get().getUser().getId())) {
			MessageBuilder builder = MessageBuilder.create();

			ModpackCommand.Pack pack = ModpackCommand.getRandomPack();

			builder.addEmbed(EmbedBuilder.create()
					.color(EmbedColors.GRAY)
					.title("What pack should I play?")
					.description("[" + pack.name + "](" + pack.url + ")")
			);

			event.edit().respond(builder);
		} else {
			event.acknowledge();
		}
	}

	private static void stopOngoingAction(ComponentEventWrapper event, String id) {
		event.acknowledge();
		OngoingAction.stop(id);
	}

	private static void modalTest(ComponentEventWrapper event) {
		event.event.presentModal(InteractionPresentModalSpec.builder()
				.title("Modal Test")
				.customId("modal_test")
				.addComponent(ActionRow.of(TextInput.small("modal_test_1", "Test 1", "Placeholder text 1")))
				.addComponent(ActionRow.of(TextInput.paragraph("modal_test_2", "Test 2", "Placeholder text 2").required(false)))
				.build()
		).subscribe();
	}

	private static void modmail(ModalEventWrapper event) throws DiscordCommandException {
		event.respond("Message sent!");

		String message = event.get("message").asString();

		event.context.gc.adminMessagesChannel.messageChannel().flatMap(ChannelInfo::getOrCreateWebhook).ifPresent(w -> w.execute(MessageBuilder.create()
				.webhookName("Modmail from " + event.context.sender.getTag())
				.webhookAvatarUrl(event.context.sender.getAvatarUrl())
				.allowUserMentions(event.context.sender.getId())
				.content(event.context.sender.getMention() + ":\n" + message)
		));
	}

	private static void report(ModalEventWrapper event, Snowflake channel, Snowflake user) throws DiscordCommandException {
		if (true) {
			event.respond("Reporting isn't implemented yet! You'll have to ping admins");
			return;
		}

		CachedRole role = event.context.gc.reportMentionRole.getRole();

		if (role == null) {
			event.respond("Thank you for your report!");
		} else {
			event.respond("Thank you for your report! <@&" + role.id.asString() + "> have been notified.");
		}

		/*
		event.respond(msg -> {
			if (role == null) {
				msg.content("Select reason for reporting this message:");
			} else {
				msg.content("Select reason for reporting this message: (<@&" + role.id.asString() + "> will be pinged)");
			}

			List<SelectMenu.Option> options = new ArrayList<>();
			options.add(SelectMenu.Option.of("Cancel", "-"));

			for (String s : event.context.gc.reportOptions.get().split(" \\| ")) {
				options.add(SelectMenu.Option.of(s, s));
			}

			options.add(SelectMenu.Option.of("Other", "Other"));
			msg.addComponent(ActionRow.of(SelectMenu.of("report/" + m.getChannelId().asString() + "/" + m.getId().asString(), options).withPlaceholder("Select Reason...")).getData());
		});
		 */
	}

	private static void feedback(ModalEventWrapper event) throws DiscordCommandException {
		//event.respond("Feedback sent!");

		// event.acknowledgeEphemeral();
		ChannelInfo feedbackChannel = event.context.gc.feedbackChannel.messageChannel().orElse(null);

		if (feedbackChannel == null) {
			throw new DiscordCommandException("Feedback channel is not set up on this server!");
		}

		String suggestion = event.get("feedback").asString();

		int number = event.context.gc.feedbackNumber.get() + 1;
		event.context.gc.feedbackNumber.set(number);
		event.context.gc.feedbackNumber.save();

		event.context.referenceMessage = false;

		event.context.checkBotPerms(feedbackChannel, Permission.ADD_REACTIONS, Permission.SEND_MESSAGES);

		Message m = feedbackChannel.createMessage(EmbedBuilder.create()
				.url(App.url("feedback/" + event.context.gc.guildId.asString() + "/" + number))
				.title("Loading suggestion #" + number + "...")
		).block();

		Document document = new Document();
		document.put("_id", m.getId().asLong());
		document.put("author", event.context.sender.getId().asLong());
		document.put("timestamp", Date.from(m.getTimestamp()));
		document.put("number", number);
		document.put("content", suggestion);
		document.put("status", 0);
		BasicDBObject votes = new BasicDBObject();
		votes.put(event.context.sender.getId().asString(), true);
		document.put("votes", votes);
		event.context.gc.feedback.insert(document);
		m.edit(MessageEditSpec.builder().addEmbed(event.context.gc.feedback.findFirst(m).edit(event.context.gc, event.context.gc.anonymousFeedback.get() ? null : EmbedCreateFields.Footer.of(event.context.sender.getTag(), event.context.sender.getAvatarUrl()))).build()).block();

		try {
			Utils.THREAD_ROUTE.newRequest(m.getChannelId().asLong(), m.getId().asLong())
					.body(new ThreadMessageRequest("Discussion of " + number))
					.exchange(event.context.handler.client.getCoreResources().getRouter())
					.skipBody()
					.block();
		} catch (Exception ex) {
			App.error("Failed to create a thread for suggestion " + event.context.gc + "/#" + number);
		}

		m.edit(MessageEditSpec.builder().addComponent(ActionRow.of(
				Button.secondary("feedback/" + number + "/upvote", Emojis.VOTEUP),
				Button.secondary("feedback/" + number + "/mehvote", Emojis.VOTENONE),
				Button.secondary("feedback/" + number + "/downvote", Emojis.VOTEDOWN),
				Button.link(QuoteHandler.getChannelURL(event.context.gc.guildId, m.getId()), "Discussion")
		)).build()).block();

		event.respond(MessageBuilder.create("Your feedback has been submitted!").addComponentRow(Button.link(QuoteHandler.getMessageURL(event.context.gc.guildId, m.getChannelId(), m.getId()), "Open")));
	}

	private static void addMacro(ModalEventWrapper event, String name) {
		if (name.isEmpty()) {
			throw new DiscordCommandException("Macro name can't be empty!");
		} else if (name.length() > 50) {
			throw new DiscordCommandException("Macro name too long! Max 50 characters.");
		}

		if (event.context.gc.getMacro(name) != null) {
			throw new DiscordCommandException("Macro with that name already exists!");
		}

		String content = event.get("content").asString()
				.replaceAll("<@&(\\d+)>", "role:$1")
				.replaceAll("<@(\\d+)>", "user:$1")
				.replace("@here", "mention:here")
				.replace("@everyone", "mention:everyone");

		if (content.isEmpty()) {
			throw new DiscordCommandException("Can't have empty content!");
		}

		List<String> extra = new ArrayList<>(Arrays.stream(event.get("extra").asString().trim().split("\n")).map(String::trim).filter(s -> !s.isEmpty()).toList());

		Document document = new Document();
		document.put("name", name);
		document.put("content", content);

		extra.remove("clear");

		if (!extra.isEmpty()) {
			document.put("extra", extra);
		}

		document.put("author", event.context.sender.getId().asLong());
		document.put("created", new Date());
		document.put("uses", 0);
		document.put("type", "text");
		event.context.gc.macros.insert(document);
		event.context.gc.updateMacroMap();

		event.respond(MessageBuilder.create("Macro '" + name + "' created!").ephemeral(false));
	}

	private static void editMacro(ModalEventWrapper event, String name) {
		if (name.isEmpty()) {
			throw new DiscordCommandException("Macro name can't be empty!");
		}

		Macro macro = event.context.gc.getMacro(name);

		if (macro == null) {
			throw new DiscordCommandException("Macro not found!");
		}

		String rename = event.get("rename").asString(macro.getName());

		List<Bson> updates = new ArrayList<>();
		long slashId = 0L;

		if (!rename.equals(macro.getName())) {
			if (rename.length() > 50) {
				throw new DiscordCommandException("Macro name too long! Max 50 characters.");
			}

			if (event.context.gc.getMacro(rename) != null) {
				throw new DiscordCommandException("Macro with that name already exists!");
			}

			slashId = macro.setSlashCommand(false);
			updates.add(Updates.set("name", rename));
		}

		String content = event.get("content").asString(macro.getContent());

		if (!content.equals(macro.getContent())) {
			updates.add(Updates.set("content", content));
		}

		List<String> extra = Arrays.stream(event.get("extra").asString().trim().split("\n")).map(String::trim).filter(s -> !s.isEmpty()).toList();

		if (!extra.isEmpty()) {
			if (extra.contains("clear")) {
				updates.add(Updates.unset("extra"));
			} else {
				updates.add(Updates.set("extra", extra));
			}
		}

		macro.update(updates);

		if (slashId != 0L) {
			macro.setSlashCommand(true);
		}

		event.context.gc.updateMacroMap();
		event.respond(MessageBuilder.create("Macro '" + rename + "' updated!").ephemeral(false));
	}
}