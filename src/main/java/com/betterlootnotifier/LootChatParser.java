/*
 * Copyright (c) 2026, GIM 99Chores
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.betterlootnotifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.ChatMessageType;
import net.runelite.client.util.Text;

/**
 * Turns loot broadcast chat lines into {@link LootEvent}s.
 *
 * <p>RuneLite itself parses none of these messages - nothing in the client jar matches on "received
 * a drop", "special loot" or "found something special" - so the wordings below are reconstructed
 * rather than borrowed, and each carries a confidence note. Any loot-looking line that does not match
 * is logged verbatim, which is how the wrong ones get corrected.
 *
 * <p>Kept free of client state so it can be exercised in full by {@code LootChatParserTest}.
 */
final class LootChatParser
{
	/**
	 * Player names are at most 12 characters and may contain spaces, hyphens or underscores. Bounding
	 * the name group matters for {@link #COX_PURPLE}, whose separator is common enough that an
	 * unbounded prefix would swallow half of chat.
	 */
	private static final String NAME = "(?<player>[\\w' -]{1,12})";

	/**
	 * The space Jagex puts inside a display name. Rendered identically to a plain space everywhere it
	 * is shown, which makes a pattern that only accepts the plain one fail for reasons nothing on
	 * screen can explain.
	 */
	private static final char NBSP = '\u00A0';

	/**
	 * The bracketed tail of a broadcast when it is the drop's worth rather than the NPC that dropped
	 * it, as in "105 x Blood rune (36,540 coins)".
	 */
	private static final Pattern COIN_VALUE = Pattern.compile("^(?<amount>[\\d,]+) coins?$");

	/** Loot-ish words used only to decide whether an unmatched line is worth logging. */
	private static final List<String> CANDIDATE_KEYWORDS =
		Arrays.asList("drop", "loot", "special", "received", "found", "purple");

	/**
	 * The NPC a broadcast credits the drop to, e.g. the "(General Graardor)" in
	 * "Hidung received a drop: Rune 2h sword (General Graardor)". Optional, and captured separately
	 * so it does not end up glued onto the item name.
	 *
	 * <p>Greedy-anchored at the end, so for "Clue scroll (hard) (General Graardor)" it takes the last
	 * bracketed run and leaves the tier attached to the item where it belongs.
	 */
	private static final String DROP_SOURCE = "(?: \\((?<src>[^()]+)\\))?";

	private static final List<Rule> RULES = Collections.unmodifiableList(Arrays.asList(
		// Confirmed in-game. Bosses that assign a drop by damage announce it in green to everyone
		// present, as "<col=005f00>Name received a drop: Item</col> <col=106f10>(General Graardor)</col>".
		// Same wording as the group broadcast below, so the channel is what separates them: the game
		// channel means a boss told the room, and the brackets name the NPC.
		new Rule(
			"BOSS_DROP",
			Pattern.compile("^" + NAME + " received a drop: (?:(?<qty>[\\d,]+) x )?(?<item>.+?)"
				+ DROP_SOURCE + "\\.?$"),
			EnumSet.of(ChatMessageType.GAMEMESSAGE, ChatMessageType.SPAM),
			LootSource.BOSS,
			false),

		// Confirmed in-game. The Group Ironman broadcast, which arrives on a clan channel and states
		// the drop's worth in the brackets rather than naming the NPC.
		new Rule(
			"GIM_DROP",
			Pattern.compile("^" + NAME + " received a drop: (?:(?<qty>[\\d,]+) x )?(?<item>.+?)"
				+ DROP_SOURCE + "\\.?$"),
			EnumSet.of(ChatMessageType.CLAN_GIM_MESSAGE, ChatMessageType.CLAN_MESSAGE,
				ChatMessageType.FRIENDSCHATNOTIFICATION),
			LootSource.GROUP_IRONMAN,
			false),

		// Confidence: medium-low. Whether raids re-broadcast to the group at all is unconfirmed.
		new Rule(
			"GIM_SPECIAL_RAID",
			Pattern.compile("^" + NAME + " received special loot from a raid: (?<item>.+?)"
				+ DROP_SOURCE + "\\.?$"),
			EnumSet.of(ChatMessageType.CLAN_GIM_MESSAGE, ChatMessageType.CLAN_MESSAGE,
				ChatMessageType.FRIENDSCHATNOTIFICATION),
			LootSource.GROUP_IRONMAN,
			false),

		// Confidence: medium. Best reconstruction of the Theatre of Blood and Tombs of Amascut purple
		// announcement. The message type is uncertain, so the likely carriers are all accepted.
		new Rule(
			"BOSS_SPECIAL",
			Pattern.compile("^" + NAME + " found something special: (?<item>.+?)" + DROP_SOURCE + "\\.?$"),
			EnumSet.of(ChatMessageType.GAMEMESSAGE, ChatMessageType.FRIENDSCHATNOTIFICATION,
				ChatMessageType.CLAN_MESSAGE, ChatMessageType.SPAM),
			LootSource.BOSS,
			false),

		// Confidence: low, and deliberately the last rule. "<name> - <text>" matches an enormous
		// amount of unrelated chat, so this one fires only on colour-tagged lines and only while the
		// player is inside Chambers of Xeric. If the captured logs show CoX uses the same wording as
		// the rule above, delete this entirely - that is the better outcome.
		new Rule(
			"COX_PURPLE",
			Pattern.compile("^" + NAME + " - (?<item>.+?)\\.?$"),
			EnumSet.of(ChatMessageType.GAMEMESSAGE, ChatMessageType.FRIENDSCHATNOTIFICATION,
				ChatMessageType.SPAM),
			LootSource.BOSS,
			true,
			true)
	));

