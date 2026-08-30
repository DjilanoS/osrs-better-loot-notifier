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

import java.util.Arrays;
import net.runelite.api.ChatMessageType;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The broadcast wordings this plugin matches were reconstructed rather than taken from RuneLite,
 * which parses none of them. That makes this table the part most likely to be wrong, and the part
 * most worth pinning: these tests fix the intended behaviour so a later correction pass changes
 * exactly what it means to.
 */
public class LootChatParserTest
{
	@Test
	public void ruleTableIsPinned()
	{
		assertEquals(
			Arrays.asList("BOSS_DROP", "GIM_DROP", "GIM_SPECIAL_RAID", "BOSS_SPECIAL", "COX_PURPLE"),
			LootChatParser.ruleIds());
	}

	@Test
	public void parsesGroupIronmanDrop()
	{
		final LootEvent loot = LootChatParser.parse(
			ChatMessageType.CLAN_GIM_MESSAGE, "Zezima received a drop: Twisted bow.");

		assertNotNull(loot);
		assertEquals("Zezima", loot.getPlayerName());
		assertEquals("Twisted bow", loot.getItemName());
		assertEquals(1, loot.getQuantity());
		assertEquals(LootSource.GROUP_IRONMAN, loot.getSource());
		assertEquals(LootEvent.UNKNOWN_ITEM_ID, loot.getItemId());
	}

	/**
	 * Captured verbatim from the client log. Bosses that assign a drop by damage announce it in green
	 * to everyone present, with the NPC in its own colour span after the item.
	 */
	@Test
	public void parsesTheBossDropAssignment()
	{
		final LootEvent loot = LootChatParser.parse(
			ChatMessageType.GAMEMESSAGE,
			"<col=005f00>Hidung received a drop: Rune 2h sword</col> <col=106f10>(General Graardor)</col>");

		assertNotNull(loot);
		assertEquals("Hidung", loot.getPlayerName());
		assertEquals("the killer must not be glued onto the item name",
			"Rune 2h sword", loot.getItemName());
		assertEquals("General Graardor", loot.getDropSource());
		assertEquals(1, loot.getQuantity());
		assertEquals("announced by the boss, not broadcast to a group",
			LootSource.BOSS, loot.getSource());
	}

	@Test
	public void bossAssignmentsCarryQuantityAndArriveOnEitherGameChannel()
	{
		final LootEvent mole = LootChatParser.parse(
			ChatMessageType.GAMEMESSAGE,
			"<col=005f00>Zezima received a drop: 3 x Mole claw</col> <col=106f10>(Giant Mole)</col>");

		assertNotNull(mole);
		assertEquals("Mole claw", mole.getItemName());
		assertEquals(3, mole.getQuantity());
		assertEquals("Giant Mole", mole.getDropSource());
		assertEquals(LootSource.BOSS, mole.getSource());

		// Game messages are filtered to the spam channel under some chat settings.
		final LootEvent spam = LootChatParser.parse(
			ChatMessageType.SPAM,
			"<col=005f00>Zezima received a drop: Mole skin</col> <col=106f10>(Giant Mole)</col>");

		assertNotNull("a boss announcement must survive being filtered to spam", spam);
		assertEquals(LootSource.BOSS, spam.getSource());
	}

	@Test
	public void theChannelSeparatesABossAnnouncementFromAGroupBroadcast()
	{
		// Identical wording; only the channel and the brackets differ.
		final LootEvent group = LootChatParser.parse(
			ChatMessageType.CLAN_GIM_MESSAGE,
			"Fluid Juicey received a drop: 105 x Blood rune (36,540 coins).");
		final LootEvent boss = LootChatParser.parse(
			ChatMessageType.GAMEMESSAGE,
			"Fluid Juicey received a drop: 105 x Blood rune (General Graardor)");

		assertNotNull(group);
		assertNotNull(boss);
		assertEquals(LootSource.GROUP_IRONMAN, group.getSource());
		assertEquals(LootSource.BOSS, boss.getSource());
	}

