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

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Paints the card stack. Draws only - every value it reads was computed on the client thread by
 * {@link LootCardController}.
 *
 * <p>Positioning follows RuneLite's own movable-overlay contract, which is easy to get subtly wrong:
 * the overlay renderer translates the {@link Graphics2D} to the overlay's location <em>before</em>
 * calling {@link #render}, and derives the draggable bounds from the returned {@link Dimension}. So
 * everything here is drawn from a local origin of (0, 0), a real size is always returned, and a
 * preferred location is seeded on the first frame - without one the renderer anchors at (0, 0) and
 * the first drag lands the stack twice as far as it was dragged.
 */
class LootCardOverlay extends Overlay
{
	/**
	 * Stable across class renames on purpose: {@link OverlayManager} persists the user's chosen
	 * position under this name, so changing it would silently reset everyone's placement.
	 */
	private static final String OVERLAY_NAME = "betterlootnotifier";

	private static final int ACCENT_WIDTH = 3;
	private static final int ICON_WIDTH = LootCardController.ICON_WIDTH;
	private static final int ICON_HEIGHT = LootCardController.ICON_HEIGHT;
	private static final int PAD_LEFT = 6;
	private static final int PAD_RIGHT = 8;
	/** Vertical inset. Sized so the text is not crowded against the card's top and bottom edges. */
	private static final int PAD_Y = 4;
	private static final int CORNER_RADIUS = 8;
	private static final int BORDER_WIDTH = 1;

	/**
	 * Highlighted cards carry a heavier frame, so they stand out even in a monochrome screenshot.
	 * Kept one pixel above the normal border rather than at a fixed size, so the two stay in
	 * proportion - a 3px frame against a 1px one reads as a different component, not an emphasis.
	 */
	private static final int HIGHLIGHT_BORDER_WIDTH = BORDER_WIDTH + 1;

	/** Gap after the icon, before the text column begins. */
	private static final int ICON_GAP = 10;

	/** Gap after the player name, before the item name begins. */
	private static final int NAME_GAP = 7;

	/** Gap between the item name and the value, so they never read as one string. */
	private static final int VALUE_GAP = 10;

	/** Extra leading between wrapped lines. */
	private static final int LINE_SPACING = 1;

	/**
	 * A long item name wraps rather than being cut short, but only so far - past two lines a card
	 * stops being a glance and starts being a paragraph.
	 */
	private static final int MAX_ITEM_LINES = 2;

	private static final String NAME_SEPARATOR = ":";

	private static final Color SHADOW = Color.BLACK;
	private static final Color VALUE_COLOR = new Color(255, 255, 255, 165);

	/** Left inset of the default anchor. Clears the slide-in distance so it is never clipped. */
	private static final int DEFAULT_ANCHOR_X = 16;

	private final Client client;
	private final LootCardController controller;
	private final BetterLootNotifierConfig config;

	@Inject
	LootCardOverlay(Client client, LootCardController controller, BetterLootNotifierConfig config)
	{
		this.client = client;
		this.controller = controller;
		this.config = config;

		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_HIGH);
		setMovable(true);
		// Snapping is corner-based, which would keep dragging the stack away from a left-edge anchor.
		setSnappable(false);
		setResettable(true);
		addMenuEntry(MenuAction.RUNELITE_OVERLAY_CONFIG, OverlayManager.OPTION_CONFIGURE, "Better Loot Notifier");
	}

	@Override
	public String getName()
	{
		return OVERLAY_NAME;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (getPreferredLocation() == null)
		{
			seedPreferredLocation();
			// The renderer already translated using the old location this frame, so draw nothing and
			// pick up the new anchor on the next one.
			return null;
		}

		List<LootCard> cards = controller.getCards();
		if (cards.isEmpty())
		{
			final LootCard preview = controller.getPreviewCard();
			if (preview == null)
			{
				// Returning null leaves the overlay with empty bounds, so an idle stack has no
				// invisible hit box sitting over the game world.
				return null;
			}
			cards = Collections.singletonList(preview);
		}

		final int width = config.cardWidth();
		final long now = System.currentTimeMillis();
		final int fadeOut = config.fadeMillis();

		final Font font = FontManager.getRunescapeSmallFont();
		graphics.setFont(font);
		final FontMetrics metrics = graphics.getFontMetrics(font);

		final Composite originalComposite = graphics.getComposite();
		try
		{
			for (final LootCard card : cards)
			{
				final float alpha = card.alphaAt(now, LootCardController.FADE_IN_MS, fadeOut);
				if (alpha <= 0f)
				{
					continue;
				}

				graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
				drawCard(graphics, metrics, card, Math.round(card.getSlideX()),
					Math.round(card.getRenderY()), width);
			}
		}
		finally
		{
			graphics.setComposite(originalComposite);
		}

		return new Dimension(width, Math.round(controller.getStackHeight()));
	}

	private void drawCard(Graphics2D graphics, FontMetrics metrics, LootCard card, int x, int y, int width)
	{
		final boolean showIcon = config.showIcon();
		final String valueText = card.getValueText();

		final int textLeft = x + ACCENT_WIDTH + PAD_LEFT + (showIcon ? ICON_WIDTH + ICON_GAP : 0);
		final int valueWidth = valueText == null ? 0 : metrics.stringWidth(valueText) + VALUE_GAP;
		final int textWidth = (x + width - PAD_RIGHT - valueWidth) - textLeft;

		final String playerName = card.getPlayerName();
		final boolean hasName = playerName != null && !playerName.isEmpty();

		// Stacked, the name is a heading rather than a prefix, so it needs no trailing colon and
		// claims no width from the item's line.
		final boolean stacked = hasName && config.nameAboveItem();
		final String namePrefix = hasName ? (stacked ? playerName : playerName + NAME_SEPARATOR) : null;
		final int nameWidth = hasName && !stacked ? metrics.stringWidth(namePrefix) + NAME_GAP : 0;

		final int lineHeight = metrics.getAscent() + metrics.getDescent();

		// Anything that moves the wrap points is folded into one key, so the layout is measured once
		// and then reused every frame until the user actually changes something.
		final int layoutKey = (width * 8) + (showIcon ? 4 : 0) + (valueText != null ? 2 : 0)
			+ (stacked ? 1 : 0);
		if (!card.hasLayout(layoutKey))
		{
			final CardText text = layOutText(card.getItemLabel(), metrics, textWidth, nameWidth,
				MAX_ITEM_LINES, stacked);

			final int rows = Math.max(1, text.lines.size() + (text.nameOnOwnLine ? 1 : 0));
			final int textHeight = (rows * lineHeight) + ((rows - 1) * LINE_SPACING);

			card.cacheLayout(layoutKey, text.lines, text.nameOnOwnLine,
				Math.max(LootCardController.MIN_CARD_HEIGHT,
					Math.max(showIcon ? ICON_HEIGHT : 0, textHeight) + (PAD_Y * 2)));
		}

		final List<String> lines = card.getLayoutLines();
		final boolean nameOnOwnLine = card.isNameOnOwnLine();
		final int height = card.getHeight();

		// A highlighted card is recoloured whole rather than just flagged, so it reads as different at
		// a glance instead of needing to be compared against its neighbours.
		final boolean highlighted = card.isHighlighted();
		final int borderWidth = highlighted ? HIGHLIGHT_BORDER_WIDTH : BORDER_WIDTH;

		graphics.setColor(highlighted ? config.highlightBackgroundColor() : config.backgroundColor());
		graphics.fillRoundRect(x, y, width, height, CORNER_RADIUS, CORNER_RADIUS);

		if (config.showSourceAccent())
		{
			// Clipped to the rounded background so the bar follows the corner instead of poking
			// through it.
			final Shape originalClip = graphics.getClip();
			graphics.clipRect(x, y, width, height);
			graphics.setColor(accentColor(card.getSource()));
			graphics.fillRect(x + borderWidth, y + borderWidth, ACCENT_WIDTH, height - (borderWidth * 2));
			graphics.setClip(originalClip);
		}

		final Stroke originalStroke = graphics.getStroke();
		graphics.setStroke(new BasicStroke(borderWidth));
		graphics.setColor(highlighted ? config.highlightBorderColor() : config.borderColor());
		// Inset by half the stroke so the border lands inside the filled panel rather than straddling
		// its edge.
		graphics.drawRoundRect(x + (borderWidth / 2), y + (borderWidth / 2),
			width - borderWidth, height - borderWidth, CORNER_RADIUS, CORNER_RADIUS);
		graphics.setStroke(originalStroke);

		final BufferedImage icon = card.getIcon();
		if (showIcon && icon != null)
		{
			// The icon column is reserved whether or not the sprite has resolved yet, so text does not
			// shift sideways when an async image arrives a frame later. The offsets centre the item's
			// visible pixels within that column, since the sprite's own padding is rarely symmetric.
			graphics.drawImage(icon,
				x + ACCENT_WIDTH + PAD_LEFT + card.getIconOffsetX(),
				y + ((height - ICON_HEIGHT) / 2) + card.getIconOffsetY(),
				null);
		}

		if (valueText != null)
		{
			final int valueBaseline = y + ((height - lineHeight) / 2) + metrics.getAscent();
			drawShadowed(graphics, valueText,
				x + width - PAD_RIGHT - metrics.stringWidth(valueText), valueBaseline, VALUE_COLOR);
		}

		if (textWidth <= 0)
		{
			return;
		}

		final int rows = Math.max(1, lines.size() + (nameOnOwnLine ? 1 : 0));
		final int textHeight = (rows * lineHeight) + ((rows - 1) * LINE_SPACING);
		final int firstBaseline = y + ((height - textHeight) / 2) + metrics.getAscent();

		if (hasName)
		{
			drawShadowed(graphics, namePrefix, textLeft, firstBaseline, config.nameColor());
		}

		// The item sits beside the name when it fits there whole; otherwise the name keeps the first
		// row to itself and the item starts on the next one, at the full column width.
		final int firstItemRow = nameOnOwnLine ? 1 : 0;
		for (int i = 0; i < lines.size(); i++)
		{
			final int row = firstItemRow + i;
			final int lineX = row == 0 ? textLeft + nameWidth : textLeft;
			final int baseline = firstBaseline + (row * (lineHeight + LINE_SPACING));

			drawShadowed(graphics, lines.get(i), lineX, baseline, config.itemColor());
		}
	}

	/**
	 * Decides how the player name and item name share the card.
	 *
	 * <p>The item label is kept whole wherever possible. Breaking "9 x Iron bolts" across rows just to
	 * fill the space left over beside the player name reads as two separate things, so if the label
	 * does not fit there in one piece the name takes the first row alone and the label moves down
	 * intact, where it has the full width to work with.
	 *
	 * @param nameWidth  width already claimed by the player name, or 0 when the name is not sharing
	 *                   the item's row
	 * @param forceOwnLine put the name on its own row whether or not the item would have fitted
	 *                     beside it
	 */
	static CardText layOutText(String itemLabel, FontMetrics metrics, int textWidth, int nameWidth,
		int maxItemLines, boolean forceOwnLine)
	{
		if (itemLabel == null || itemLabel.isEmpty())
		{
			return new CardText(Collections.emptyList(), false);
		}

		if (forceOwnLine || (nameWidth > 0 && metrics.stringWidth(itemLabel) > textWidth - nameWidth))
		{
			return new CardText(wrap(itemLabel, metrics, textWidth, maxItemLines), true);
		}

		return new CardText(wrap(itemLabel, metrics, textWidth - nameWidth, maxItemLines), false);
	}

	/** How the item label was broken up, and whether the player name had to take a row of its own. */
	static final class CardText
	{
		final List<String> lines;
		final boolean nameOnOwnLine;

		CardText(List<String> lines, boolean nameOnOwnLine)
		{
			this.lines = lines;
			this.nameOnOwnLine = nameOnOwnLine;
		}
	}

	/**
	 * Greedy word wrap into at most {@code maxLines}.
	 *
	 * <p>Deliberately never truncates: a word too wide for the column is drawn anyway, and once the
	 * last permitted line is reached everything remaining is kept on it. Overflow runs to the edge
	 * rather than being replaced by an ellipsis, because a name shortened to "Ancient ceremonial l..."
	 * tells you less than one that simply runs on.
	 */
	static List<String> wrap(String text, FontMetrics metrics, int width, int maxLines)
	{
		final List<String> lines = new ArrayList<>(maxLines);
		if (text == null || text.isEmpty())
		{
			return lines;
		}

		final StringBuilder current = new StringBuilder();

		for (final String word : text.split(" "))
		{
			if (word.isEmpty())
			{
				continue;
			}

			if (current.length() == 0)
			{
				current.append(word);
				continue;
			}

			final String candidate = current + " " + word;

			if (metrics.stringWidth(candidate) <= width || lines.size() >= maxLines - 1)
			{
				current.setLength(0);
				current.append(candidate);
			}
			else
			{
				lines.add(current.toString());
				current.setLength(0);
				current.append(word);
			}
		}

		if (current.length() > 0)
		{
			lines.add(current.toString());
		}

		return lines;
	}

	/** Every string gets a black copy one pixel down and right, which keeps it legible over anything. */
	private static void drawShadowed(Graphics2D graphics, String text, int x, int baseline, Color color)
	{
		graphics.setColor(SHADOW);
		graphics.drawString(text, x + 1, baseline + 1);
		graphics.setColor(color);
		graphics.drawString(text, x, baseline);
	}

	private Color accentColor(LootSource source)
	{
		switch (source)
		{
			case BOSS:
				return config.bossAccentColor();
			case GROUP_IRONMAN:
				return config.groupAccentColor();
			default:
				return config.ownAccentColor();
		}
	}

	/**
	 * Anchors the stack at the left edge, vertically centred, the first time it is drawn. Only ever
	 * runs when nothing was restored from config, so it cannot overwrite a placement the user chose.
	 */
	private void seedPreferredLocation()
	{
		final int canvasHeight = client.getCanvasHeight();
		final int stack = (LootCardController.MIN_CARD_HEIGHT + LootCardController.CARD_GAP)
			* Math.max(1, config.maxCards());

		int y = (canvasHeight - stack) / 2;
		final int maxY = Math.max(0, canvasHeight - LootCardController.MIN_CARD_HEIGHT);
		y = Math.max(0, Math.min(y, maxY));

		setPreferredLocation(new Point(DEFAULT_ANCHOR_X, y));
	}
}