	private LootChatParser()
	{
	}

	/**
	 * @param type the message's type, which gates every rule
	 * @param raw  the message exactly as it arrived, colour tags intact - the tags are part of the
	 *             signal, so they are tested before being stripped
	 * @return the parsed loot, or null when no rule applies
	 */
	static LootEvent parse(ChatMessageType type, String raw)
	{
		return parse(type, null, raw);
	}

	/**
	 * As {@link #parse(ChatMessageType, String)}, but also tries the line with the speaker's name put
	 * back on the front.
	 *
	 * <p>Chat does not always hand over one flat line: on the clan channels the speaker travels in the
	 * event's own name field, so what arrives can be just "received a drop: 5 x Rune full helm", even
	 * though the chatbox shows the name in front of it. Every pattern here is anchored on the name, so
	 * without this the whole broadcast would go unrecognised.
	 *
	 * @param name the event's name field, or null when it has none
	 */
	static LootEvent parse(ChatMessageType type, String name, String raw)
	{
		if (type == null || raw == null || raw.isEmpty())
		{
			return null;
		}

		final LootEvent direct = parseLine(type, raw);
		if (direct != null)
		{
			return direct;
		}

		final String speaker = name == null ? "" : Text.removeTags(name).replace(NBSP, ' ').trim();
		if (speaker.isEmpty() || raw.contains(speaker))
		{
			return null;
		}

		return parseLine(type, speaker + " " + raw);
	}

	private static LootEvent parseLine(ChatMessageType type, String raw)
	{

		final boolean coloured = raw.contains("<col=");

		// Names arrive with a non-breaking space where the chatbox shows an ordinary one, and the two
		// are indistinguishable on screen and in the log. Folded to a plain space before matching, so
		// that a player whose name contains a space is not silently unrecognisable.
		final String message = Text.removeTags(raw).replace(NBSP, ' ').trim();
		if (message.isEmpty())
		{
			return null;
		}

		for (final Rule rule : RULES)
		{
			if (!rule.types.contains(type) || (rule.requiresColourTag && !coloured))
			{
				continue;
			}

			final Matcher matcher = rule.pattern.matcher(message);
			if (!matcher.matches())
			{
				continue;
			}

			final String item = matcher.group("item").trim();
			if (item.isEmpty())
			{
				continue;
			}

			// The brackets carry one of two different things depending on the broadcast, so decide
			// which before handing either on: a coin total is the drop's worth, anything else is the
			// NPC that dropped it (or, occasionally, part of the item's own name).
			final String bracketed = groupOrNull(rule.hasSourceGroup, matcher);
			final long value = coinValueOf(bracketed);

			return new LootEvent(
				matcher.group("player").trim(),
				item,
				LootEvent.UNKNOWN_ITEM_ID,
				quantityOf(rule, matcher),
				rule.source,
				rule.requiresRaidContext,
				value == LootEvent.UNKNOWN_VALUE ? bracketed : null,
				value);
		}

		return null;
	}

