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

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.KeyCode;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.grounditems.GroundItemsConfig;
import net.runelite.client.util.QuantityFormatter;

/**
 * Owns the card stack: what is on it, and where each card is in its entry, life and fade.
 *
 * <p>Everything here runs on the client thread - loot arrives from event subscriptions, and
 * {@link #tick()} is driven from {@code BeforeRender}. The overlay only ever reads
 * {@link #getCards()}, which is republished as an immutable snapshot each frame, so painting never
 * races a list being mutated underneath it.
 */
@Slf4j
@Singleton
class LootCardController
{
	/**
	 * Height of a card whose text fits on one line. Cards measure themselves once drawn - a wrapped
	 * item name can make one taller - so this is the floor and the assumption until then.
	 */
	static final int MIN_CARD_HEIGHT = 40;

	/** Vertical gap between stacked cards. */
	static final int CARD_GAP = 3;

	/** The cell an item sprite is centred in. Matches the native size of a RuneLite item image. */
	static final int ICON_WIDTH = 36;
	static final int ICON_HEIGHT = 32;

	/** How far a new card starts to the left of its resting place when sliding in. */
	static final float SLIDE_PX = 12f;

	static final int FADE_IN_MS = 160;

	/**
	 * Time constant of the position easing. At ~0.07s a card covers most of the distance in about a
	 * fifth of a second: quick enough not to lag the eye, slow enough to read as movement.
	 */
	private static final float TAU_SECONDS = 0.07f;

	/**
	 * Longest frame the animation will honour. Past this - an alt-tab, a loading screen - the stack
	 * would otherwise jump straight to its target, which reads as a glitch rather than a settle.
	 */
	private static final float MAX_FRAME_SECONDS = 0.1f;

	/** Shortest gap between two alerts. One kill dropping several highlights should chime once. */
	private static final long ALERT_COOLDOWN_MS = 400L;

	private final Client client;
	private final BetterLootNotifierConfig config;
	private final ItemResolver itemResolver;
	private final ConfigManager configManager;
	private final HighlightSoundPlayer soundPlayer;

	private final List<LootCard> cards = new ArrayList<>();

	private volatile List<LootCard> published = Collections.emptyList();
	private volatile LootCard publishedPreview;
	private volatile float stackHeight;

	private LootFilter filter;
	private long lastFrameMs;
	private long lastAlertMs;

	private LootCard previewCard;

	@Inject
	LootCardController(Client client, BetterLootNotifierConfig config, ItemResolver itemResolver,
		ConfigManager configManager, HighlightSoundPlayer soundPlayer)
	{
		this.client = client;
		this.config = config;
		this.itemResolver = itemResolver;
		this.configManager = configManager;
		this.soundPlayer = soundPlayer;
		rebuildFilter();
	}

	void rebuildFilter()
	{
		String groundHighlighted = "";
		String groundHidden = "";

		if (config.useGroundItemsLists())
		{
			// Read through the config proxy rather than by raw key, so that a user who has never
			// opened Ground Items still gets its stock hidden list rather than an empty one.
			final GroundItemsConfig groundItems = configManager.getConfig(GroundItemsConfig.class);
			if (groundItems != null)
			{
				groundHighlighted = groundItems.getHighlightItems();
				groundHidden = groundItems.getHiddenItems();
			}
		}

		filter = LootFilter.of(config.alwaysShow(), config.neverShow(), config.highlightItems(),
			groundHighlighted, groundHidden, config.minValue());
	}

