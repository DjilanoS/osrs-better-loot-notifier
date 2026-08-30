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

import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.grounditems.GroundItemsConfig;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Better Loot Notifier",
	description = "Shows boss, group and personal loot as small fading cards instead of easy-to-miss chat lines.",
	tags = {"loot", "boss", "group", "ironman", "raids", "drops", "broadcast"}
)
public class BetterLootNotifierPlugin extends Plugin
{
	/**
	 * How long two loot events describing the same kill are treated as one. The client can fire both
	 * {@link ServerNpcLoot} and {@link NpcLootReceived} for a single drop, moments apart.
	 */
	private static final long NPC_DEDUPE_MS = 1500L;

	/**
	 * A Group Ironman's own drop arrives twice: once as a loot event, once as the group broadcast.
	 * The broadcast trails the drop itself, so this window is wider.
	 */
	private static final long SELF_DEDUPE_MS = 3000L;

	@Inject
	private Client client;

	@Inject
	private BetterLootNotifierConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private LootCardOverlay overlay;

	@Inject
	private LootCardController controller;

	@Inject
	private HighlightSoundPlayer soundPlayer;

	private final RecentLootKeys npcLootKeys = new RecentLootKeys(NPC_DEDUPE_MS);
	private final RecentLootKeys selfLootKeys = new RecentLootKeys(SELF_DEDUPE_MS);

	@Override
	protected void startUp()
	{
		controller.rebuildFilter();
		overlayManager.add(overlay);
		log.debug("Better Loot Notifier started");
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		controller.clear();
		soundPlayer.dispose();
		npcLootKeys.clear();
		selfLootKeys.clear();
		log.debug("Better Loot Notifier stopped");
	}

	/**
	 * All animation happens here rather than in the overlay, so painting stays a pure read of state
	 * the client thread already settled.
	 */
	@Subscribe
	public void onBeforeRender(BeforeRender event)
	{
		controller.tick();
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		final ChatMessageType type = event.getType();
		final String raw = event.getMessage();

		if (config.debugLogUnmatched())
		{
			log.debug("Chat type={} sender={} name={} raw={}",
				type, event.getSender(), event.getName(), raw);
		}

		// The name is passed separately because the clan channels deliver it that way, leaving the
		// message itself starting mid-sentence.
		final LootEvent loot = LootChatParser.parse(type, event.getName(), raw);
		if (loot == null)
		{
			if (LootChatParser.looksLootRelated(raw))
			{
				// Always logged rather than sitting behind the debug toggle: a loot line this plugin
				// cannot read is the one thing worth knowing about, and a broadcast going quietly
				// unrecognised is exactly the failure that is hardest to notice. Tags are kept intact
				// because they are as much of the signal as the words, and the name and sender fields
				// are printed separately because chat does not always put them in the message.
				log.debug("Unmatched loot candidate type={} sender={} name={} raw={}",
					type, event.getSender(), event.getName(), raw);
			}
			return;
		}

		if (!isSourceEnabled(loot.getSource()))
		{
			return;
		}

		// The Chambers of Xeric pattern is a shape common enough in ordinary chat that it is only
		// believed while actually inside the raid.
		if (loot.isRequiresRaidContext() && client.getVarbitValue(VarbitID.RAIDS_CLIENT_INDUNGEON) != 1)
		{
			return;
		}

		final String player = Text.standardize(Text.removeTags(loot.getPlayerName()));
		final long now = System.currentTimeMillis();

		// A Group Ironman's own drop is announced to the group as well as landing in their inventory.
		// Whichever arrived first already made a card.
		if (isLocalPlayer(player)
			&& selfLootKeys.contains(selfKey(player, loot.getItemName(), loot.getQuantity()), now))
		{
			return;
		}

		controller.onLootEvent(loot, now);
	}

	@Subscribe
	public void onServerNpcLoot(ServerNpcLoot event)
	{
		handleOwnLoot(event.getItems());
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		handleOwnLoot(event.getItems());
	}

