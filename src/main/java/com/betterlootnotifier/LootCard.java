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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * One card on screen: what it says, and where in its life it currently is.
 *
 * <p>The animation state ({@link #renderY}, {@link #slideX}) is advanced by the controller on the
 * client thread and only read by the overlay, so nothing here needs to be thread safe. The maths
 * that drives it is kept static and side-effect free so it can be tested without a client.
 */
class LootCard
{
	/** No layout has been measured yet, so the card is still assumed to be a single line tall. */
	private static final int NO_LAYOUT = -1;

	private final String playerName;
	private final String itemLabel;
	private final String valueText;
	private final BufferedImage icon;
	private final LootSource source;
	private final boolean highlighted;

	/** The whole stack's worth in coins, kept numerically so cards can be ranked against each other. */
	private final long value;

	private final long bornAtMs;

	/** Pulled forward when the card is pushed off the bottom of the stack by newer loot. */
	private long expireAtMs;

	private float renderY;
	private float slideX;

	/**
	 * The item text broken into the lines it will actually be drawn on, plus the height that needs.
	 * Wrapping means measuring text, which needs font metrics the controller does not have, so the
	 * overlay works it out on the first frame it draws this card and stores it back here. The stack
	 * layout is a frame behind on the very first frame of a new card, which the easing hides.
	 */
	private List<String> layoutLines = Collections.emptyList();
	private boolean nameOnOwnLine;
	private int layoutKey = NO_LAYOUT;
	private int height;

	/** Measured once when the card is built, so the sprite's visible pixels sit centred in its column. */
	private int iconOffsetX;
	private int iconOffsetY;

	/**
	 * Ranks the stack: highlighted first, then most valuable, then newest.
	 *
	 * <p>Highlighting outranks value because it is an explicit instruction - the player has said this
	 * item matters - whereas value is only a proxy for the same thing. The final tiebreak on age keeps
	 * the order total, so an unchanged stack never reshuffles between frames.
	 */
	static final Comparator<LootCard> BY_IMPORTANCE = (a, b) ->
	{
		if (a.highlighted != b.highlighted)
		{
			return a.highlighted ? -1 : 1;
		}

		final int byValue = Long.compare(b.value, a.value);
		return byValue != 0 ? byValue : Long.compare(b.bornAtMs, a.bornAtMs);
	};

	LootCard(String playerName, String itemLabel, String valueText, BufferedImage icon,
		LootSource source, long bornAtMs, long expireAtMs, float slideX)
	{
		this(playerName, itemLabel, valueText, icon, source, false, 0L, bornAtMs, expireAtMs, slideX);
	}

	LootCard(String playerName, String itemLabel, String valueText, BufferedImage icon,
		LootSource source, boolean highlighted, long value, long bornAtMs, long expireAtMs, float slideX)
	{
		this.playerName = playerName;
		this.itemLabel = itemLabel;
		this.valueText = valueText;
		this.icon = icon;
		this.source = source;
		this.highlighted = highlighted;
		this.value = value;
		this.bornAtMs = bornAtMs;
		this.expireAtMs = expireAtMs;
		this.slideX = slideX;
	}

	/**
	 * Opacity at a moment in this card's life: ramps up over {@code fadeInMs}, holds, then ramps
	 * down over the last {@code fadeOutMs} before expiry.
	 *
	 * <p>Static and parameterised rather than reading the clock itself so the whole curve, including
	 * its boundaries, is testable.
	 */
	static float alphaAt(long nowMs, long bornAtMs, long expireAtMs, int fadeInMs, int fadeOutMs)
	{
		final long remaining = expireAtMs - nowMs;
		if (remaining <= 0)
		{
			return 0f;
		}

		final long age = nowMs - bornAtMs;
		if (age < 0)
		{
			return 0f;
		}

		float alpha = 1f;
		if (fadeInMs > 0)
		{
			alpha = Math.min(alpha, (float) age / fadeInMs);
		}
		if (fadeOutMs > 0)
		{
			alpha = Math.min(alpha, (float) remaining / fadeOutMs);
		}

		return clamp01(alpha);
	}

	static boolean isExpired(long nowMs, long expireAtMs)
	{
		return nowMs >= expireAtMs;
	}

	/**
	 * Moves {@code current} a fraction of the way to {@code target}, framed so that the result
	 * depends on elapsed time rather than on how many frames elapsed.
	 *
	 * <p>The naive {@code current += (target - current) * k} converges twice as fast at 100fps as at
	 * 50fps, which makes the stack visibly snappier on faster machines. Decaying by
	 * {@code 1 - e^(-dt/tau)} instead makes ten 10ms steps land where one 100ms step does.
	 */
	static float ease(float current, float target, float dtSeconds, float tauSeconds)
	{
		if (dtSeconds <= 0f || tauSeconds <= 0f)
		{
			return tauSeconds <= 0f ? target : current;
		}

		final float factor = 1f - (float) Math.exp(-dtSeconds / tauSeconds);
		return current + ((target - current) * factor);
	}

	static float clamp01(float v)
	{
		return v < 0f ? 0f : (v > 1f ? 1f : v);
	}

	float alphaAt(long nowMs, int fadeInMs, int fadeOutMs)
	{
		return alphaAt(nowMs, bornAtMs, expireAtMs, fadeInMs, fadeOutMs);
	}

	boolean isExpired(long nowMs)
	{
		return isExpired(nowMs, expireAtMs);
	}

	String getPlayerName()
	{
		return playerName;
	}

	String getItemLabel()
	{
		return itemLabel;
	}

	String getValueText()
	{
		return valueText;
	}

	BufferedImage getIcon()
	{
		return icon;
	}

	int getIconOffsetX()
	{
		return iconOffsetX;
	}

	int getIconOffsetY()
	{
		return iconOffsetY;
	}

	void setIconOffsets(int x, int y)
	{
		this.iconOffsetX = x;
		this.iconOffsetY = y;
	}

	LootSource getSource()
	{
		return source;
	}

	/** Whether this item matched the highlight list and should be drawn to stand out. */
	boolean isHighlighted()
	{
		return highlighted;
	}

	long getExpireAtMs()
	{
		return expireAtMs;
	}

	void setExpireAtMs(long expireAtMs)
	{
		this.expireAtMs = expireAtMs;
	}

	float getRenderY()
	{
		return renderY;
	}

	void setRenderY(float renderY)
	{
		this.renderY = renderY;
	}

	float getSlideX()
	{
		return slideX;
	}

	void setSlideX(float slideX)
	{
		this.slideX = slideX;
	}

	/** The measured height, or the single-line height until the overlay has laid this card out. */
	int getHeight()
	{
		return height > 0 ? height : LootCardController.MIN_CARD_HEIGHT;
	}

	/** True when the cached layout was built for these exact conditions and can be reused. */
	boolean hasLayout(int key)
	{
		return layoutKey == key;
	}

	List<String> getLayoutLines()
	{
		return layoutLines;
	}

	/** True when the item label would not fit beside the player name and took the next row instead. */
	boolean isNameOnOwnLine()
	{
		return nameOnOwnLine;
	}

	void cacheLayout(int key, List<String> lines, boolean nameOnOwnLine, int height)
	{
		this.layoutKey = key;
		this.layoutLines = lines;
		this.nameOnOwnLine = nameOnOwnLine;
		this.height = height;
	}
}
