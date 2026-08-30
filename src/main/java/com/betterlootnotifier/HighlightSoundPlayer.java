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

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.audio.AudioPlayer;

/**
 * Plays the highlight alert, always off the client thread.
 *
 * <p>Opening an audio line can block for a noticeable moment, which on the client thread would be a
 * stutter in the middle of a kill - exactly when this plugin fires. Playback is therefore handed to
 * a single daemon thread whose queue discards rather than blocks, so a burst of drops can never back
 * up into the game.
 */
@Slf4j
@Singleton
class HighlightSoundPlayer
{
	/**
	 * Queue depth. Small on purpose: if playback has fallen this far behind, the alerts are no longer
	 * telling the player anything timely and dropping them is better than a delayed pile-up.
	 */
	private static final int MAX_PENDING = 4;

	private final AudioPlayer audioPlayer;

	/** Created on first use and released on shutdown, so an unused plugin costs no thread. */
	private ThreadPoolExecutor player;

	@Inject
	HighlightSoundPlayer(AudioPlayer audioPlayer)
	{
		this.audioPlayer = audioPlayer;
	}

	void play(HighlightSound sound, int volumePercent)
	{
		if (sound == null || sound.getResource() == null || volumePercent <= 0)
		{
			return;
		}

		final String resource = sound.getResource();
		final float gainDb = gainFor(volumePercent);

		// DiscardPolicy silently drops the task when the queue is full or the executor is shutting
		// down, so execute() never throws and the client thread is never affected.
		ensurePlayer().execute(() ->
		{
			try
			{
				audioPlayer.play(HighlightSoundPlayer.class, resource, gainDb);
			}
			catch (Exception e)
			{
				// Best effort: no audio line, a headless mixer, or a missing resource. A missing alert
				// is not worth interrupting anything over.
				log.debug("Highlight sound skipped: {}", resource, e);
			}
		});
	}

	/** Lazily (re)creates the player thread. Synchronized against {@link #dispose}; cheap on the fast path. */
	private synchronized ThreadPoolExecutor ensurePlayer()
	{
		if (player == null || player.isShutdown())
		{
			player = new ThreadPoolExecutor(
				1, 1, 0L, TimeUnit.MILLISECONDS,
				new LinkedBlockingQueue<>(MAX_PENDING),
				r ->
				{
					final Thread thread = new Thread(r, "better-loot-notifier-sound");
					thread.setDaemon(true);
					return thread;
				},
				new ThreadPoolExecutor.DiscardPolicy());
		}
		return player;
	}

	/**
	 * Maps a 1-100 volume onto a decibel gain. Loudness is perceived logarithmically, so a linear
	 * percentage is converted rather than used directly: 100% is the sample's own level and 1% is
	 * about -40 dB. Floored at 1 because {@code log10(0)} is undefined.
	 */
	private static float gainFor(int volumePercent)
	{
		final float fraction = Math.max(1, volumePercent) / 100f;
		return (float) (20.0 * Math.log10(fraction));
	}

	/** Stops the player thread and releases it. Called on plugin shutdown. */
	synchronized void dispose()
	{
		if (player != null)
		{
			player.shutdownNow();
			player = null;
		}
	}
}
