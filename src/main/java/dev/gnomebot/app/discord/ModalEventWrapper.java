package dev.gnomebot.app.discord;

import dev.gnomebot.app.App;
import dev.gnomebot.app.data.GuildCollections;
import dev.gnomebot.app.discord.command.CommandOption;
import dev.gnomebot.app.discord.legacycommand.CommandContext;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionResolved;
import discord4j.core.object.component.FileUpload;
import discord4j.core.object.component.LayoutComponent;
import discord4j.core.object.component.MessageComponent;
import discord4j.core.object.component.SelectMenu;
import discord4j.core.object.component.TextInput;
import discord4j.core.object.entity.Attachment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ModalEventWrapper extends ComponentEventWrapper {
	public final Map<String, List<CommandOption>> options;
	public final Map<Snowflake, Attachment> attachmentMap;
	public final Map<String, List<Attachment>> attachments;

	public ModalEventWrapper(App app, GuildCollections gc, ModalSubmitInteractionEvent e, String id) {
		super(app, gc, e, id);
		this.options = new HashMap<>();
		this.attachmentMap = e.getResolved().map(ApplicationCommandInteractionResolved::getAttachments).orElse(Map.of());
		this.attachments = new HashMap<>();
		mapOptions(context, attachmentMap, e.getComponents(), options, attachments);
	}

	private static void mapOptions(CommandContext context, Map<Snowflake, Attachment> attachmentMap, List<MessageComponent> components, Map<String, List<CommandOption>> options, Map<String, List<Attachment>> attachments) {
		for (var component : components) {
			if (component instanceof LayoutComponent layout) {
				mapOptions(context, attachmentMap, layout.getChildren(), options, attachments);
			} else if (component instanceof TextInput textInput) {
				var o1 = new CommandOption(context, textInput.getCustomId(), textInput.getValue().orElse(""), false);
				options.put(o1.name, List.of(o1));
			} else if (component instanceof SelectMenu selectMenu) {
				options.put(selectMenu.getCustomId(), selectMenu.getValues().orElse(List.of()).stream()
					.map(value -> new CommandOption(context, selectMenu.getCustomId(), value, false))
					.toList()
				);
			} else if (component instanceof FileUpload fileUpload) {
				var attachmentList = new ArrayList<Attachment>();

				if (fileUpload.getValues().isPresent()) {
					for (var id : fileUpload.getValues().get()) {
						var attachment = attachmentMap.get(id);

						if (attachment != null) {
							attachmentList.add(attachment);
						}
					}
				}

				attachments.put(fileUpload.getCustomId(), attachmentList);
			}
		}
	}

	@Override
	public String toString() {
		return super.toString() + " {" + options.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue().stream().map(CommandOption::asString).toList()).collect(Collectors.joining(", ")) + "}";
	}

	public boolean has(String id) {
		return options.containsKey(id);
	}

	public List<CommandOption> getAll(String id) {
		return options.getOrDefault(id, List.of());
	}

	public CommandOption get(String id) {
		var list = getAll(id);

		if (list.isEmpty()) {
			return new CommandOption(context, id, "", false);
		}

		return list.getFirst();
	}

	public <T> List<T> getAllAs(String id, Function<CommandOption, Optional<T>> mapper) {
		return getAll(id).stream().map(mapper).filter(Optional::isPresent).map(Optional::get).toList();
	}

	public List<Attachment> getAttachments(String id) {
		return attachments.getOrDefault(id, List.of());
	}
}
