package dev.gnomebot.app.cli;

import discord4j.core.object.component.Button;

public class CLIModalTest {
	public static final CLICommand COMMAND = CLICommand.make("modal-test")
			.description("Test modals")
			.noAdmin()
			.visible()
			.run(CLIModalTest::run);

	private static void run(CLIEvent event) throws Exception {
		event.respond("Modal Test");
		event.response.addComponentRow(Button.danger("modal-test", "Click this to open test form!"));
	}
}
