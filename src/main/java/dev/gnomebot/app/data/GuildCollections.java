package dev.gnomebot.app.data;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import dev.gnomebot.app.App;
import dev.gnomebot.app.AppPaths;
import dev.gnomebot.app.BrainEvents;
import dev.gnomebot.app.GuildPaths;
import dev.gnomebot.app.data.config.BooleanConfigKey;
import dev.gnomebot.app.data.config.ChannelConfigKey;
import dev.gnomebot.app.data.config.ConfigKey;
import dev.gnomebot.app.data.config.DBConfig;
import dev.gnomebot.app.data.config.IntConfigKey;
import dev.gnomebot.app.data.config.MemberConfigKey;
import dev.gnomebot.app.data.config.RoleConfigKey;
import dev.gnomebot.app.data.config.StringConfigKey;
import dev.gnomebot.app.discord.CachedRole;
import dev.gnomebot.app.discord.EmbedColor;
import dev.gnomebot.app.discord.MemberCache;
import dev.gnomebot.app.discord.command.ChatCommandSuggestion;
import dev.gnomebot.app.discord.command.ChatCommandSuggestionEvent;
import dev.gnomebot.app.script.DiscordJS;
import dev.gnomebot.app.script.WrappedGuild;
import dev.gnomebot.app.script.WrappedId;
import dev.gnomebot.app.server.AuthLevel;
import dev.gnomebot.app.util.EmbedBuilder;
import dev.gnomebot.app.util.MapWrapper;
import dev.gnomebot.app.util.MessageBuilder;
import dev.gnomebot.app.util.RecentUser;
import dev.gnomebot.app.util.Utils;
import dev.latvian.apps.webutils.ansi.Ansi;
import dev.latvian.apps.webutils.json.JSON;
import dev.latvian.apps.webutils.json.JSONObject;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.Role;
import discord4j.core.object.entity.channel.CategorizableChannel;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.object.entity.channel.ThreadChannel;
import discord4j.core.object.entity.channel.TopLevelGuildMessageChannel;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.core.spec.MessageEditSpec;
import discord4j.core.spec.StartThreadWithoutMessageSpec;
import discord4j.discordjson.Id;
import discord4j.discordjson.json.ChannelData;
import discord4j.discordjson.json.MemberData;
import discord4j.discordjson.json.UserData;
import discord4j.rest.util.AllowedMentions;
import discord4j.rest.util.Image;
import discord4j.rest.util.Permission;
import discord4j.rest.util.PermissionSet;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GuildCollections {
	public final Databases db;
	public final Snowflake guildId;
	public final WrappedId wrappedId;
	public final MongoDatabase database;
	public final GuildPaths paths;
	public DiscordJS discordJS;

	public final WrappedCollection<DiscordMember> members;
	public final WrappedCollection<DiscordMessage> messages;
	public final WrappedCollection<DiscordMessage> editedMessages;
	public final WrappedCollection<DiscordFeedback> feedback;
	public final WrappedCollection<DiscordPoll> polls;
	public final WrappedCollection<DiscordMessageCount> messageCount;
	public final WrappedCollection<DiscordMessageXP> messageXp;
	public final WrappedCollection<GnomeAuditLogEntry> auditLog;
	public final WrappedCollection<ThreadLocation> memberLogThreads;

	public final DBConfig config;
	public final StringConfigKey name;
	public final StringConfigKey iconUrl;
	public final MemberConfigKey ownerId;
	public final IntConfigKey feedbackNumber;
	public final IntConfigKey pollNumber;

	public final IntConfigKey globalXp;
	public final IntConfigKey regularMessages;
	public final IntConfigKey regularXP;
	public final RoleConfigKey regularRole;
	public final RoleConfigKey adminRole;
	public final RoleConfigKey mutedRole;
	public final RoleConfigKey feedbackSuggestRole;
	public final RoleConfigKey feedbackVoteRole;
	public final RoleConfigKey reportMentionRole;
	public final ChannelConfigKey feedbackChannel;
	public final ChannelConfigKey adminLogChannel;
	public final ChannelConfigKey adminMessagesChannel;
	public final ChannelConfigKey muteAppealChannel;
	public final ChannelConfigKey logNewAccountsChannel;
	public final ChannelConfigKey logLeavingChannel;
	public final ChannelConfigKey reportChannel;
	public final ChannelConfigKey logIpAddressesChannel;
	public final ChannelConfigKey appealChannel;
	public final StringConfigKey legacyPrefix;
	public final StringConfigKey macroPrefix;
	public final StringConfigKey inviteCode;
	public final BooleanConfigKey lockdownMode;
	public final IntConfigKey kickNewAccounts;
	public final BooleanConfigKey anonymousFeedback;
	public final BooleanConfigKey adminsBypassAnonFeedback;
	public final StringConfigKey font;
	public final IntConfigKey autoMuteUrlShortener;
	public final IntConfigKey autoMuteScamUrl;
	public final BooleanConfigKey autoPaste;
	public final StringConfigKey reportOptions;
	public final BooleanConfigKey autoMuteEmbed;

	private WrappedGuild wrappedGuild;
	private Map<Snowflake, ChannelInfo> channelMap;
	private List<ChannelInfo> channelList;
	private Map<Snowflake, CachedRole> roleMap;
	private List<CachedRole> roleList;
	public final List<RecentUser> recentUsers;
	private List<ChatCommandSuggestion> recentUserSuggestions;
	private Map<String, Macro> macroMap;
	private final Map<Long, Snowflake> memberLogThreadCache;
	private final Map<Long, Snowflake> appealThreadCache;

	public boolean advancedLogging = false;

	public GuildCollections(Databases d, Snowflake g) {
		db = d;
		guildId = g;
		wrappedId = new WrappedId(guildId);
		database = db.mongoClient.getDatabase("gnomebot_" + g.asString());
		paths = AppPaths.getGuildPaths(guildId);

		String dbid = guildId.asString();

		members = create("members", DiscordMember::new);
		// TODO: Move messages to edited when channel is deleted
		messages = create("messages", DiscordMessage::new);
		editedMessages = create("edited_messages", DiscordMessage::new).expiresAfterMonth("timestamp_expire_" + dbid, "timestamp", null); // GDPR
		feedback = create("feedback", DiscordFeedback::new);
		polls = create("polls", DiscordPoll::new);
		messageCount = create("message_count", DiscordMessageCount::new);
		messageXp = create("message_xp", DiscordMessageXP::new);
		auditLog = create("audit_log", GnomeAuditLogEntry::new).expiresAfterMonth("timestamp_expire_" + dbid, "expires", Filters.exists("expires")); // GDPR
		memberLogThreads = create("member_log_threads", ThreadLocation::new);

		config = new DBConfig();

		name = config.add(new StringConfigKey(this, "name", guildId.asString()));
		iconUrl = config.add(new StringConfigKey(this, "icon_url", ""));
		ownerId = config.add(new MemberConfigKey(this, "owner_id"));
		feedbackNumber = config.add(new IntConfigKey(this, "feedback_number", 0));
		pollNumber = config.add(new IntConfigKey(this, "poll_number", 0));

		globalXp = config.add(new IntConfigKey(this, "global_xp", 0)).title("Global XP");
		regularMessages = config.add(new IntConfigKey(this, "regular_messages", 0)).title("Regular Messages");
		regularXP = config.add(new IntConfigKey(this, "regular_xp", 3000)).title("Regular XP");
		regularRole = config.add(new RoleConfigKey(this, "regular_role")).title("Regular Role");
		adminRole = config.add(new RoleConfigKey(this, "admin_role")).title("Admin Role");
		mutedRole = config.add(new RoleConfigKey(this, "muted_role")).title("Muted Role");
		feedbackSuggestRole = config.add(new RoleConfigKey(this, "feedback_suggest_role")).title("Feedback Role for suggest command");
		feedbackVoteRole = config.add(new RoleConfigKey(this, "feedback_vote_role")).title("Feedback Role for voting");
		reportMentionRole = config.add(new RoleConfigKey(this, "report_mention_role")).title("Message Report mention role");
		feedbackChannel = config.add(new ChannelConfigKey(this, "feedback_channel")).title("Feedback Channel");
		adminLogChannel = config.add(new ChannelConfigKey(this, "admin_log_channel")).title("Admin Log Channel");
		adminMessagesChannel = config.add(new ChannelConfigKey(this, "admin_messages_channel")).title("Admin Messages Channel");
		muteAppealChannel = config.add(new ChannelConfigKey(this, "mute_appeal_channel")).title("Mute Appeal Channel");
		logNewAccountsChannel = config.add(new ChannelConfigKey(this, "log_new_accounts")).title("Log New Accounts Channel");
		logLeavingChannel = config.add(new ChannelConfigKey(this, "log_leaving")).title("Log Leaving Channel");
		reportChannel = config.add(new ChannelConfigKey(this, "report_channel")).title("Report Channel");
		logIpAddressesChannel = config.add(new ChannelConfigKey(this, "log_ip_addresses_channel")).title("Log IP Addresses Channel");
		appealChannel = config.add(new ChannelConfigKey(this, "appeal_channel")).title("Appeal Channel");
		legacyPrefix = config.add(new StringConfigKey(this, "prefix", "!")).title("Command Prefix");
		macroPrefix = config.add(new StringConfigKey(this, "custom_command_prefix", "!")).title("Macro Prefix");
		inviteCode = config.add(new StringConfigKey(this, "invite_code", "")).title("Invite Code");
		lockdownMode = config.add(new BooleanConfigKey(this, "lockdown_mode", false)).title("Lockdown Mode");
		kickNewAccounts = config.add(new IntConfigKey(this, "kick_new_accounts", 0)).title("Kick New Accounts (in seconds since account created, e.g 604800 == 1 week)");
		anonymousFeedback = config.add(new BooleanConfigKey(this, "anonymous_feedback", false)).title("Anonymous Feedback");
		adminsBypassAnonFeedback = config.add(new BooleanConfigKey(this, "anonymous_feedback_admin_bypass", true)).title("Admins Bypass Anonymous Feedback");
		font = config.add(new StringConfigKey(this, "font", "DejaVu Sans Light").enumValues(App::listFonts)).title("Font");
		autoMuteUrlShortener = config.add(new IntConfigKey(this, "automute_url_shortener", 0, 0, 43800)).title("Auto-mute url shortener link (minutes)");
		autoMuteScamUrl = config.add(new IntConfigKey(this, "automute_scam_url", 30, 0, 43800)).title("Auto-mute potential scam link (minutes)");
		autoPaste = config.add(new BooleanConfigKey(this, "auto_paste", true)).title("Auto-paste text files");
		reportOptions = config.add(new StringConfigKey(this, "report_options", "Scam | Spam | NSFW | Hacks")).title("Report Options (separated by ' | ')");
		autoMuteEmbed = config.add(new BooleanConfigKey(this, "auto_mute_embed", true)).title("Post info embed about auto-muted users");

		recentUsers = new ArrayList<>();
		memberLogThreadCache = new HashMap<>();
		appealThreadCache = new HashMap<>();

		var updates = new ArrayList<Bson>();
		config.read(dbid, db.guildData.query(guildId.asLong()).firstDocument(), updates);

		if (!updates.isEmpty()) {
			db.guildData.query(guildId.asLong()).upsert(updates);
		}

		postReadSettings();
		discordJS = new DiscordJS(this, false);
	}

	public <T extends WrappedDocument<T>> WrappedCollection<T> create(String ci, BiFunction<WrappedCollection<T>, MapWrapper, T> w) {
		return db.create(database, ci, w).gc(this);
	}

	public Guild getGuild() {
		return Objects.requireNonNull(db.app.discordHandler.client.getGuildById(guildId).block());
	}

	@Nullable
	public MemberData getMemberData(Snowflake id) {
		try {
			return db.app.discordHandler.client.getRestClient().getGuildService().getGuildMember(guildId.asLong(), id.asLong()).block();
		} catch (Exception ex) {
			return null;
		}
	}

	@Nullable
	public Member getMember(Snowflake id) {
		try {
			return db.app.discordHandler.client.getMemberById(guildId, id).block();
		} catch (Exception ex) {
			return null;
		}
	}

	public PermissionSet getEffectiveGlobalPermissions(Snowflake member) {
		try {
			var set = getMember(member).getBasePermissions().block();

			if (set == null) {
				return PermissionSet.none();
			} else if (set.contains(Permission.ADMINISTRATOR)) {
				return PermissionSet.all();
			} else {
				return set;
			}
		} catch (Exception ignored) {
			return PermissionSet.none();
		}
	}

	@Override
	public String toString() {
		return name.get();
	}

	public boolean isMM() {
		return guildId.asLong() == 166630061217153024L;
	}

	public boolean isTest() {
		return guildId.asLong() == 720671115336220693L;
	}

	public GatewayDiscordClient getClient() {
		return db.app.discordHandler.client;
	}

	public void postReadSettings() {
		// App.info("Loading settings for " + this + "...");

		//Table settingsTable = new Table("Setting", "Value");
		//
		//for (DBSetting<?, ?> setting : settings.map.values())
		//{
		//	settingsTable.addRow(setting.key, setting);
		//}
		//
		//settingsTable.print();
	}

	public String getClickableName() {
		if (inviteCode.get().isEmpty()) {
			return name.get();
		}

		return "[" + name.get() + "](https://discord.gg/" + inviteCode + ")";
	}

	public Optional<Message> findMessage(@Nullable Snowflake id, @Nullable ChannelInfo priority) {
		if (id == null) {
			return Optional.empty();
		}

		var c = getChannelList();

		if (priority != null) {
			c.remove(priority);
			c.add(0, priority);
		}

		for (var channel : c) {
			var m = channel.getMessage(id);

			if (m != null) {
				return Optional.of(m);
			}
		}

		return Optional.empty();
	}

	public void adminLogChannelEmbed(@Nullable UserData user, ChannelConfigKey channelConfig, Consumer<EmbedBuilder> embed) {
		var builder = EmbedBuilder.create();
		builder.color(EmbedColor.RED);
		builder.timestamp(Instant.now());
		embed.accept(builder);
		var message = MessageBuilder.create(builder);

		if (user != null) {
			var id = memberAuditLogThread(user);

			if (id.asLong() != 0L) {
				db.app.discordHandler.client.getRestClient().getChannelService().createMessage(id.asLong(), message.toMultipartMessageCreateRequest()).block();
				closeThread(id, Duration.ofMinutes(5L));
				return;
			}
		}

		channelConfig.messageChannel().ifPresent(c -> c.createMessage(message).subscribe());
	}

	public long getUserID(String tag) {
		var member = getGuild().getMembers().cache().filter(m1 -> m1.getTag().equals(tag)).blockFirst();
		return member == null ? 0L : member.getId().asLong();
	}

	public void unmute(Snowflake user, long seconds, String reason) {
		if (seconds <= 0L) {
			ScheduledTask.unmuteNow(this, user, reason);
		} else if (seconds < Integer.MAX_VALUE) {
			db.app.schedule(Duration.ofSeconds(seconds), ScheduledTask.UNMUTE, guildId.asLong(), 0L, user.asLong(), reason);
		}
	}

	public MemberCache createMemberCache() {
		return new MemberCache(this);
	}

	public ChannelInfo getChannelInfo(Snowflake id) {
		return Objects.requireNonNull(getChannelMap().get(id));
	}

	public ChannelInfo getChannelInfo(Channel channel) {
		return getChannelInfo(channel.getId());
	}

	public String getChannelName(Snowflake channel) {
		ChannelInfo c = getChannelMap().get(channel);
		return c == null ? channel.asString() : c.getName();
	}

	public JSONObject getChannelJson(Snowflake channel) {
		var json = JSONObject.of();
		json.put("id", channel.asString());
		json.put("name", getChannelName(channel));
		return json;
	}

	public void save(String key) {
		ConfigKey<?> c = config.map.get(key);

		if (c != null) {
			c.save();
		}
	}

	public AuthLevel getAuthLevel(@Nullable Member member) {
		if (member == null) {
			return AuthLevel.NO_AUTH;
		} else if (member.getId().equals(db.app.discordHandler.selfId)) {
			return AuthLevel.OWNER;
		} else if (member.getId().equals(getGuild().getOwnerId())) {
			return AuthLevel.OWNER;
		}

		Set<Snowflake> roleIds = member.getRoleIds();

		for (Snowflake id : roleIds) {
			CachedRole r = getRoleMap().get(id);

			if (r != null && r.ownerRole) {
				return AuthLevel.OWNER;
			}
		}

		for (Snowflake id : roleIds) {
			CachedRole r = getRoleMap().get(id);

			if (r != null && r.adminRole) {
				return AuthLevel.ADMIN;
			}
		}

		return AuthLevel.MEMBER;
	}

	public AuthLevel getAuthLevel(@Nullable Snowflake memberId) {
		if (memberId == null) {
			return AuthLevel.NO_AUTH;
		} else if (memberId.equals(db.app.discordHandler.selfId)) {
			return AuthLevel.OWNER;
		} else if (memberId.equals(getGuild().getOwnerId())) {
			return AuthLevel.OWNER;
		}

		MemberData data = getMemberData(memberId);

		if (data == null) {
			return AuthLevel.NO_AUTH;
		}

		List<Id> roleIds = data.roles();

		for (Id id : roleIds) {
			CachedRole r = getRoleMap().get(Snowflake.of(id));

			if (r != null && r.ownerRole) {
				return AuthLevel.OWNER;
			}
		}

		for (Id id : roleIds) {
			CachedRole r = getRoleMap().get(Snowflake.of(id));

			if (r != null && r.adminRole) {
				return AuthLevel.ADMIN;
			}
		}

		return AuthLevel.MEMBER;
	}

	private void refreshCache() {
		refreshChannelCache();
		wrappedGuild = null;
		roleMap = null;
		roleList = null;
		macroMap = null;
	}

	public void guildUpdated(@Nullable Guild g) {
		App.LOGGER.event(BrainEvents.REFRESHED_GUILD_CACHE);
		refreshCache();

		if (g != null) {
			String n = g.getName();

			if (!name.get().equals(n)) {
				name.set(n);
				name.save();
			}

			String i = g.getIconUrl(Image.Format.PNG).orElse("");

			if (!iconUrl.get().equals(i)) {
				iconUrl.set(i);
				iconUrl.save();
			}

			Snowflake o = g.getOwnerId();

			if (!ownerId.get().equals(o)) {
				ownerId.set(o);
				ownerId.save();
			}
		}
	}

	public void channelUpdated(@Nullable CategorizableChannel old, TopLevelGuildMessageChannel channel, boolean deleted) {
		App.LOGGER.event(BrainEvents.REFRESHED_CHANNEL_CACHE);
		refreshCache();

		if (advancedLogging) {
			App.info("Channel updated: " + this + "/#" + channel.getName());

			if (old != null) {
				if (!old.getName().equals(channel.getName())) {
					App.info("> Name " + old.getName() + " -> " + channel.getName());
				}

				if (old.getRawPosition() != channel.getRawPosition()) {
					App.info("> Position " + old.getRawPosition() + " -> " + channel.getRawPosition());
				}
			}
		}

		if (!deleted) {
			ChannelInfo ci = getChannelMap().get(channel.getId());

			if (ci != null) {
				ci.settings.updateFrom(channel);
			} else {
				App.error("Unknown channel " + channel.getId().asString() + "/" + channel.getName() + " updated!");
			}
		}
	}

	public void roleUpdated(Snowflake roleId, boolean deleted) {
		App.LOGGER.event(BrainEvents.REFRESHED_ROLE_CACHE);
		refreshCache();
	}

	// 0 update | 1 join | 2 leave
	public void memberUpdated(Snowflake userId, int type) {
		if (type == 0) {
			// App.LOGGER.refreshedMemberCache();
		}
		// refreshCache();
	}

	public synchronized void refreshChannelCache() {
		channelMap = null;
		channelList = null;
	}

	public synchronized Map<Snowflake, ChannelInfo> getChannelMap() {
		if (channelMap == null) {
			channelMap = new LinkedHashMap<>();

			try {
				for (var ch : getGuild().getChannels()
						.filter(c -> c instanceof TopLevelGuildMessageChannel)
						.cast(TopLevelGuildMessageChannel.class)
						.sort(Comparator.comparing(TopLevelGuildMessageChannel::getRawPosition).thenComparing(TopLevelGuildMessageChannel::getId))
						.toIterable()) {
					var settings = getChannelSettings(ch.getId());
					settings.updateFrom(ch);
					var c = new ChannelInfo(this, ch.getId(), settings);
					channelMap.put(ch.getId(), c);
				}
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}

		return channelMap;
	}

	public List<ChannelInfo> getChannelList() {
		if (channelList == null) {
			channelList = new ArrayList<>(getChannelMap().values());
		}

		return channelList;
	}

	public Map<Snowflake, CachedRole> getRoleMap() {
		if (roleMap == null) {
			roleMap = new LinkedHashMap<>();

			try {
				getGuild().getRoles()
						.filter(r -> !r.isEveryone())
						.sort(Comparator.comparing(Role::getRawPosition).thenComparing(Role::getId).reversed())
						.toStream()
						.forEach(r -> roleMap.put(r.getId(), new CachedRole(this, r)));
			} catch (Exception ex) {
			}

			roleList = new ArrayList<>(roleMap.values());

			CachedRole adminRoleW = roleMap.get(adminRole.get());
			int i = adminRoleW == null ? -1 : roleList.indexOf(adminRoleW);

			if (i != -1) {
				for (int j = 0; j <= i; j++) {
					roleList.get(j).adminRole = true;
				}
			}
		}

		return roleMap;
	}

	public List<CachedRole> getRoleList() {
		getRoleMap();
		return roleList;
	}

	public void auditLog(GnomeAuditLogEntry.Builder builder) {
		try {
			auditLog.insert(builder.build());
		} catch (Exception ex) {
			Ansi.log(Ansi.orange("Failed to write to audit log [" + builder.type.name + "]: ").append(Ansi.darkRed(ex)));
		}
	}

	public WrappedGuild getWrappedGuild() {
		if (wrappedGuild == null) {
			wrappedGuild = new WrappedGuild(discordJS, this);
		}

		return wrappedGuild;
	}

	public Stream<Member> getMemberStream() {
		return getGuild().getMembers().toStream();
	}

	public List<Member> getMembers() {
		return getMemberStream().toList();
	}

	private ChannelSettings getChannelSettings(Snowflake id) {
		var settings = db.channelSettings.findFirst(id);

		if (settings == null) {
			settings = new ChannelSettings(db.channelSettings, MapWrapper.wrap(new Document("_id", id.asLong())));
			db.channelSettings.insert(settings.document.toDocument());
		}

		return settings;
	}

	public ChannelInfo getOrMakeChannelInfo(Snowflake id) {
		ChannelInfo ci = getChannelMap().get(id);

		if (ci == null) {
			ci = new ChannelInfo(this, id, getChannelSettings(id));
			ChannelData data = ci.getChannelData();
			Id parentId = data == null ? null : data.parentId().toOptional().orElse(Optional.empty()).orElse(null);

			if (parentId != null) {
				ci = getOrMakeChannelInfo(Snowflake.of(parentId)).thread(id, "-");
			}
		}

		return ci;
	}

	public void usernameSuggestions(ChatCommandSuggestionEvent event) {
		if (recentUserSuggestions == null) {
			recentUserSuggestions = new ArrayList<>();

			for (int i = 0; i < recentUsers.size(); i++) {
				RecentUser user = recentUsers.get(i);
				recentUserSuggestions.add(new ChatCommandSuggestion(user.tag(), user.id().asString(), user.tag().toLowerCase(), recentUsers.size() - i));
			}

			Set<Snowflake> set = recentUsers.stream().map(RecentUser::id).collect(Collectors.toSet());

			for (Member member : getMembers()) {
				if (!set.contains(member.getId())) {
					recentUserSuggestions.add(new ChatCommandSuggestion(member.getTag(), member.getId().asString(), member.getTag().toLowerCase(), 0));
				}
			}
		}

		event.transformSearch = s -> s.toLowerCase().replace('@', ' ').trim();
		event.suggestions.addAll(recentUserSuggestions);
	}

	public void pushRecentUser(Snowflake userId, String tag) {
		if (!recentUsers.isEmpty() && recentUsers.get(0).id().equals(userId)) {
			return;
		}

		if (tag.endsWith("#0")) {
			tag = tag.substring(0, tag.length() - 2);
		} else if (tag.endsWith("#null")) {
			tag = tag.substring(0, tag.length() - 5);
		}

		recentUserSuggestions = null;
		RecentUser user = new RecentUser(userId, tag);
		recentUsers.remove(user);
		recentUsers.add(0, user);

		if (recentUsers.size() > 1000) {
			recentUsers.remove(recentUsers.size() - 1);
		}
	}

	public Map<String, Macro> getMacroMap() {
		if (macroMap == null) {
			macroMap = new LinkedHashMap<>();

			try {
				if (Files.exists(paths.macrosFile)) {
					for (var entry : JSON.DEFAULT.read(paths.macrosFile).readObject().entrySet()) {
						if (entry.getValue() instanceof JSONObject json) {
							var macro = new Macro(this);
							macro.id = entry.getKey().toLowerCase();
							macro.name = entry.getKey();
							macro.content = json.asString("content");
							macro.author = json.asLong("author");
							macro.created = json.containsKey("created") ? Instant.parse(json.asString("created")) : null;
							macro.uses = json.asInt("uses");
							macro.slashCommand = json.asLong("slash_command");
							macroMap.put(macro.id, macro);
						}
					}
				}
			} catch (Exception ex) {
				macroMap = null;
				ex.printStackTrace();
			}
		}

		return macroMap;
	}

	@Nullable
	public Macro getMacro(String name) {
		var m = getMacroMap().get(name.toLowerCase());

		if (m == null) {
			if (name.startsWith("moddedmc:")) {
				return db.guild(Snowflake.of(166630061217153024L)).getMacro(name.substring(9));
			} else if (name.startsWith("lat:")) {
				return db.guild(Snowflake.of(303440391124942858L)).getMacro(name.substring(4));
			}
		}

		return m;
	}

	public void saveMacroMap() {
		var json = JSONObject.of();

		for (var macro : getMacroMap().values()) {
			var obj = JSONObject.of();
			obj.put("content", macro.content);
			obj.put("author", macro.author);

			if (macro.created != null) {
				obj.put("created", macro.created.toString());
			}

			if (macro.uses > 0) {
				obj.put("uses", macro.uses);
			}

			if (macro.slashCommand != 0L) {
				obj.put("slash_command", macro.slashCommand);
			}

			json.put(macro.name, obj);
		}

		try {
			if (Files.notExists(paths.macrosFile)) {
				Files.createFile(paths.macrosFile);
			}

			Files.writeString(paths.macrosFile, json.toString());
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private Snowflake memberLogThread(int type, Map<Long, Snowflake> cache, @Nullable UserData user, ChannelConfigKey config) {
		if (user == null) {
			return Utils.NO_SNOWFLAKE;
		}

		var id = cache.get(user.id().asLong());

		exit:
		if (id == null) {
			var ci = config.messageChannel().orElse(null);
			var topLevelChannel = ci == null ? null : ci.getTopLevelChannel();

			if (topLevelChannel != null) {
				try {
					var doc = memberLogThreads.query(user.id().asLong()).eq("type", type).eq("channel", ci.id.asLong()).projectionFields("thread").first();

					if (doc != null) {
						var thread = db.app.discordHandler.client.getChannelById(doc.thread).cast(ThreadChannel.class).block();

						if (thread != null) {
							id = thread.getId();
							break exit;
						}
					}
				} catch (Exception ignore) {
				}

				var thread = topLevelChannel.startThread(StartThreadWithoutMessageSpec.builder()
						.type(type == 0 ? ThreadChannel.Type.GUILD_PUBLIC_THREAD : ThreadChannel.Type.GUILD_PRIVATE_THREAD)
						.invitable(false)
						.reason(user.username() + " Member Channel")
						.name(user.username())
						.autoArchiveDuration(type == 0 ? ThreadChannel.AutoArchiveDuration.DURATION1 : ThreadChannel.AutoArchiveDuration.DURATION2)
						.build()
				).block();

				memberLogThreads.query(user.id().asLong()).eq("type", type).eq("channel", ci.id.asLong()).upsert(List.of(Updates.set("thread", thread.getId().asLong())));

				if (type == 0) {
					var list = new ArrayList<String>();
					list.add("# <@" + user.id().asString() + ">");
					list.add("### ID");
					list.add(user.id().asString());
					list.add("### Username");
					list.add(user.username());
					list.add("### Global Name");
					list.add(user.globalName().orElse(user.username()));
					list.add("### Account Created");
					list.add(Utils.formatRelativeDate(Snowflake.of(user.id().asLong()).getTimestamp()));

					try {
						var member = getMember(Snowflake.of(user.id().asLong()));

						if (member != null && member.getJoinTime().isPresent()) {
							list.add("### Joined");
							list.add(Utils.formatRelativeDate(member.getJoinTime().get()));
						}
					} catch (Exception ignored) {
					}

					thread.createMessage(MessageCreateSpec.builder()
							.content(String.join("\n", list))
							.allowedMentions(AllowedMentions.builder()
									.allowUser(Snowflake.of(user.id().asLong()))
									.build()
							)
							.build()
					).subscribe();
				}

				if (adminRole.isSet()) {
					thread.createMessage("...").withAllowedMentions(AllowedMentions.builder()
							.allowRole(adminRole.get())
							.build()
					).flatMap(m -> m.edit(MessageEditSpec.builder()
							.allowedMentionsOrNull(AllowedMentions.builder()
									.allowRole(adminRole.get())
									.build()
							)
							.contentOrNull("Adding <@&" + adminRole.get().asString() + ">...")
							.build()
					)).flatMap(Message::delete).subscribe();
				}

				id = thread.getId();
			}
		}

		if (id == null) {
			id = Utils.NO_SNOWFLAKE;
		}

		cache.put(user.id().asLong(), id);
		return id;
	}

	public Snowflake memberAuditLogThread(@Nullable UserData user) {
		return memberLogThread(0, memberLogThreadCache, user, adminLogChannel);
	}

	public Snowflake memberAppealThread(@Nullable UserData user) {
		return memberLogThread(1, appealThreadCache, user, appealChannel);
	}

	public void closeThread(Snowflake threadId, Duration duration) {
		var task = db.app.findScheduledGuildTask(guildId, t -> t.type.equals(ScheduledTask.CLOSE_THREAD) && t.channelId.asLong() == threadId.asLong());

		if (task != null) {
			task.changeEnd(Math.min(task.end, System.currentTimeMillis() + duration.toMillis()));
		} else {
			db.app.schedule(duration, ScheduledTask.CLOSE_THREAD, guildId.asLong(), threadId.asLong(), 0L, "");
		}
	}
}