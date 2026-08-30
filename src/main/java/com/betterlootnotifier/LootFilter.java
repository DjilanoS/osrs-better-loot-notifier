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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.runelite.client.util.Text;
import net.runelite.client.util.WildcardMatcher;

/**
 * Decides whether a piece of loot earns a card.
 *
 * <p>Four lists are consulted in a fixed order of authority, so that a more specific instruction
 * always beats a more general one:
 *
 * <ol>
 *   <li>this plugin's <em>Never show</em> - the last word, nothing overrides it</li>
 *   <li>this plugin's <em>Always show</em> - beats anything Ground Items says</li>
 *   <li>the <em>highlight</em> list - flagging an item as worth shouting about implies wanting to
 *       see it at all, so this admits it regardless of value</li>
 *   <li>Ground Items' <em>Hidden items</em></li>
 *   <li>failing all of those, the minimum value</li>
 * </ol>
 *
 * <p>Borrowing the Ground Items lists means the items already declared uninteresting on the floor
 * are uninteresting here too, without setting them up twice. Ordering this plugin's lists above them
 * is what makes it an addition rather than a copy: hiding Coins on the ground while still wanting a
 * card for them is a coherent thing to ask for, so "Always show: Coins" wins.
 *
 * <p>Entries may use {@code *} wildcards, matching how Ground Items reads the same text.
 *
 * <p>Built once per config change rather than per loot event, because parsing four lists on every
 * drop would be wasted work in exactly the moments the plugin is busiest.
 */
final class LootFilter
{
	private final NameList neverShow;
	private final NameList alwaysShow;
	private final NameList highlight;
	private final NameList groundHidden;
	private final int minValue;

	private LootFilter(NameList neverShow, NameList alwaysShow, NameList highlight,
		NameList groundHidden, int minValue)
	{
		this.neverShow = neverShow;
		this.alwaysShow = alwaysShow;
		this.highlight = highlight;
		this.groundHidden = groundHidden;
		this.minValue = minValue;
	}

	/**
	 * @param highlightCsv         this plugin's highlight list
	 * @param groundHighlightedCsv Ground Items' highlighted list, or empty to ignore it. Merged into
	 *                             the highlight list, since an item flagged as worth noticing on the
	 *                             floor is worth noticing on a card too
	 * @param groundHiddenCsv      Ground Items' hidden list, or empty to ignore it
	 */
	static LootFilter of(String alwaysShowCsv, String neverShowCsv, String highlightCsv,
		String groundHighlightedCsv, String groundHiddenCsv, int minValue)
	{
		return new LootFilter(
			NameList.of(neverShowCsv),
			NameList.of(alwaysShowCsv),
			NameList.of(highlightCsv, groundHighlightedCsv),
			NameList.of(groundHiddenCsv),
			Math.max(0, minValue));
	}

	/**
	 * @param itemName   the item's display name; a null or blank name can only pass on value
	 * @param totalValue the whole stack's value in coins, or 0 when it could not be priced
	 */
	boolean accept(String itemName, int totalValue)
	{
		if (itemName != null && !itemName.trim().isEmpty())
		{
			final String name = itemName.trim();

			if (neverShow.matches(name))
			{
				return false;
			}
			if (alwaysShow.matches(name) || highlight.matches(name))
			{
				return true;
			}
			if (groundHidden.matches(name))
			{
				return false;
			}
		}

		return totalValue >= minValue;
	}

	/**
	 * Whether this item should be called out - a stronger card, and an alert if one is configured.
	 *
	 * <p>Only meaningful for loot that {@link #accept} already let through; an item on both the
	 * highlight list and <em>Never show</em> never reaches a card to be highlighted.
	 */
	boolean isHighlighted(String itemName)
	{
		if (itemName == null || itemName.trim().isEmpty())
		{
			return false;
		}

		final String name = itemName.trim();
		return !neverShow.matches(name) && highlight.matches(name);
	}

	/**
	 * A parsed comma-separated name list. Plain entries are matched by an exact lowercase lookup and
	 * only the entries actually containing a wildcard pay for pattern matching, which keeps the
	 * common case a set probe.
	 */
	private static final class NameList
	{
		private static final NameList EMPTY = new NameList(Collections.emptySet(), Collections.emptyList());

		private final Set<String> exact;
		private final List<String> wildcards;

		private NameList(Set<String> exact, List<String> wildcards)
		{
			this.exact = exact;
			this.wildcards = wildcards;
		}

		/** Merges any number of comma-separated lists into one. */
		private static NameList of(String... csvs)
		{
			final Set<String> exact = new HashSet<>();
			final List<String> wildcards = new ArrayList<>();

			for (final String csv : csvs)
			{
				if (csv == null || csv.trim().isEmpty())
				{
					continue;
				}

				for (final String entry : Text.fromCSV(csv))
				{
					final String trimmed = entry.trim();
					if (trimmed.isEmpty())
					{
						continue;
					}

					if (trimmed.indexOf('*') >= 0)
					{
						wildcards.add(trimmed);
					}
					else
					{
						exact.add(trimmed.toLowerCase(Locale.ENGLISH));
					}
				}
			}

			return exact.isEmpty() && wildcards.isEmpty() ? EMPTY : new NameList(exact, wildcards);
		}

		private boolean matches(String name)
		{
			if (exact.contains(name.toLowerCase(Locale.ENGLISH)))
			{
				return true;
			}

			for (final String pattern : wildcards)
			{
				// WildcardMatcher is already case-insensitive, so the raw name goes in.
				if (WildcardMatcher.matches(pattern, name))
				{
					return true;
				}
			}

			return false;
		}
	}
}
