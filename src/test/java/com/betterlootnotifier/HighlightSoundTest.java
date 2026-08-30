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

import java.io.BufferedInputStream;
import java.io.InputStream;
import javax.sound.sampled.AudioSystem;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Pins the alert sounds to the classpath.
 *
 * <p>Existing is not enough, so each one is actually opened with the same audio stack
 * {@link net.runelite.client.audio.AudioPlayer} uses. These were sourced as MP3s, which
 * {@code javax.sound.sampled} cannot decode at all - shipping one by mistake would produce silence
 * and a debug line nobody reads, rather than a failure.
 */
public class HighlightSoundTest
{
	@Test
	public void everySoundResourceIsOnTheClasspathAndDecodable() throws Exception
	{
		for (final HighlightSound sound : HighlightSound.values())
		{
			if (sound == HighlightSound.NONE)
			{
				continue;
			}

			final String resource = sound.getResource();
			assertNotNull(sound + " has no resource", resource);

			try (InputStream in = HighlightSound.class.getResourceAsStream(resource))
			{
				assertNotNull("missing resource for " + sound + ": " + resource, in);

				// AudioSystem needs mark/reset to sniff the header, exactly as AudioPlayer does.
				AudioSystem.getAudioInputStream(new BufferedInputStream(in)).close();
			}
		}
	}

	@Test
	public void noneIsSilent()
	{
		assertNull("None must resolve to no resource so the player short-circuits",
			HighlightSound.NONE.getResource());
	}

	@Test
	public void soundsHaveReadableNames()
	{
		assertEquals("Chime", HighlightSound.CHIME.toString());
		assertEquals("Item pickup", HighlightSound.ITEM_PICKUP.toString());
		assertEquals("Arcade", HighlightSound.ARCADE.toString());
		assertEquals("None", HighlightSound.NONE.toString());
	}
}