	@Test
	public void parsesRealBroadcastWithQuantityAndKiller()
	{
		final LootEvent coins = LootChatParser.parse(
			ChatMessageType.GAMEMESSAGE,
			"<col=005f00>GIM 99Chores received a drop: 19,612 x Coins</col> <col=106f10>(General Graardor)</col>");

		assertNotNull(coins);
		assertEquals("GIM 99Chores", coins.getPlayerName());
		assertEquals("Coins", coins.getItemName());
		assertEquals(19_612, coins.getQuantity());
		assertEquals("General Graardor", coins.getDropSource());

		final LootEvent potato = LootChatParser.parse(
			ChatMessageType.GAMEMESSAGE,
			"GIM 99Chores received a drop: 3 x Chilli potato (Sergeant Steelwill)");

		assertNotNull(potato);
		assertEquals("Chilli potato", potato.getItemName());
		assertEquals(3, potato.getQuantity());
		assertEquals("Sergeant Steelwill", potato.getDropSource());
	}

	/**
	 * Captured from the client log. These broadcasts state the drop's worth in the brackets rather
	 * than naming the killer, and reading it is what lets an untradeable like a Crystal shard - which
	 * has no Grand Exchange price to look up - survive a minimum value filter.
	 */
	@Test
	public void readsTheValueStatedByTheBroadcast()
	{
		final LootEvent blood = LootChatParser.parse(
			ChatMessageType.CLAN_GIM_MESSAGE,
			"Fluid Juicey received a drop: 105 x Blood rune (36,540 coins).");

		assertNotNull(blood);
		assertEquals("Fluid Juicey", blood.getPlayerName());
		assertEquals("Blood rune", blood.getItemName());
		assertEquals(105, blood.getQuantity());
		assertEquals(36_540L, blood.getBroadcastValue());
		assertNull("a coin total is not a drop source", blood.getDropSource());

		final LootEvent shard = LootChatParser.parse(
			ChatMessageType.CLAN_GIM_MESSAGE,
			"Fluid Juicey received a drop: 12 x Crystal shard (120,000 coins).");

		assertNotNull(shard);
		assertEquals("Crystal shard", shard.getItemName());
		assertEquals(12, shard.getQuantity());
		assertEquals(120_000L, shard.getBroadcastValue());
	}

	@Test
	public void aNamedKillerIsStillADropSourceRatherThanAValue()
	{
		final LootEvent loot = LootChatParser.parse(
			ChatMessageType.GAMEMESSAGE,
			"<col=005f00>Hidung received a drop: Rune 2h sword</col> <col=106f10>(General Graardor)</col>");

		assertNotNull(loot);
		assertEquals("General Graardor", loot.getDropSource());
		assertEquals("no value was stated", LootEvent.UNKNOWN_VALUE, loot.getBroadcastValue());
	}

	@Test
	public void aSingleCoinValueIsRead()
	{
		final LootEvent loot = LootChatParser.parse(
			ChatMessageType.CLAN_GIM_MESSAGE, "Zezima received a drop: Bones (1 coin).");

		assertNotNull(loot);
		assertEquals("Bones", loot.getItemName());
		assertEquals(1L, loot.getBroadcastValue());
	}

	@Test
	public void keepsBracketsThatBelongToTheItemName()
	{
		// The killer is the last bracketed run, so a clue tier stays where it belongs and the
		// controller can rebuild the full name from the two halves.
		final LootEvent loot = LootChatParser.parse(
			ChatMessageType.GAMEMESSAGE, "Zezima received a drop: Clue scroll (hard) (General Graardor)");

		assertNotNull(loot);
		assertEquals("Clue scroll (hard)", loot.getItemName());
		assertEquals("General Graardor", loot.getDropSource());
	}

	@Test
	public void dropSourceIsAbsentWhenNoKillerIsNamed()
	{
		final LootEvent loot = LootChatParser.parse(
			ChatMessageType.GAMEMESSAGE, "Zezima received a drop: Twisted bow");

		assertNotNull(loot);
		assertEquals("Twisted bow", loot.getItemName());
		assertNull(loot.getDropSource());
	}

