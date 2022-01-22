package dev.gnomebot.app.discord.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.gnomebot.app.discord.EmbedColors;
import dev.gnomebot.app.util.Utils;
import discord4j.core.object.component.ActionComponent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.reaction.ReactionEmoji;
import discord4j.discordjson.json.EmbedData;
import discord4j.discordjson.json.EmbedFieldData;
import discord4j.discordjson.json.ImmutableEmbedData;
import discord4j.discordjson.json.ImmutableWebhookMessageEditRequest;
import discord4j.discordjson.json.WebhookMessageEditRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * @author LatvianModder
 */
public class DefineCommand extends ApplicationCommands {
	@RootCommand
	public static final CommandBuilder COMMAND = root("define")
			.description("Prints dictionary definition of a word")
			.add(string("word").required())
			.run(DefineCommand::run);

	private static void run(ApplicationCommandEventWrapper event) throws Exception {
		event.acknowledge();

		try {
			JsonObject o = Utils.readInternalJson("api/info/define/" + Utils.encode(event.get("word").asString())).getAsJsonObject();

			if (o.get("found").getAsBoolean()) {
				ImmutableWebhookMessageEditRequest.Builder builder = WebhookMessageEditRequest.builder();
				String word = o.get("word").getAsString();

				ImmutableEmbedData.Builder embedBuilder = EmbedData.builder();
				embedBuilder.color(EmbedColors.GRAY.getRGB());
				embedBuilder.title(word);

				JsonArray meanings = o.get("meanings").getAsJsonArray();

				for (int i = 0; i < Math.min(25, meanings.size()); i++) {
					JsonObject o1 = meanings.get(i).getAsJsonObject();

					StringBuilder b = new StringBuilder("*");
					Utils.titleCase(b, o1.get("definition").getAsString());
					b.append('*');

					if (!o1.get("example").getAsString().isEmpty()) {
						b.append("\n\n\"");
						Utils.titleCase(b, o1.get("example").getAsString());
						b.append('"');
					}

					embedBuilder.addField(EmbedFieldData.builder().name((i + 1) + ". " + o1.get("type").getAsString()).value(Utils.trim(b.toString(), 1024)).inline(false).build());
				}

				builder.addEmbed(embedBuilder.build());

				List<ActionComponent> list = new ArrayList<>();

				for (JsonElement e : o.get("phonetics").getAsJsonArray()) {
					JsonObject o1 = e.getAsJsonObject();

					if (!o1.get("audio_url").getAsString().isEmpty()) {
						list.add(Button.link(o1.get("audio_url").getAsString(), ReactionEmoji.unicode("\uD83C\uDFB5"), o1.get("text").getAsString()));
					}
				}

				if (!list.isEmpty()) {
					builder.addComponent(ActionRow.of(list).getData());
				}

				event.editInitial(builder.build());
				return;
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		event.respond("No Definitions Found!");
	}
}