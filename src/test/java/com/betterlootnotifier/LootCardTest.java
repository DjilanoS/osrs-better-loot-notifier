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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LootCardTest
{
	private static final float EPSILON = 0.0001f;

	private static final long BORN = 10_000L;
	private static final long EXPIRE = 18_000L;
	private static final int FADE_IN = 160;
	private static final int FADE_OUT = 500;

	@Test
	public void startsTransparentAndReachesFullOpacityAfterFadeIn()
	{
		assertEquals(0f, LootCard.alphaAt(BORN, BORN, EXPIRE, FADE_IN, FADE_OUT), EPSILON);
		assertEquals(0.5f, LootCard.alphaAt(BORN + 80, BORN, EXPIRE, FADE_IN, FADE_OUT), EPSILON);
		assertEquals(1f, LootCard.alphaAt(BORN + FADE_IN, BORN, EXPIRE, FADE_IN, FADE_OUT), EPSILON);
	}

	@Test
	public void holdsFullOpacityThroughTheMiddleOfItsLife()
	{
		assertEquals(1f, LootCard.alphaAt(14_000L, BORN, EXPIRE, FADE_IN, FADE_OUT), EPSILON);
	}

	@Test
	public void rampsDownOverTheFinalFadeOut()
	{
		assertEquals(1f, LootCard.alphaAt(EXPIRE - FADE_OUT, BORN, EXPIRE, FADE_IN, FADE_OUT), EPSILON);
		assertEquals(0.5f, LootCard.alphaAt(EXPIRE - 250, BORN, EXPIRE, FADE_IN, FADE_OUT), EPSILON);
		assertEquals(0f, LootCard.alphaAt(EXPIRE, BORN, EXPIRE, FADE_IN, FADE_OUT), EPSILON);
		assertEquals(0f, LootCard.alphaAt(EXPIRE + 5_000, BORN, EXPIRE, FADE_IN, FADE_OUT), EPSILON);
	}

	@Test
	public void zeroFadeDurationsAreInstantRatherThanDividingByZero()
	{
		assertEquals(1f, LootCard.alphaAt(BORN, BORN, EXPIRE, 0, 0), EPSILON);
		assertEquals(1f, LootCard.alphaAt(EXPIRE - 1, BORN, EXPIRE, 0, 0), EPSILON);
		assertEquals(0f, LootCard.alphaAt(EXPIRE, BORN, EXPIRE, 0, 0), EPSILON);
	}

	@Test
	public void aShortLifeStillFadesBothWaysWithoutExceedingOne()
	{
		// Fade in and fade out overlap when the card is capped off early; the lower of the two wins
		// and the result must stay in range.
		final long shortExpire = BORN + 200;
		for (long t = BORN; t <= shortExpire; t += 10)
		{
			final float alpha = LootCard.alphaAt(t, BORN, shortExpire, FADE_IN, FADE_OUT);
			assertTrue("alpha out of range at t=" + t, alpha >= 0f && alpha <= 1f);
		}
	}

	@Test
	public void expiryBoundaryIsInclusive()
	{
		assertFalse(LootCard.isExpired(EXPIRE - 1, EXPIRE));
		assertTrue(LootCard.isExpired(EXPIRE, EXPIRE));
		assertTrue(LootCard.isExpired(EXPIRE + 1, EXPIRE));
	}

	@Test
	public void easingApproachesTheTargetWithoutOvershooting()
	{
		float value = 0f;
		for (int i = 0; i < 200; i++)
		{
			value = LootCard.ease(value, 100f, 0.016f, 0.07f);
			assertTrue("eased past the target", value <= 100f);
		}
		assertEquals(100f, value, 0.01f);
	}

	@Test
	public void easingDependsOnElapsedTimeNotFrameCount()
	{
		// The whole point of the exponential form: a 100ms gap must land in the same place whether
		// the client drew one frame or ten, or the stack animates faster on faster machines.
		final float oneBigStep = LootCard.ease(0f, 100f, 0.1f, 0.07f);

		float manySmallSteps = 0f;
		for (int i = 0; i < 10; i++)
		{
			manySmallSteps = LootCard.ease(manySmallSteps, 100f, 0.01f, 0.07f);
		}

		assertEquals(oneBigStep, manySmallSteps, 0.01f);
	}

	@Test
	public void easingIsANoOpForAZeroLengthFrame()
	{
		assertEquals(25f, LootCard.ease(25f, 100f, 0f, 0.07f), EPSILON);
	}

	@Test
	public void clampKeepsAlphaInRange()
	{
		assertEquals(0f, LootCard.clamp01(-3f), EPSILON);
		assertEquals(1f, LootCard.clamp01(4f), EPSILON);
		assertEquals(0.25f, LootCard.clamp01(0.25f), EPSILON);
	}

	private static LootCard card(String item, boolean highlighted, long value, long bornAtMs)
	{
		return new LootCard("Zezima", item, null, null, LootSource.SELF, highlighted, value,
			bornAtMs, EXPIRE, 0f);
	}

	private static List<String> orderOf(LootCard... cards)
	{
		final List<LootCard> list = new ArrayList<>(Arrays.asList(cards));
		list.sort(LootCard.BY_IMPORTANCE);

		final List<String> names = new ArrayList<>(list.size());
		for (final LootCard card : list)
		{
			names.add(card.getItemLabel());
		}
		return names;
	}

	@Test
	public void theMostValuableItemRisesToTheTop()
	{
		assertEquals(
			Arrays.asList("Twisted bow", "Dragon warhammer", "Bones"),
			orderOf(
				card("Bones", false, 200, 3_000L),
				card("Twisted bow", false, 1_000_000_000L, 1_000L),
				card("Dragon warhammer", false, 30_000_000L, 2_000L)));
	}

	@Test
	public void highlightingOutranksValue()
	{
		// An explicit "this matters" beats the price tag, however large the gap.
		assertEquals(
			Arrays.asList("Olmlet", "Twisted bow"),
			orderOf(
				card("Twisted bow", false, 1_000_000_000L, 1_000L),
				card("Olmlet", true, 0, 2_000L)));
	}

	@Test
	public void highlightedCardsAreRankedAgainstEachOtherByValue()
	{
		assertEquals(
			Arrays.asList("Scythe of vitur", "Olmlet", "Bones"),
			orderOf(
				card("Olmlet", true, 0, 1_000L),
				card("Bones", false, 200, 2_000L),
				card("Scythe of vitur", true, 800_000_000L, 3_000L)));
	}

	@Test
	public void equallyRankedCardsPutTheNewestFirst()
	{
		assertEquals(
			Arrays.asList("Second", "First"),
			orderOf(
				card("First", false, 5_000, 1_000L),
				card("Second", false, 5_000, 2_000L)));
	}

	@Test
	public void orderingIsStableAcrossRepeatedSorts()
	{
		// Re-sorted on every new card, so an unchanged stack must not shuffle between frames.
		final List<LootCard> list = new ArrayList<>(Arrays.asList(
			card("Bones", false, 200, 1_000L),
			card("Olmlet", true, 0, 2_000L),
			card("Coins", false, 200, 3_000L)));

		list.sort(LootCard.BY_IMPORTANCE);
		final List<LootCard> once = new ArrayList<>(list);
		list.sort(LootCard.BY_IMPORTANCE);

		assertEquals(once, list);
	}

	@Test
	public void layoutCacheOnlyAnswersForTheConditionsItWasBuiltFor()
	{
		final LootCard card = new LootCard("Zezima", "Twisted bow", null, null,
			LootSource.BOSS, BORN, EXPIRE, 0f);

		assertEquals("an unmeasured card is assumed to be one line tall",
			LootCardController.MIN_CARD_HEIGHT, card.getHeight());

		card.cacheLayout(801, Collections.singletonList("Twisted bow"), false, 52);

		assertTrue(card.hasLayout(801));
		assertEquals(52, card.getHeight());
		assertEquals(Collections.singletonList("Twisted bow"), card.getLayoutLines());
		assertFalse("a resized card must re-measure rather than reuse a stale layout",
			card.hasLayout(802));
	}
}
