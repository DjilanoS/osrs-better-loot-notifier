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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * A small set of keys that forget themselves after a while, used to collapse the several events
 * the client can fire for one real drop.
 *
 * <p>Bounded on both ends: entries expire on time, and the deque is capped so a pathological burst
 * cannot grow it without limit.
 */
final class RecentLootKeys
{
	private static final int MAX_ENTRIES = 64;

	private final long windowMs;
	private final Deque<Entry> entries = new ArrayDeque<>();

	RecentLootKeys(long windowMs)
	{
		this.windowMs = windowMs;
	}

	/**
	 * Records the key and reports whether it was new.
	 *
	 * @return true when the key had not been seen inside the window, i.e. the caller should act on it
	 */
	boolean offer(String key, long nowMs)
	{
		final boolean seen = contains(key, nowMs);
		if (!seen)
		{
			entries.addLast(new Entry(key, nowMs));
			while (entries.size() > MAX_ENTRIES)
			{
				entries.removeFirst();
			}
		}
		return !seen;
	}

	boolean contains(String key, long nowMs)
	{
		prune(nowMs);
		for (final Entry entry : entries)
		{
			if (entry.key.equals(key))
			{
				return true;
			}
		}
		return false;
	}

	void clear()
	{
		entries.clear();
	}

	int size()
	{
		return entries.size();
	}

	private void prune(long nowMs)
	{
		final Iterator<Entry> it = entries.iterator();
		while (it.hasNext())
		{
			// Entries are appended in time order, so the first live one ends the sweep. A clock that
			// jumps backwards would otherwise strand entries here forever, hence the negative check.
			final long age = nowMs - it.next().addedAtMs;
			if (age >= windowMs || age < 0)
			{
				it.remove();
			}
			else
			{
				return;
			}
		}
	}

	private static final class Entry
	{
		private final String key;
		private final long addedAtMs;

		private Entry(String key, long addedAtMs)
		{
			this.key = key;
			this.addedAtMs = addedAtMs;
		}
	}
}
