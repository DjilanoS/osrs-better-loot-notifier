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
import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * Covers the sprite centring maths. Item sprites pad themselves asymmetrically, so drawing them at
 * the corner of their column leaves items visibly off to one side - this is what corrects for it.
 */
public class ItemResolverTest
{
	private static final int CELL_W = 36;
	private static final int CELL_H = 32;

	/** A blank 36x32 sprite with an opaque block drawn into it, mimicking an item's own padding. */
	private static BufferedImage sprite(int x, int y, int w, int h)
	{
		final BufferedImage image = new BufferedImage(CELL_W, CELL_H, BufferedImage.TYPE_INT_ARGB);
		for (int py = y; py < y + h; py++)
		{
			for (int px = x; px < x + w; px++)
			{
				image.setRGB(px, py, 0xFFFFFFFF);
			}
		}
		return image;
	}

	@Test
	public void contentHuggingTheLeftEdgeIsPushedRight()
	{
		// 10 wide against the left edge: 26 spare columns, so 13 to each side.
		final int[] offsets = ItemResolver.centringOffsets(sprite(0, 11, 10, 10), CELL_W, CELL_H);

		assertEquals(13, offsets[0]);
	}

	@Test
	public void contentHuggingTheRightEdgeIsPulledLeft()
	{
		final int[] offsets = ItemResolver.centringOffsets(sprite(26, 11, 10, 10), CELL_W, CELL_H);

		assertEquals(-13, offsets[0]);
	}

	@Test
	public void alreadyCentredContentIsLeftAlone()
	{
		final int[] offsets = ItemResolver.centringOffsets(sprite(13, 11, 10, 10), CELL_W, CELL_H);

		assertArrayEquals(new int[]{0, 0}, offsets);
	}

	@Test
	public void centresVerticallyToo()
	{
		// 10 tall against the top edge: 22 spare rows, so 11 above and below.
		final int[] offsets = ItemResolver.centringOffsets(sprite(13, 0, 10, 10), CELL_W, CELL_H);

		assertEquals(11, offsets[1]);
	}

	@Test
	public void contentFillingTheCellNeedsNoOffset()
	{
		assertArrayEquals(new int[]{0, 0},
			ItemResolver.centringOffsets(sprite(0, 0, CELL_W, CELL_H), CELL_W, CELL_H));
	}

	@Test
	public void anUnrenderedSpriteIsDrawnWhereItLands()
	{
		// Fully transparent: there is nothing to measure, so fall back to the plain corner blit
		// rather than inventing an offset.
		final BufferedImage blank = new BufferedImage(CELL_W, CELL_H, BufferedImage.TYPE_INT_ARGB);

		assertArrayEquals(new int[]{0, 0}, ItemResolver.centringOffsets(blank, CELL_W, CELL_H));
	}

	@Test
	public void handlesAMissingSprite()
	{
		assertArrayEquals(new int[]{0, 0}, ItemResolver.centringOffsets(null, CELL_W, CELL_H));
	}
}