	@Test
	public void groupIronmanDropParsesWithoutTrailingPeriod()
	{
		final LootEvent loot = LootChatParser.parse(
			ChatMessageType.CLAN_GIM_MESSAGE, "Zezima received a drop: Twisted bow");

		assertNotNull(loot);
		assertEquals("Twisted bow", loot.getItemName());
	}

	@Test
	public void groupIronmanDropReadsQuantity()
	{
		final LootEvent loot = LootChatParser.parse(
			ChatMessageType.CLAN_GIM_MESSAGE, "Zezima received a drop: 1,200 x Coins.");

		assertNotNull(loot);
		assertEquals(1200, loot.getQuantity());
		assertEquals("Coins", loot.getItemName());
	}

	@Test
	public void stripsColourAndImageTagsBeforeMatching()
	{
		final LootEvent loot = LootChatParser.parse(
			ChatMessageType.CLAN_GIM_MESSAGE,
			"<img=2>Zezima received a drop: <col=ef1020>Twisted bow</col>.");

		assertNotNull(loot);
		assertEquals("Zezima", loot.getPlayerName());
		assertEquals("Twisted bow", loot.getItemName());
	}

	@Test
	public void parsesRaidSpecialLoot()
	{
		final LootEvent loot = LootChatParser.parse(
			ChatMessageType.GAMEMESSAGE, "Woox found something special: Scythe of vitur.");

		assertNotNull(loot);
		assertEquals("Woox", loot.getPlayerName());
		assertEquals("Scythe of vitur", loot.getItemName());
		assertEquals(LootSource.BOSS, loot.getSource());
		assertFalse("distinctive wording needs no raid gate", loot.isRequiresRaidContext());
	}

	@Test
	public void parsesGroupRaidBroadcast()
	{
		final LootEvent loot = LootChatParser.parse(
			ChatMessageType.CLAN_GIM_MESSAGE,
			"Zezima received special loot from a raid: Twisted buckler.");

		assertNotNull(loot);
		assertEquals("Twisted buckler", loot.getItemName());
		assertEquals(LootSource.GROUP_IRONMAN, loot.getSource());
	}

	@Test
	public void chambersPatternNeedsAColourTag()
	{
		assertNull("plain text of this shape is ordinary chat, not loot",
			LootChatParser.parse(ChatMessageType.GAMEMESSAGE, "Zezima - hello there"));
	}

	@Test
	public void chambersPatternMatchesWhenColoured()
	{
		final LootEvent loot = LootChatParser.parse(
			ChatMessageType.GAMEMESSAGE, "<col=ef20ff>Zezima - Twisted bow</col>");

		assertNotNull(loot);
		assertEquals("Zezima", loot.getPlayerName());
		assertEquals("Twisted bow", loot.getItemName());
		assertTrue("this shape is too loose to trust outside a raid", loot.isRequiresRaidContext());
	}

	@Test
	public void ignoresNonLootGroupMessages()
	{
		assertNull(LootChatParser.parse(ChatMessageType.CLAN_GIM_MESSAGE, "Zezima has left the group."));
		assertNull(LootChatParser.parse(ChatMessageType.CLAN_GIM_MESSAGE, "Zezima has logged in."));
	}

	@Test
	public void messageTypeGatesEachRule()
	{
		// The right words on the wrong channel are somebody talking, not a broadcast.
		assertNull(LootChatParser.parse(ChatMessageType.PUBLICCHAT, "Zezima received a drop: Twisted bow."));
		assertNull(LootChatParser.parse(ChatMessageType.PRIVATECHAT, "Woox found something special: Elidinis' ward."));
	}

	/**
	 * The clan channels can deliver the speaker in the event's own name field, leaving the message
	 * itself starting mid-sentence. Every pattern is anchored on the name, so the parser has to put it
	 * back before the line can be recognised at all.
	 */
	@Test
	public void reattachesASeparatelyDeliveredSpeakerName()
	{
		final LootEvent loot = LootChatParser.parse(
			ChatMessageType.CLAN_GIM_MESSAGE,
			"Fluid Juicey",
			"received a drop: 5 x Rune full helm (103,475 coins).");

		assertNotNull(loot);
		assertEquals("Fluid Juicey", loot.getPlayerName());
		assertEquals("Rune full helm", loot.getItemName());
		assertEquals(5, loot.getQuantity());
		assertEquals(103_475L, loot.getBroadcastValue());
	}

