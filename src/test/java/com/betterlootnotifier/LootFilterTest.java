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

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LootFilterTest
{
	@Test
	public void minimumValueRejectsCheapLoot()
	{
		final LootFilter filter = LootFilter.of("", "", "", "", "", 50_000);

		assertFalse(filter.accept("Bones", 120));
		assertTrue(filter.accept("Twisted bow", 1_000_000_00));
	}

	@Test
	public void zeroMinimumAcceptsEverything()
	{
		final LootFilter filter = LootFilter.of("", "", "", "", "", 0);

		assertTrue(filter.accept("Bones", 0));
		assertTrue("an unpriced untradeable must still show when no threshold is set",
			filter.accept("Olmlet", 0));
	}

	@Test
	public void alwaysShowBypassesMinimumValue()
	{
		final LootFilter filter = LootFilter.of("Olmlet", "", "", "", "", 50_000);

		assertTrue(filter.accept("Olmlet", 0));
		assertFalse(filter.accept("Bones", 0));
	}

	@Test
	public void neverShowBeatsAlwaysShow()
	{
		final LootFilter filter = LootFilter.of("Bones", "Bones", "", "", "", 0);

		assertFalse("the deny list is the last word", filter.accept("Bones", 999_999));
	}

	@Test
	public void neverShowBeatsMinimumValue()
	{
		final LootFilter filter = LootFilter.of("", "Twisted bow", "", "", "", 0);

		assertFalse(filter.accept("Twisted bow", 1_000_000_00));
	}

	@Test
	public void listsIgnoreCaseAndSurroundingSpace()
	{
		final LootFilter filter = LootFilter.of(" twisted BOW , scythe of vitur ", "", "", "", "", 50_000);

		assertTrue(filter.accept("Twisted bow", 0));
		assertTrue(filter.accept("Scythe of vitur", 0));
		assertFalse(filter.accept("Bandos chestplate", 0));
	}

	@Test
	public void emptyAndNullListsBehaveAsNoList()
	{
		final LootFilter filter = LootFilter.of(null, "   ", "", "", "", 100);

		assertTrue(filter.accept("Twisted bow", 100));
		assertFalse(filter.accept("Bones", 99));
	}

	@Test
	public void unnamedLootIsJudgedOnValueAlone()
	{
		final LootFilter filter = LootFilter.of("Twisted bow", "Twisted bow", "", "", "", 100);

		// A name neither list can match must not accidentally pick up either verdict.
		assertTrue(filter.accept(null, 100));
		assertFalse(filter.accept(null, 99));
	}

	@Test
	public void groundItemsHiddenListSuppressesLoot()
	{
		final LootFilter filter = LootFilter.of("", "", "", "", "Vial, Ashes, Coins, Bones", 0);

		assertFalse(filter.accept("Coins", 5_000));
		assertFalse(filter.accept("Bones", 500));
		assertTrue(filter.accept("Twisted bow", 0));
	}

	@Test
	public void alwaysShowOverridesGroundItemsHidden()
	{
		// The case that makes this an addition rather than a copy: Coins are noise on the floor but
		// still worth a card when they drop.
		final LootFilter filter = LootFilter.of("Coins", "", "", "", "Vial, Ashes, Coins, Bones", 0);

		assertTrue(filter.accept("Coins", 5_000));
		assertFalse("everything else Ground Items hides stays hidden", filter.accept("Bones", 500));
	}

	@Test
	public void neverShowOverridesGroundItemsHighlighted()
	{
		final LootFilter filter = LootFilter.of("", "Coins", "", "Coins", "", 0);

		assertFalse(filter.accept("Coins", 5_000));
	}

	@Test
	public void groundItemsHighlightedBypassesMinimumValue()
	{
		final LootFilter filter = LootFilter.of("", "", "", "Olmlet", "", 50_000);

		assertTrue(filter.accept("Olmlet", 0));
		assertFalse(filter.accept("Bones", 0));
	}

	@Test
	public void highlightingOutranksGroundItemsHidden()
	{
		// Highlighted items now get a louder card and an alert, so asking for that is a stronger
		// statement than Ground Items being told to keep the floor tidy.
		final LootFilter filter = LootFilter.of("", "", "", "Coins", "Coins", 0);

		assertTrue(filter.accept("Coins", 5_000));
		assertTrue(filter.isHighlighted("Coins"));
	}

	@Test
	public void highlightedItemsAreFlagged()
	{
		final LootFilter filter = LootFilter.of("", "", "Twisted bow, Scythe of vitur", "", "", 0);

		assertTrue(filter.isHighlighted("Twisted bow"));
		assertTrue(filter.isHighlighted("Scythe of vitur"));
		assertFalse(filter.isHighlighted("Bones"));
		assertFalse(filter.isHighlighted(null));
	}

	@Test
	public void highlightingBypassesMinimumValue()
	{
		final LootFilter filter = LootFilter.of("", "", "Olmlet", "", "", 50_000);

		assertTrue("flagging an item as worth an alert implies wanting to see it at all",
			filter.accept("Olmlet", 0));
		assertTrue(filter.isHighlighted("Olmlet"));
	}

	@Test
	public void neverShowStopsAnItemBeingHighlighted()
	{
		// An item that never reaches a card cannot be a highlighted one, so it must not trigger the
		// alert either.
		final LootFilter filter = LootFilter.of("", "Twisted bow", "Twisted bow", "", "", 0);

		assertFalse(filter.accept("Twisted bow", 1_000_000_00));
		assertFalse(filter.isHighlighted("Twisted bow"));
	}

	@Test
	public void groundItemsHighlightsCountAsHighlights()
	{
		final LootFilter filter = LootFilter.of("", "", "", "Dragon warhammer", "", 0);

		assertTrue(filter.isHighlighted("Dragon warhammer"));
	}

	@Test
	public void highlightMatchingSupportsWildcards()
	{
		final LootFilter filter = LootFilter.of("", "", "*bow*", "", "", 0);

		assertTrue(filter.isHighlighted("Twisted bow"));
		assertTrue(filter.isHighlighted("Bow of faerdhinen"));
		assertFalse(filter.isHighlighted("Bones"));
	}

	@Test
	public void supportsWildcardEntries()
	{
		// Ground Items reads these lists with wildcards, so pasting one across must behave the same.
		final LootFilter filter = LootFilter.of("", "", "", "", "*rune*", 0);

		assertFalse(filter.accept("Rune 2h sword", 30_000));
		assertFalse(filter.accept("Air rune", 100));
		assertTrue(filter.accept("Twisted bow", 0));
	}

	@Test
	public void wildcardMatchingIsCaseInsensitive()
	{
		final LootFilter filter = LootFilter.of("dragon *", "", "", "", "", 50_000);

		assertTrue(filter.accept("Dragon warhammer", 0));
		assertFalse(filter.accept("Rune warhammer", 0));
	}

	@Test
	public void ignoringGroundItemsListsIsJustEmptyStrings()
	{
		// How the controller expresses "Use Ground Items lists" being off.
		final LootFilter filter = LootFilter.of("", "", "", "", "", 0);

		assertTrue(filter.accept("Coins", 0));
		assertTrue(filter.accept("Bones", 0));
	}
}