	/**
	 * The ids of every rule in the table, in match order. Exists so the tests can pin the table:
	 * these patterns are reconstructed rather than sourced, so one silently disappearing during a
	 * later correction pass should fail the build rather than quietly stop showing loot.
	 */
	static List<String> ruleIds()
	{
		final List<String> ids = new ArrayList<>(RULES.size());
		for (final Rule rule : RULES)
		{
			ids.add(rule.id);
		}
		return ids;
	}

	/**
	 * Whether an unrecognised line looks enough like loot to be worth writing to the log. Without
	 * this gate a busy clan chat would bury the handful of lines actually worth reading.
	 */
	static boolean looksLootRelated(String message)
	{
		if (message == null || message.isEmpty())
		{
			return false;
		}

		final String lower = Text.removeTags(message).toLowerCase(Locale.ENGLISH);
		for (final String keyword : CANDIDATE_KEYWORDS)
		{
			if (lower.contains(keyword))
			{
				return true;
			}
		}
		return false;
	}

	private static String groupOrNull(boolean present, Matcher matcher)
	{
		if (!present)
		{
			return null;
		}

		final String value = matcher.group("src");
		return value == null || value.trim().isEmpty() ? null : value.trim();
	}

	/**
	 * @return the coin total the bracketed text states, or {@link LootEvent#UNKNOWN_VALUE} when it is
	 *         not a coin total at all
	 */
	private static long coinValueOf(String bracketed)
	{
		if (bracketed == null)
		{
			return LootEvent.UNKNOWN_VALUE;
		}

		final Matcher matcher = COIN_VALUE.matcher(bracketed);
		if (!matcher.matches())
		{
			return LootEvent.UNKNOWN_VALUE;
		}

		try
		{
			return Long.parseLong(matcher.group("amount").replace(",", ""));
		}
		catch (NumberFormatException e)
		{
			// A total too large for a long is not worth losing the whole card over.
			return LootEvent.UNKNOWN_VALUE;
		}
	}

	private static int quantityOf(Rule rule, Matcher matcher)
	{
		if (!rule.hasQuantityGroup)
		{
			return 1;
		}

		final String qty = matcher.group("qty");
		if (qty == null)
		{
			return 1;
		}

		try
		{
			return Math.max(1, Integer.parseInt(qty.replace(",", "")));
		}
		catch (NumberFormatException e)
		{
			// A quantity too large for an int is not worth losing the whole card over.
			return 1;
		}
	}

	private static final class Rule
	{
		private final String id;
		private final Pattern pattern;
		private final Set<ChatMessageType> types;
		private final LootSource source;
		private final boolean requiresColourTag;
		private final boolean requiresRaidContext;
		private final boolean hasQuantityGroup;
		private final boolean hasSourceGroup;

		private Rule(String id, Pattern pattern, Set<ChatMessageType> types, LootSource source,
			boolean requiresColourTag)
		{
			this(id, pattern, types, source, requiresColourTag, false);
		}

		private Rule(String id, Pattern pattern, Set<ChatMessageType> types, LootSource source,
			boolean requiresColourTag, boolean requiresRaidContext)
		{
			this.id = id;
			this.pattern = pattern;
			this.types = types;
			this.source = source;
			this.requiresColourTag = requiresColourTag;
			this.requiresRaidContext = requiresRaidContext;
			this.hasQuantityGroup = pattern.pattern().contains("<qty>");
			this.hasSourceGroup = pattern.pattern().contains("<src>");
		}

		@Override
		public String toString()
		{
			return id;
		}
	}
}