	@Test
	public void aNameAlreadyInTheMessageIsNotAddedTwice()
	{
		final LootEvent loot = LootChatParser.parse(
			ChatMessageType.CLAN_GIM_MESSAGE,
			"Fluid Juicey",
			"Fluid Juicey received a drop: 12 x Crystal shard (120,000 coins).");

		assertNotNull(loot);
		assertEquals("Fluid Juicey", loot.getPlayerName());
		assertEquals("Crystal shard", loot.getItemName());
	}

	@Test
	public void aNameIsNotBoltedOntoUnrelatedChat()
	{
		assertNull(LootChatParser.parse(
			ChatMessageType.CLAN_GIM_MESSAGE, "Fluid Juicey", "has left the group."));
	}

	@Test
	public void tagsAreStrippedFromASeparateName()
	{
		final LootEvent loot = LootChatParser.parse(
			ChatMessageType.CLAN_GIM_MESSAGE,
			"<img=2>Fluid Juicey",
			"received a drop: Crystal armour seed (5,758,363 coins).");

		assertNotNull(loot);
		assertEquals("Fluid Juicey", loot.getPlayerName());
		assertEquals(5_758_363L, loot.getBroadcastValue());
	}

	/**
	 * Captured from the client log. Jagex puts a non-breaking space inside a display name, which is
	 * indistinguishable from a plain one on screen and in the log - so a name pattern that accepts
	 * only the plain one fails for reasons nothing visible can explain. Every player whose name
	 * contains a space went unrecognised until this was folded.
	 */
	@Test
	public void aNonBreakingSpaceInTheNameStillMatches()
	{
		final LootEvent loot = LootChatParser.parse(
			ChatMessageType.CLAN_GIM_MESSAGE,
			"Fluid\u00A0Juicey received a drop: 70 x Dragon arrow (126,420 coins).");

		assertNotNull("a name with a non-breaking space must parse", loot);
		assertEquals("Fluid Juicey", loot.getPlayerName());
		assertEquals("Dragon arrow", loot.getItemName());
		assertEquals(70, loot.getQuantity());
		assertEquals(126_420L, loot.getBroadcastValue());
	}

	@Test
	public void aNonBreakingSpaceIsFoldedInASeparateNameToo()
	{
		final LootEvent loot = LootChatParser.parse(
			ChatMessageType.CLAN_GIM_MESSAGE,
			"Fluid\u00A0Juicey",
			"received a drop: 2 x Rune halberd (75,206 coins).");

		assertNotNull(loot);
		assertEquals("Fluid Juicey", loot.getPlayerName());
		assertEquals("Rune halberd", loot.getItemName());
	}

	@Test
	public void handlesMissingInput()
	{
		assertNull(LootChatParser.parse(null, "Zezima received a drop: Twisted bow."));
		assertNull(LootChatParser.parse(ChatMessageType.CLAN_GIM_MESSAGE, null));
		assertNull(LootChatParser.parse(ChatMessageType.CLAN_GIM_MESSAGE, ""));
	}

	@Test
	public void recognisesLootLookingLinesForLogging()
	{
		assertTrue(LootChatParser.looksLootRelated("Zezima received a mystery drop!"));
		assertTrue(LootChatParser.looksLootRelated("Someone found something"));
		assertTrue(LootChatParser.looksLootRelated("<col=ef20ff>special loot</col>"));
	}

	@Test
	public void ignoresOrdinaryChatForLogging()
	{
		assertFalse(LootChatParser.looksLootRelated("nice one mate"));
		assertFalse(LootChatParser.looksLootRelated(""));
		assertFalse(LootChatParser.looksLootRelated(null));
	}
}