	/**
	 * Turns a loot event into a card, unless the filters reject it.
	 *
	 * @return the item's resolved display name when a card was added, or null when it was filtered
	 *         out. Callers use the name to record what was shown, since resolving it is this class's
	 *         job and re-deriving it elsewhere would risk the two disagreeing.
	 */
	String onLootEvent(LootEvent event, long nowMs)
	{
		int itemId = event.getItemId();
		if (itemId == LootEvent.UNKNOWN_ITEM_ID)
		{
			itemId = itemResolver.idForName(event.getItemName());

			if (itemId == LootEvent.UNKNOWN_ITEM_ID && event.getDropSource() != null)
			{
				// The bracketed text was not the killer after all - it was part of the item's own
				// name, as in "Clue scroll (hard)". Put it back and try again.
				itemId = itemResolver.idForName(
					event.getItemName() + " (" + event.getDropSource() + ")");
			}
		}

		// Prefer the game's own name over whatever the broadcast called it, so the card and the
		// filter lists agree on spelling.
		String itemName = itemResolver.nameForId(itemId);
		if (itemName == null || itemName.isEmpty())
		{
			itemName = event.getItemName();
		}

		if (itemName == null || itemName.isEmpty())
		{
			// Nothing to put on the card, and a nameless one would only be a coloured rectangle.
			return null;
		}

		final int quantity = event.getQuantity();

		// A broadcast states what the drop was worth, and that figure beats anything derivable here:
		// it needs no name lookup, and untradeables have no Grand Exchange price to look up at all -
		// which previously valued every one of them at zero and hid them behind the minimum value.
		final long stated = event.getBroadcastValue();
		final long totalValue = stated != LootEvent.UNKNOWN_VALUE
			? stated
			: (long) itemResolver.priceFor(itemId) * quantity;

		if (!filter.accept(itemName, (int) Math.min(Integer.MAX_VALUE, totalValue)))
		{
			log.debug("Filtered out {} x {} worth {} (minimum {})",
				quantity, itemName, totalValue, config.minValue());
			return null;
		}

		final String label = quantity > 1
			? QuantityFormatter.quantityToRSDecimalStack(quantity) + " x " + itemName
			: itemName;

		// Abbreviated the way the game abbreviates stack sizes - 4K, 2.2M, 1B - so the number can be
		// taken in at a glance rather than read digit by digit.
		final String valueText = config.showValue() && totalValue > 0
			? "GE: " + QuantityFormatter.quantityToRSDecimalStack(
				(int) Math.min(Integer.MAX_VALUE, totalValue))
			: null;

		final BufferedImage icon = config.showIcon() ? itemResolver.imageFor(itemId, quantity) : null;
		final boolean highlighted = filter.isHighlighted(itemName);

		final LootCard card = new LootCard(
			event.getPlayerName(),
			label,
			valueText,
			icon,
			event.getSource(),
			highlighted,
			totalValue,
			nowMs,
			nowMs + (config.displaySeconds() * 1000L),
			config.slideIn() ? -SLIDE_PX : 0f);

		// Measured here rather than per frame. ItemManager renders synchronously when called from the
		// client thread, as this always is, so the sprite's pixels are already there to measure.
		final int[] offsets = ItemResolver.centringOffsets(icon, ICON_WIDTH, ICON_HEIGHT);
		card.setIconOffsets(offsets[0], offsets[1]);

		// The stack is ranked rather than chronological, so where this card lands depends on what is
		// already there. Sorting the whole list is trivial at these sizes and keeps one rule in one
		// place; the easing then carries any displaced card to its new slot.
		cards.add(card);
		cards.sort(LootCard.BY_IMPORTANCE);

		// Placed at its resting position rather than sliding down from the top of the stack, which
		// would otherwise read as the card being demoted the instant it appeared.
		card.setRenderY(targetYOf(card));

		enforceCap(nowMs);

		if (highlighted)
		{
			alert(nowMs);
		}

		// Logged alongside the rejection above so the two together account for every loot event: a run
		// where neither line appears means the event never reached here at all, which points at the
		// parser or the source toggles rather than the filters.
		log.debug("Carded {} x {} worth {} from {}{}",
			quantity, itemName, totalValue, event.getSource(), highlighted ? " (highlighted)" : "");

		return itemName;
	}

	/**
	 * Plays the highlight alert, at most once per burst.
	 *
	 * <p>A single kill can drop several highlighted items at once, and firing the sample once per
	 * item turns a chime into a clatter. One alert says the same thing.
	 */
	private void alert(long nowMs)
	{
		if (nowMs - lastAlertMs < ALERT_COOLDOWN_MS)
		{
			return;
		}

		lastAlertMs = nowMs;
		soundPlayer.play(config.highlightSound(), config.highlightSoundVolume());
	}

