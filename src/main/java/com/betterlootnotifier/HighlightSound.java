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

/**
 * The alert that can play when a highlighted item drops.
 *
 * <p>Stored as 16-bit PCM WAV rather than the MP3s they were sourced from, because
 * {@code javax.sound.sampled} - which {@link net.runelite.client.audio.AudioPlayer} is built on -
 * ships no MP3 decoder, so an MP3 would simply fail to load at runtime.
 *
 * <p><strong>Must stay public.</strong> {@link BetterLootNotifierConfig} is implemented at runtime by a
 * generated proxy in another package, and a proxy cannot so much as name a package-private return
 * type - it throws {@link IllegalAccessError} the first time the setting is read. Any type used in a
 * config method signature has the same requirement.
 */
public enum HighlightSound
{
	NONE("None", null),
	CHIME("Chime", "sound/scale-e6.wav"),
	ITEM_PICKUP("Item pickup", "sound/item-pickup.wav"),
	ARCADE("Arcade", "sound/arcade.wav");

	private final String displayName;
	private final String resource;

	HighlightSound(String displayName, String resource)
	{
		this.displayName = displayName;
		this.resource = resource;
	}

	/** Path relative to this package, or null when nothing should play. */
	String getResource()
	{
		return resource;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
