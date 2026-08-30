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

import java.util.Objects;

/**
 * One piece of loot worth showing, normalised away from whichever event or chat line produced it.
 *
 * <p>Chat-sourced loot arrives with a name and no id ({@link #getItemId()} is {@link #UNKNOWN_ITEM_ID}),
 * because a broadcast only ever names the item. Loot from the client's own events arrives the other
 * way round. The controller fills in whichever half is missing.
 */
final class LootEvent
{
	static final int UNKNOWN_ITEM_ID = -1;

	/** No value was stated, so it has to be worked out from the item's own price. */
	static final long UNKNOWN_VALUE = -1L;

	private final String playerName;
	private final String itemName;
	private final int itemId;
	private final int quantity;
	private final LootSource source;
	private final boolean requiresRaidContext;
	private final String dropSource;
	private final long broadcastValue;

	LootEvent(String playerName, String itemName, int itemId, int quantity, LootSource source)
	{
		this(playerName, itemName, itemId, quantity, source, false, null, UNKNOWN_VALUE);
	}

	LootEvent(String playerName, String itemName, int itemId, int quantity, LootSource source,
		boolean requiresRaidContext, String dropSource, long broadcastValue)
	{
		this.playerName = playerName;
		this.itemName = itemName;
		this.itemId = itemId;
		this.quantity = Math.max(1, quantity);
		this.source = source;
		this.requiresRaidContext = requiresRaidContext;
		this.dropSource = dropSource;
		this.broadcastValue = broadcastValue;
	}

	String getPlayerName()
	{
		return playerName;
	}

	String getItemName()
	{
		return itemName;
	}

	int getItemId()
	{
		return itemId;
	}

	int getQuantity()
	{
		return quantity;
	}

	LootSource getSource()
	{
		return source;
	}

	/**
	 * True when the pattern that produced this is loose enough that it should only be believed while
	 * the player is actually inside a raid.
	 */
	boolean isRequiresRaidContext()
	{
		return requiresRaidContext;
	}

	/**
	 * The NPC the broadcast credited the drop to, when it named one. Null for loot that did not come
	 * from a broadcast.
	 *
	 * <p>Kept rather than discarded because the bracketed text is only <em>usually</em> the killer:
	 * for an item whose own name ends in brackets it may have been part of the name all along, and
	 * this is what lets that be recovered.
	 */
	String getDropSource()
	{
		return dropSource;
	}

	/**
	 * The whole stack's worth in coins as stated by the broadcast itself, or {@link #UNKNOWN_VALUE}
	 * when it named none.
	 *
	 * <p>Worth preferring over a locally derived price: it is what the game decided the drop was
	 * worth, it needs no name lookup, and it is the only figure available at all for untradeables,
	 * which have no Grand Exchange price to look up.
	 */
	long getBroadcastValue()
	{
		return broadcastValue;
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof LootEvent))
		{
			return false;
		}
		final LootEvent other = (LootEvent) o;
		return itemId == other.itemId
			&& quantity == other.quantity
			&& source == other.source
			&& Objects.equals(playerName, other.playerName)
			&& Objects.equals(itemName, other.itemName);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(playerName, itemName, itemId, quantity, source);
	}

	@Override
	public String toString()
	{
		return "LootEvent{" + playerName + ", " + quantity + " x " + itemName
			+ " (" + itemId + "), " + source + '}';
	}
}
