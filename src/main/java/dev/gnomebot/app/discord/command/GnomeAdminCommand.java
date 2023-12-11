package dev.gnomebot.app.discord.command;

import dev.gnomebot.app.discord.command.admin.BanCommand;
import dev.gnomebot.app.discord.command.admin.DisplayCommands;
import dev.gnomebot.app.discord.command.admin.EchoCommand;
import dev.gnomebot.app.discord.command.admin.KickCommand;
import dev.gnomebot.app.discord.command.admin.LockdownCommand;
import dev.gnomebot.app.discord.command.admin.MuteCommand;
import dev.gnomebot.app.discord.command.admin.MutesCommand;
import dev.gnomebot.app.discord.command.admin.NoteCommand;
import dev.gnomebot.app.discord.command.admin.RegexKickCommand;
import dev.gnomebot.app.discord.command.admin.SettingsCommands;
import dev.gnomebot.app.discord.command.admin.UnbanCommand;
import dev.gnomebot.app.discord.command.admin.UnmuteCommand;
import dev.gnomebot.app.discord.command.admin.WarnCommand;
import dev.gnomebot.app.discord.command.admin.WarnsCommand;
import discord4j.rest.util.Permission;
import discord4j.rest.util.PermissionSet;

public class GnomeAdminCommand extends ApplicationCommands {
	@RegisterCommand
	public static final ChatInputInteractionBuilder COMMAND = chatInputInteraction("gnome-admin")
			.defaultMemberPermissions(PermissionSet.of(Permission.MODERATE_MEMBERS))
			.add(subGroup("settings")
					.description("Configure Gnome bot")
					.add(sub("set")
							.description("Change a setting")
							.add(string("key").required().suggest(SettingsCommands::suggestKey))
							.add(string("value").required().suggest(SettingsCommands::suggestValue))
							.run(SettingsCommands::set)
					)
					.add(sub("get")
							.description("Print a setting value")
							.add(string("key").required().suggest(SettingsCommands::suggestKey))
							.run(SettingsCommands::get)
					)
					.add(sub("logout-everyone")
							.description("Log everyone out of the panel (Invalidates everyone's tokens)")
							.run(PanelCommands::logoutEveryone)
					)
			)
			.add(sub("echo")
					.description("Sends message back as bot")
					.add(string("message").required())
					.run(EchoCommand::run)
			)
			.add(sub("ban")
					.description("Bans a member")
					.add(user("user").required())
					.add(string("reason"))
					.add(bool("delete_messages").description("Deletes Messages"))
					.run(BanCommand::run)
			)
			.add(sub("unban")
					.description("Unbans a member")
					.add(user("user").required())
					.run(UnbanCommand::run)
			)
			.add(sub("kick")
					.description("Kicks a member")
					.add(user("user").required())
					.add(string("reason"))
					.run(KickCommand::run)
			)
			.add(sub("regex-kick")
					.description("Kicks multiple members at once based on their username")
					.add(string("regex").required())
					.add(string("reason"))
					.run(RegexKickCommand::run)
			)
			.add(sub("mute")
					.description("Mutes a member")
					.add(user("user").required())
					.add(string("reason"))
					.add(time("time", false, true))
					.run(MuteCommand::run)
			)
			.add(sub("unmute")
					.description("Unmutes a member")
					.add(user("user").required())
					.run(UnmuteCommand::run)
			)
			.add(sub("mutes")
					.description("Prints all mutes")
					.add(user("user"))
					.run(MutesCommand::run)
			)
			.add(sub("warn")
					.description("Warns a member")
					.add(user("user").required())
					.add(string("reason"))
					.run(WarnCommand::run)
			)
			.add(sub("warns")
					.description("Lists warnings")
					.add(user("user"))
					.run(WarnsCommand::run)
			)
			.add(sub("note")
					.description("Adds note to member")
					.add(user("user").required())
					.add(string("note").required())
					.run(NoteCommand::run)
			)
			.add(subGroup("lockdown")
					.description("Lockdown controls")
					.add(sub("enable")
							.description("Enables lockdown mode")
							.add(time("kick_time", false, true))
							.run(LockdownCommand::enable)
					)
					.add(sub("disable")
							.description("Disables lockdown mode")
							.run(LockdownCommand::disable)
					)
			)
			.add(subGroup("display")
					.description("Displays messages, users, etc.")
					.add(sub("members")
							.add(string("name-regex"))
							.add(role("role"))
							.run(DisplayCommands::members)
					)
					.add(sub("messages")
							.add(string("content-regex"))
							.add(user("member"))
							.add(integer("flags"))
							.add(bool("recently-deleted"))
							.add(bool("activity"))
							.run(DisplayCommands::messages)
					)
					.add(sub("quiet-member-count")
							.run(DisplayCommands::quietMemberCount)
					)
					.add(sub("message-history-export")
							.add(user("member"))
							.run(DisplayCommands::messageHistoryExport)
					)
					.add(sub("message-count-per-month")
							.add(channel("channel"))
							.run(DisplayCommands::messageCountPerMonth)
					)
					.add(sub("admin-roles")
							.run(DisplayCommands::adminRoles)
					)
					.add(sub("hourly-activity")
							.description("Display hourly activity of a member")
							.add(user("member"))
							.add(integer("days"))
							.add(zone("timezone"))
							.run(DisplayCommands::hourlyActivity)
					)
					.add(sub("member-count")
							.description("Displays member count")
							.add(role("role"))
							.run(DisplayCommands::memberCount)
					)
					.add(sub("user-mention-leaderboard")
							.description("User Mention Leaderboard")
							.add(user("mention").required())
							.add(time("timespan", true, false))
							.add(integer("limit"))
							.add(channel("channel"))
							.run(event -> MentionLeaderboardCommand.run(event, true))
					)
					.add(sub("role-mention-leaderboard")
							.description("Role Mention Leaderboard")
							.add(role("mention").required())
							.add(time("timespan", true, false))
							.add(integer("limit"))
							.add(channel("channel"))
							.run(event -> MentionLeaderboardCommand.run(event, false))
					)
			)
			.add(subGroup("feedback")
					.description("Feedback")
					.add(sub("approve")
							.description("Approves feedback")
							.add(string("reason"))
							.add(integer("id"))
							.run(FeedbackCommands::approve)
					)
					.add(sub("deny")
							.description("Denies feedback")
							.add(string("reason"))
							.add(integer("id"))
							.run(FeedbackCommands::deny)
					)
					.add(sub("consider")
							.description("Considers feedback")
							.add(string("reason"))
							.add(integer("id"))
							.run(FeedbackCommands::consider)
					)
					.add(sub("cleanup")
							.description("Removes all approved and denied suggestions")
							.run(FeedbackCommands::cleanup)
					)
			)
			.add(subGroup("scam-domains")
					.description("Manage scam URL domains")
					.add(sub("fetch")
							.description("Fetches list from SinkingYachts")
							.run(ScamsCommands::fetchDomains)
					)
					.add(sub("check")
							.description("Checks if domain is blocked or allowed")
							.add(string("domain").required())
							.run(ScamsCommands::checkDomain)
					)
					.add(sub("block")
							.description("Blocks domain (For scam URLs)")
							.add(string("domain").required())
							.run(ScamsCommands::blockDomain)
					)
					.add(sub("allow")
							.description("Allows domain (For false positives)")
							.add(string("domain").required())
							.run(ScamsCommands::allowDomain)
					)
					.add(sub("remove")
							.description("Removes domain from being either blocked or allowed")
							.add(string("domain").required())
							.run(ScamsCommands::removeDomain)
					)
			)
			.add(sub("test-scam")
					.description("Tests and prints back if input contains any scam URLs")
					.add(string("text").required())
					.run(ScamsCommands::test)
			)
			// END
			;
}