	/** Advances every animation and republishes the snapshot the overlay paints from. */
	void tick()
	{
		final long now = System.currentTimeMillis();
		final float dt = frameSeconds(now);
		lastFrameMs = now;

		cards.removeIf(card -> card.isExpired(now));

		// Cards are not all the same height - a wrapped item name makes one taller - so each target
		// is the running total of everything above it rather than a fixed pitch.
		float height = 0f;
		float target = 0f;
		for (final LootCard card : cards)
		{
			card.setRenderY(LootCard.ease(card.getRenderY(), target, dt, TAU_SECONDS));
			card.setSlideX(LootCard.ease(card.getSlideX(), 0f, dt, TAU_SECONDS));

			height = Math.max(height, card.getRenderY() + card.getHeight());
			target += card.getHeight() + CARD_GAP;
		}

		updatePreview(now);

		// The preview only stands in for an empty stack; once real loot arrives it gets out of the way.
		final LootCard preview = cards.isEmpty() ? previewCard : null;
		if (preview != null)
		{
			height = preview.getHeight();
		}

		stackHeight = height;
		publishedPreview = preview;
		published = cards.isEmpty()
			? Collections.emptyList()
			: Collections.unmodifiableList(new ArrayList<>(cards));
	}

	/**
	 * The cards to paint, newest first. Never null, and safe to iterate while the client thread
	 * carries on mutating the live list.
	 */
	List<LootCard> getCards()
	{
		return published;
	}

	/**
	 * A stand-in card shown when the stack would otherwise be empty, so it still has bounds to grab.
	 * Null when no preview is wanted.
	 */
	LootCard getPreviewCard()
	{
		return publishedPreview;
	}

	float getStackHeight()
	{
		return stackHeight;
	}

	void clear()
	{
		cards.clear();
		previewCard = null;
		publishedPreview = null;
		published = Collections.emptyList();
		stackHeight = 0f;
		lastFrameMs = 0L;
	}

	private float frameSeconds(long now)
	{
		if (lastFrameMs == 0L)
		{
			return 0f;
		}

		final float dt = (now - lastFrameMs) / 1000f;
		return dt < 0f ? 0f : Math.min(dt, MAX_FRAME_SECONDS);
	}

	/** Where a card sits once everything above it is accounted for. */
	private float targetYOf(LootCard card)
	{
		float y = 0f;
		for (final LootCard other : cards)
		{
			if (other == card)
			{
				break;
			}
			y += other.getHeight() + CARD_GAP;
		}
		return y;
	}

	/**
	 * Keeps the stack within the configured cap by bringing the expiry of the cards past it forward,
	 * rather than dropping them outright, so they fade like every other card instead of vanishing.
	 *
	 * <p>Since the list is ranked, the cards past the cap are the least interesting ones - so a cheap
	 * drop arriving during a busy kill no longer pushes a valuable one off the bottom.
	 */
	private void enforceCap(long nowMs)
	{
		final int max = Math.max(1, config.maxCards());
		final long fadeOutAt = nowMs + Math.max(1, config.fadeMillis());

		for (int i = max; i < cards.size(); i++)
		{
			final LootCard card = cards.get(i);
			card.setExpireAtMs(Math.min(card.getExpireAtMs(), fadeOutAt));
		}
	}

	private void updatePreview(long nowMs)
	{
		final boolean wanted = config.previewCard()
			|| (config.previewOnAlt() && client.isKeyPressed(KeyCode.KC_ALT));

		if (!wanted)
		{
			previewCard = null;
			return;
		}

		// Rebuilt whenever it would otherwise expire, so the sample sits at full opacity for as long
		// as it is wanted rather than fading out under the user mid-drag.
		if (previewCard == null || previewCard.getExpireAtMs() - nowMs < 1000L)
		{
			previewCard = new LootCard(
				"Preview",
				"Drag with Alt held",
				null,
				null,
				LootSource.SELF,
				nowMs - FADE_IN_MS,
				nowMs + 60_000L,
				0f);
		}
	}
}
