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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RecentLootKeysTest
{
	@Test
	public void firstOfferIsNewAndAnImmediateRepeatIsNot()
	{
		final RecentLootKeys keys = new RecentLootKeys(1500L);

		assertTrue(keys.offer("npc|123:1", 1_000L));
		assertFalse("the second event for one kill must be swallowed", keys.offer("npc|123:1", 1_100L));
	}

	@Test
	public void keyIsNewAgainOnceTheWindowHasPassed()
	{
		final RecentLootKeys keys = new RecentLootKeys(1500L);

		assertTrue(keys.offer("npc|123:1", 1_000L));
		assertTrue("a genuinely later kill is not a duplicate", keys.offer("npc|123:1", 2_500L));
	}

	@Test
	public void distinctKeysDoNotInterfere()
	{
		final RecentLootKeys keys = new RecentLootKeys(1500L);

		assertTrue(keys.offer("npc|123:1", 1_000L));
		assertTrue(keys.offer("npc|456:1", 1_000L));
		assertFalse(keys.offer("npc|123:1", 1_000L));
	}

	@Test
	public void containsDoesNotRecordTheKey()
	{
		final RecentLootKeys keys = new RecentLootKeys(1500L);

		assertFalse(keys.contains("self|zezima|twisted bow|1", 1_000L));
		assertTrue("a lookup must not count as an occurrence", keys.offer("self|zezima|twisted bow|1", 1_000L));
	}

	@Test
	public void staysBoundedUnderABurst()
	{
		final RecentLootKeys keys = new RecentLootKeys(60_000L);

		for (int i = 0; i < 500; i++)
		{
			keys.offer("npc|" + i + ":1", 1_000L);
		}

		assertTrue("a burst must not grow the deque without limit", keys.size() <= 64);
	}

	@Test
	public void expiredEntriesAreDropped()
	{
		final RecentLootKeys keys = new RecentLootKeys(1000L);

		keys.offer("a", 0L);
		keys.offer("b", 100L);
		keys.contains("c", 5_000L);

		assertEquals(0, keys.size());
	}

	@Test
	public void clearForgetsEverything()
	{
		final RecentLootKeys keys = new RecentLootKeys(60_000L);

		keys.offer("npc|123:1", 1_000L);
		keys.clear();

		assertTrue(keys.offer("npc|123:1", 1_000L));
	}
}
