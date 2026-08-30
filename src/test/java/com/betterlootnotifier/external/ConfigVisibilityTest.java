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
package com.betterlootnotifier.external;

import com.betterlootnotifier.BetterLootNotifierConfig;
import com.betterlootnotifier.HighlightSound;
import java.awt.Color;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Guards the config surface against the one mistake that cannot be caught inside its own package.
 *
 * <p>RuneLite implements {@link BetterLootNotifierConfig} with a generated proxy that lives in a
 * different package, so every type named in a config method signature has to be public. A
 * package-private one compiles perfectly well and then throws {@link IllegalAccessError} the first
 * time the setting is read in-game - which is how {@code HighlightSound} shipped broken once.
 *
 * <p>This class sits outside {@code com.betterlootnotifier} deliberately: it sees the config exactly as
 * the proxy does, so a type losing {@code public} breaks the build rather than the game. The
 * assertions below matter far less than the fact that this file compiles at all.
 */
public class ConfigVisibilityTest
{
	/** Stands in for the generated proxy: only legal if the interface and its types are public. */
	private static final BetterLootNotifierConfig CONFIG = new BetterLootNotifierConfig()
	{
	};

	@Test
	public void enumValuedSettingsAreReachableFromOutsideThePackage()
	{
		final HighlightSound sound = CONFIG.highlightSound();

		assertEquals("the arcade blip is the default alert", HighlightSound.ARCADE, sound);
		assertNotNull(HighlightSound.valueOf("CHIME"));
	}

	@Test
	public void remainingSettingTypesAreReachableToo()
	{
		final String highlights = CONFIG.highlightItems();

		assertNotNull(highlights);
		assertEquals(70, CONFIG.highlightSoundVolume());
		assertTrue("the name sits above the item by default", CONFIG.nameAboveItem());
		assertTrue("the value is shown by default", CONFIG.showValue());
		assertEquals(250, CONFIG.cardWidth());
		assertEquals(4, CONFIG.displaySeconds());
		assertFalse("Ground Items lists are opt-in", CONFIG.useGroundItemsLists());
		assertFalse("the preview card is off until asked for", CONFIG.previewCard());
		assertEquals("", CONFIG.alwaysShow());
		assertEquals("", CONFIG.neverShow());
		assertEquals("", CONFIG.highlightItems());
	}

	/**
	 * Pinned because these were chosen against a live card rather than derived, and RuneLite writes
	 * them as {@code AARRGGBB} - transcribing that as {@code RRGGBBAA} silently yields a plausible
	 * but wrong colour, which is easy to miss and hard to spot later.
	 */
	@Test
	public void highlightColoursMatchTheChosenValues()
	{
		final Color background = CONFIG.highlightBackgroundColor();
		final Color border = CONFIG.highlightBorderColor();

		assertEquals("#9CC5891D", 0x9CC5891D, background.getRGB());
		assertEquals("#C7FFB75B", 0xC7FFB75B, border.getRGB());
		assertEquals("#FF4B9CFF", 0xFF4B9CFF, CONFIG.groupAccentColor().getRGB());
	}
}
