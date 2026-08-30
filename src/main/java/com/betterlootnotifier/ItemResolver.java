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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.http.api.item.ItemPrice;

/**
 * Looks up the id, price and sprite behind an item.
 *
 * <p>Loot from the client's own events already carries an id; loot read out of a chat broadcast
 * only carries a name, so it has to be resolved backwards. {@link ItemManager#search(String)} covers
 * that, but only over the Grand Exchange price map - untradeables are invisible to it, which is why
 * the raid pets are named explicitly below.
 *
 * <p>Every method must be called on the client thread.
 */
@Singleton
class ItemResolver
{
	/**
	 * Untradeable drops worth a card that {@link ItemManager#search(String)} cannot find, because
	 * they never appear on the Grand Exchange. Keyed on the lowercased item name.
	 */
	private static final Map<String, Integer> UNTRADEABLE_BY_NAME = new HashMap<>();

	static
	{
		UNTRADEABLE_BY_NAME.put("olmlet", ItemID.OLMPET);
		UNTRADEABLE_BY_NAME.put("lil' zik", ItemID.VERZIKPET);
		UNTRADEABLE_BY_NAME.put("tumeken's guardian", ItemID.WARDENPET_TUMEKEN);
	}

	private final ItemManager itemManager;

	/** Resolved ids, misses included, so a name that cannot be resolved is only searched once. */
	private final Map<String, Integer> idByName = new HashMap<>();

	@Inject
	ItemResolver(ItemManager itemManager)
	{
		this.itemManager = itemManager;
	}

	/**
	 * @return the item's id, or {@link LootEvent#UNKNOWN_ITEM_ID} when the name is not recognised
	 */
	int idForName(String name)
	{
		if (name == null || name.trim().isEmpty())
		{
			return LootEvent.UNKNOWN_ITEM_ID;
		}

		final String key = name.trim().toLowerCase(Locale.ENGLISH);
		final Integer cached = idByName.get(key);
		if (cached != null)
		{
			return cached;
		}

		int resolved = LootEvent.UNKNOWN_ITEM_ID;

		final Integer untradeable = UNTRADEABLE_BY_NAME.get(key);
		if (untradeable != null)
		{
			resolved = untradeable;
		}
		else
		{
			// search() is a substring match, so an exact-name pass is needed to avoid resolving
			// "Dragon axe" to "Dragon axe head" or similar.
			for (final ItemPrice candidate : itemManager.search(name.trim()))
			{
				if (candidate.getName() != null && candidate.getName().equalsIgnoreCase(name.trim()))
				{
					resolved = candidate.getId();
					break;
				}
			}
		}

		idByName.put(key, resolved);
		return resolved;
	}

	String nameForId(int itemId)
	{
		if (itemId == LootEvent.UNKNOWN_ITEM_ID)
		{
			return null;
		}

		final ItemComposition composition = itemManager.getItemComposition(itemId);
		return composition == null ? null : composition.getName();
	}

	/**
	 * @return the Grand Exchange price of one of the item, or 0 when it has none (every untradeable)
	 */
	int priceFor(int itemId)
	{
		if (itemId == LootEvent.UNKNOWN_ITEM_ID)
		{
			return 0;
		}

		return Math.max(0, itemManager.getItemPrice(itemManager.canonicalize(itemId)));
	}

	/**
	 * The item's sprite. The quantity still picks the right pile for coins and the like, but the
	 * stack number is suppressed: it is already on the card as text, and would be unreadable burned
	 * into a 32px sprite.
	 *
	 * <p>The returned image fills itself in asynchronously, so holding the reference is enough - it
	 * simply appears on a later frame.
	 */
	BufferedImage imageFor(int itemId, int quantity)
	{
		if (itemId == LootEvent.UNKNOWN_ITEM_ID)
		{
			return null;
		}

		return itemManager.getImage(itemId, quantity, false);
	}

	/**
	 * Where to draw a sprite so its visible pixels sit in the middle of a cell of the given size.
	 *
	 * <p>Item sprites are a fixed 36x32 canvas, but the item itself is drawn wherever its model
	 * happens to land inside that canvas - Bones, for instance, sits left of centre with dead space
	 * to its right. Blitting at the cell's corner therefore pushes every item off-centre by a
	 * different amount. Measuring the opaque bounds and centring those instead makes the column look
	 * even for every item, and keeps the text column in the same place from card to card, which
	 * trimming the sprite to its content would not.
	 *
	 * @return the x and y offset to add to the cell's corner; zeroes when the image is missing or
	 *         has not been rendered yet
	 */
	static int[] centringOffsets(BufferedImage image, int cellWidth, int cellHeight)
	{
		if (image == null)
		{
			return new int[]{0, 0};
		}

		final int width = image.getWidth();
		final int height = image.getHeight();

		int minX = width;
		int minY = height;
		int maxX = -1;
		int maxY = -1;

		for (int y = 0; y < height; y++)
		{
			for (int x = 0; x < width; x++)
			{
				if ((image.getRGB(x, y) >>> 24) == 0)
				{
					continue;
				}

				if (x < minX)
				{
					minX = x;
				}
				if (x > maxX)
				{
					maxX = x;
				}
				if (y < minY)
				{
					minY = y;
				}
				if (y > maxY)
				{
					maxY = y;
				}
			}
		}

		if (maxX < 0)
		{
			// Fully transparent, so there is nothing to centre on.
			return new int[]{0, 0};
		}

		return new int[]{
			((cellWidth - (maxX - minX + 1)) / 2) - minX,
			((cellHeight - (maxY - minY + 1)) / 2) - minY
		};
	}
}
