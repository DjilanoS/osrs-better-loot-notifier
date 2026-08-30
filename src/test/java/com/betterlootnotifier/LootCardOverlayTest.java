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

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Covers the word wrap alone. It is a pure function over {@link FontMetrics}, which a monospaced
 * font off an offscreen image supplies without a client or a display - one character is exactly one
 * unit wide, so the expected wrap points can be worked out by counting letters.
 */
public class LootCardOverlayTest
{
	private static FontMetrics metrics;
	private static int charWidth;

	@BeforeClass
	public static void setUpMetrics()
	{
		final BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D graphics = image.createGraphics();
		metrics = graphics.getFontMetrics(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		graphics.dispose();

		charWidth = metrics.stringWidth("x");
		assertTrue("monospaced font expected", charWidth > 0);
	}

	private static int columns(int n)
	{
		return charWidth * n;
	}

	@Test
	public void shortTextStaysOnOneLine()
	{
		final List<String> lines = LootCardOverlay.wrap("Twisted bow", metrics, columns(20), 2);

		assertEquals(1, lines.size());
		assertEquals("Twisted bow", lines.get(0));
	}

	@Test
	public void wrapsOntoASecondLineWhenTheFirstIsFull()
	{
		// "Ancient" is 7 characters, so 10 columns fits it but not "Ancient ceremonial".
		final List<String> lines = LootCardOverlay.wrap("Ancient ceremonial legs", metrics, columns(10), 2);

		assertEquals(2, lines.size());
		assertEquals("Ancient", lines.get(0));
		assertEquals("ceremonial legs", lines.get(1));
	}

	@Test
	public void neverTruncates()
	{
		// Everything that will not fit is kept on the last permitted line rather than being cut.
		final List<String> lines = LootCardOverlay.wrap("one two three four five six", metrics, columns(7), 2);

		assertEquals(2, lines.size());

		final StringBuilder rejoined = new StringBuilder();
		for (final String line : lines)
		{
			if (rejoined.length() > 0)
			{
				rejoined.append(' ');
			}
			rejoined.append(line);
		}
		assertEquals("no word may be dropped", "one two three four five six", rejoined.toString());
	}

	@Test
	public void aSingleOverlongWordIsKeptWhole()
	{
		final List<String> lines = LootCardOverlay.wrap("Supercalifragilistic", metrics, columns(3), 2);

		assertEquals(1, lines.size());
		assertEquals("an unbreakable word runs on rather than being shortened",
			"Supercalifragilistic", lines.get(0));
	}

	@Test
	public void handlesMissingText()
	{
		assertTrue(LootCardOverlay.wrap(null, metrics, columns(10), 2).isEmpty());
		assertTrue(LootCardOverlay.wrap("", metrics, columns(10), 2).isEmpty());
	}

	@Test
	public void collapsesRepeatedSpaces()
	{
		final List<String> lines = LootCardOverlay.wrap("3 x  Chilli   potato", metrics, columns(40), 2);

		assertEquals(1, lines.size());
		assertEquals("3 x Chilli potato", lines.get(0));
	}

	@Test
	public void itemSitsBesideTheNameWhenItFitsWhole()
	{
		final LootCardOverlay.CardText text = LootCardOverlay.layOutText(
			"Bones", metrics, columns(30), columns(14), 2, false);

		assertFalse(text.nameOnOwnLine);
		assertEquals(1, text.lines.size());
		assertEquals("Bones", text.lines.get(0));
	}

	@Test
	public void itemMovesDownWholeRatherThanSplittingBesideTheName()
	{
		// The reported case: "GIM 99Chores:" leaves too little room for "9 x Iron bolts", and breaking
		// it after "Iron" read as two unrelated things. The label goes to the next row intact.
		final LootCardOverlay.CardText text = LootCardOverlay.layOutText(
			"9 x Iron bolts", metrics, columns(30), columns(20), 2, false);

		assertTrue(text.nameOnOwnLine);
		assertEquals(1, text.lines.size());
		assertEquals("9 x Iron bolts", text.lines.get(0));
	}

	@Test
	public void itemOnItsOwnRowStillWrapsIfItIsLongEnough()
	{
		final LootCardOverlay.CardText text = LootCardOverlay.layOutText(
			"1.2K x Ancient ceremonial legs", metrics, columns(16), columns(14), 2, false);

		assertTrue(text.nameOnOwnLine);
		assertEquals("the full width is used before a second row is taken", 2, text.lines.size());
	}

	@Test
	public void withoutAPlayerNameTheItemUsesTheWholeWidth()
	{
		final LootCardOverlay.CardText text = LootCardOverlay.layOutText(
			"Ancient ceremonial legs", metrics, columns(30), 0, 2, false);

		assertFalse(text.nameOnOwnLine);
		assertEquals(1, text.lines.size());
	}

	@Test
	public void stackedLayoutKeepsTheNameOnItsOwnRowEvenWhenTheItemWouldFit()
	{
		// The default: "Bones" fits beside the name three times over, and still belongs underneath it.
		final LootCardOverlay.CardText text = LootCardOverlay.layOutText(
			"Bones", metrics, columns(30), 0, 2, true);

		assertTrue(text.nameOnOwnLine);
		assertEquals(1, text.lines.size());
		assertEquals("Bones", text.lines.get(0));
	}

	@Test
	public void stackedLayoutGivesTheItemTheWholeWidth()
	{
		// Nothing is reserved for the name, so the item wraps only when it genuinely runs out of card.
		final LootCardOverlay.CardText text = LootCardOverlay.layOutText(
			"Ancient ceremonial legs", metrics, columns(30), 0, 2, true);

		assertTrue(text.nameOnOwnLine);
		assertEquals(1, text.lines.size());
	}

	@Test
	public void missingItemLabelProducesNoLines()
	{
		assertTrue(LootCardOverlay.layOutText(null, metrics, columns(30), columns(14), 2, false).lines.isEmpty());
		assertTrue(LootCardOverlay.layOutText("", metrics, columns(30), columns(14), 2, false).lines.isEmpty());
	}
}
