package dev.gnomebot.app.discord.command;

import dev.gnomebot.app.App;
import dev.gnomebot.app.discord.ModalEventWrapper;
import dev.gnomebot.app.util.MessageBuilder;
import dev.latvian.apps.ansi.log.Log;
import dev.latvian.apps.webutils.FormattingUtils;
import discord4j.core.object.component.FileUpload;
import discord4j.core.object.component.Label;
import discord4j.core.object.component.Section;
import discord4j.core.object.component.SelectMenu;
import discord4j.core.object.component.Separator;
import discord4j.core.object.component.TextDisplay;
import discord4j.core.object.component.TextInput;
import discord4j.core.object.component.Thumbnail;
import discord4j.core.object.component.TopLevelMessageComponent;
import discord4j.core.object.component.UnfurledMediaItem;
import discord4j.core.spec.MessageCreateFields;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class ModmailCommand extends ApplicationCommands {
	public static final ChatInputInteractionBuilder COMMAND = chatInputInteraction("modmail")
		.description("Open a form that will send a message to server owners in a private channel")
		.run(ModmailCommand::run);

	private static void run(ChatInputInteractionEventWrapper event) {
		if (event.context.gc.adminMessagesChannel.isSet()) {
			event.respondModal("modmail", "Send a message to server owners", List.of(
				Label.of("Users", "If relevant, type usernames of people you are reporting.", SelectMenu.ofUser("users").required(false).withMinValues(0).withMaxValues(25)),
				Label.of("Report", "Write your report here!", TextInput.paragraph("report").required()),
				Label.of("Files", "If relevant, you can include screenshots, etc.\nMax individual file size is 10 MB.", FileUpload.of("files").required(false).withMinValues(0).withMaxValues(10))
			));
		} else {
			event.respond("Modmail channel not set! You'll have to DM someone.");
		}
	}

	public static void modmailCallback(ModalEventWrapper event) {
		var users = event.getAllAs("users", CommandOption::asUser);
		var report = event.get("report").asString();
		var files = event.getAttachments("files");

		for (var file : files) {
			if (file.getSize() <= 0 || file.getSize() > 10 * 1024 * 1024) {
				event.respond("File '" + file.getFilename() + "' is too large! Max file size is 10 MB.");
				return;
			}
		}

		event.respond("Message sent!");

		event.context.gc.adminMessagesChannel.messageChannel().ifPresent(ch -> {
			var builder = MessageBuilder.create();

			builder.allowUserMentions(event.context.sender.getId().asLong());

			var componentList = new ArrayList<TopLevelMessageComponent>();
			var now = System.currentTimeMillis() / 1000L;

			componentList.add(Section.of(
				Thumbnail.of(UnfurledMediaItem.of(event.context.sender.getAvatarUrl())),
				List.of(TextDisplay.of("## Mod Mail from " + event.context.sender.getMention() + "\n<t:" + now + ">\n<t:" + now + ":R>"))
			));

			componentList.add(Separator.of(true));

			if (!users.isEmpty()) {
				var str = new StringBuilder("Reported Users:");

				for (var user : users) {
					str.append("\n- ");
					str.append(user.getMention());
				}

				componentList.add(TextDisplay.of(str.toString()));
				componentList.add(Separator.of(true));
			}

			componentList.add(TextDisplay.of(report));
			componentList.add(Separator.of(true));

			if (!files.isEmpty()) {
				componentList.add(TextDisplay.of("Attachments: " + files.size()));
				componentList.add(Separator.of(true));
			}

			builder.components(componentList);

			ch.createMessage(builder).subscribe();

			if (!files.isEmpty()) {
				Thread.startVirtualThread(() -> {
					var filesMsg = MessageBuilder.create();

					for (var file : files) {
						try {
							var bytes = App.HTTP_CLIENT.send(HttpRequest.newBuilder().uri(URI.create(file.getUrl())).GET().build(), HttpResponse.BodyHandlers.ofByteArray()).body();
							Log.info("Added file " + file.getFilename() + ", " + FormattingUtils.siByteSize(bytes.length));

							if (bytes.length > 0 && bytes.length < 10 * 1024 * 1024) {
								filesMsg.addFile(MessageCreateFields.File.of(file.getFilename(), FormattingUtils.siByteSize(bytes.length), new ByteArrayInputStream(bytes)));
							}
						} catch (Exception ex) {
							ex.printStackTrace();
						}
					}

					ch.createMessage(filesMsg).subscribe();
				});
			}
		});
	}
}