	@Subscribe
	public void onPlayerLootReceived(PlayerLootReceived event)
	{
		handleOwnLoot(event.getItems());
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		final GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING || state == GameState.LOGGING_IN)
		{
			controller.clear();
			npcLootKeys.clear();
			selfLootKeys.clear();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		// Ground Items is watched too: its hidden and highlighted lists feed this plugin's filter, so
		// editing them there has to take effect here without a restart.
		if (BetterLootNotifierConfig.GROUP.equals(event.getGroup())
			|| GroundItemsConfig.GROUP.equals(event.getGroup()))
		{
			controller.rebuildFilter();
		}
	}

	@Provides
	BetterLootNotifierConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BetterLootNotifierConfig.class);
	}

	private void handleOwnLoot(Collection<ItemStack> items)
	{
		if (!config.showOwnLoot() || items == null || items.isEmpty())
		{
			return;
		}

		final Map<Integer, Integer> totals = totalsByItem(items);

		final long now = System.currentTimeMillis();
		if (!npcLootKeys.offer(lootKey(totals), now))
		{
			return;
		}

		final String localName = localPlayerName();
		final String standardizedName = localName == null ? "" : Text.standardize(localName);

		for (final Map.Entry<Integer, Integer> entry : totals.entrySet())
		{
			final int quantity = entry.getValue();
			final LootEvent loot = new LootEvent(
				localName, null, entry.getKey(), quantity, LootSource.SELF);

			final String shownName = controller.onLootEvent(loot, now);
			if (shownName != null)
			{
				// Recorded under the same name and count the group broadcast will carry, so when that
				// broadcast arrives a moment later it can tell this drop has already been shown.
				selfLootKeys.offer(selfKey(standardizedName, shownName, quantity), now);
			}
		}
	}

	/**
	 * Sums a drop by item, because non-stackable loot arrives as one {@link ItemStack} per item.
	 * Three cards each reading "Chilli potato" say less than one reading "3 x Chilli potato" - and
	 * the game's own broadcast counts them the same way, which is what lets a player's own drop and
	 * its group broadcast recognise each other.
	 */
	private static Map<Integer, Integer> totalsByItem(Collection<ItemStack> items)
	{
		final Map<Integer, Integer> totals = new LinkedHashMap<>();
		for (final ItemStack item : items)
		{
			totals.merge(item.getId(), Math.max(1, item.getQuantity()), Integer::sum);
		}
		return totals;
	}

	/**
	 * Keyed on the summed items alone. Two things are deliberately excluded: the source NPC, because
	 * {@code NPC.getId()} and {@code NPCComposition.getId()} disagree for NPCs that transform, which
	 * would stop the two events matching in exactly the cases this dedupe exists for; and the
	 * original stack split, so that one event reporting three stacks of one still keys the same as
	 * another reporting a single stack of three.
	 */
	private static String lootKey(Map<Integer, Integer> totals)
	{
		final List<String> parts = new ArrayList<>(totals.size());
		for (final Map.Entry<Integer, Integer> entry : totals.entrySet())
		{
			parts.add(entry.getKey() + ":" + entry.getValue());
		}
		Collections.sort(parts);
		return "npc|" + String.join(",", parts);
	}

	private static String selfKey(String player, String itemName, int quantity)
	{
		return "self|" + player + "|" + Text.standardize(itemName == null ? "" : itemName) + "|" + quantity;
	}

	private boolean isSourceEnabled(LootSource source)
	{
		switch (source)
		{
			case GROUP_IRONMAN:
				return config.showGroupIronman();
			case BOSS:
				return config.showBossLoot();
			default:
				return config.showOwnLoot();
		}
	}

	private boolean isLocalPlayer(String standardizedName)
	{
		final String local = localPlayerName();
		return local != null && Text.standardize(local).equals(standardizedName);
	}

	private String localPlayerName()
	{
		final Player local = client.getLocalPlayer();
		return local == null ? null : local.getName();
	}
}
