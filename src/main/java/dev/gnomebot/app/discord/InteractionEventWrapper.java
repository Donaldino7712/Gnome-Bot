package dev.gnomebot.app.discord;

import dev.gnomebot.app.App;
import dev.gnomebot.app.data.GuildCollections;
import dev.gnomebot.app.discord.legacycommand.CommandContext;
import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent;
import discord4j.core.event.domain.interaction.InteractionCreateEvent;
import discord4j.rest.interaction.InteractionResponse;

import java.time.Instant;

public abstract class InteractionEventWrapper<T extends InteractionCreateEvent> {
	public static String encode(String string) {
		return string.replace("/", "&sol;");
	}

	public static String decode(String string) {
		return string.replace("&sol;", "/");
	}

	public final App app;
	public final T event;
	public final CommandContext context;

	public InteractionEventWrapper(App app, GuildCollections gc, T e) {
		this.app = app;
		event = e;
		context = createContext();
		context.handler = app.discordHandler;
		context.gc = gc;
		context.channelInfo = gc == null ? null : gc.channels().getChannelOrThread(e.getInteraction().getChannelId().asLong());
		context.message = event.getInteraction().getMessage().orElse(null);
		context.sender = event.getInteraction().getMember().orElse(null);
		context.interaction = this;
	}

	@Override
	public String toString() {
		if (event instanceof ApplicationCommandInteractionEvent e) {
			return e.getCommandName();
		} else if (event instanceof ChatInputAutoCompleteEvent e) {
			return e.getCommandName();
		}

		return event.getClass().getName();
	}

	protected CommandContext createContext() {
		return new CommandContext();
	}

	public Instant getTimestamp() {
		return event.getInteraction().getId().getTimestamp();
	}

	public InteractionResponse getResponse() {
		throw new IllegalStateException("Response not supported in this type of interaction!");
	}

	public void delete() {
		getResponse().deleteInitialResponse().subscribe();
	}
}
