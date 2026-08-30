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

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(BetterLootNotifierConfig.GROUP)
public interface BetterLootNotifierConfig extends Config
{
	String GROUP = "better-loot-notifier";

	@ConfigSection(
		name = "Loot sources",
		description = "Which loot announcements become cards",
		position = 0
	)
	String sourcesSection = "sources";

	@ConfigSection(
		name = "Filters",
		description = "Limit how many cards show and which items qualify",
		position = 1
	)
	String filtersSection = "filters";

	@ConfigSection(
		name = "Highlights",
		description = "Items worth calling out, and the alert that announces them",
		position = 2
	)
	String highlightsSection = "highlights";

	@ConfigSection(
		name = "Appearance",
		description = "Card size and colours",
		position = 3
	)
	String appearanceSection = "appearance";

	@ConfigSection(
		name = "Timing & motion",
		description = "How long cards live and how they animate",
		position = 4
	)
	String timingSection = "timing";

	@ConfigSection(
		name = "Positioning & debug",
		description = "Placing the stack, and capturing unrecognised broadcasts",
		position = 5
	)
	String positioningSection = "positioning";

	// --- Loot sources ----------------------------------------------------------

	@ConfigItem(
		keyName = "showGroupIronman",
		name = "Group Ironman broadcasts",
		description = "Show a card when a group member's drop is broadcast to the group.",
		section = sourcesSection,
		position = 0
	)
	default boolean showGroupIronman()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showBossLoot",
		name = "Boss loot",
		description = "Show a card when a boss announces a drop to everyone present - the assignment "
			+ "that names whoever dealt the most damage at bosses like General Graardor or the Giant "
			+ "Mole, and special loot in CoX, ToB and ToA.",
		section = sourcesSection,
		position = 1
	)
	default boolean showBossLoot()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showOwnLoot",
		name = "Your own drops",
		description = "Show a card for loot you receive yourself.",
		section = sourcesSection,
		position = 2
	)
	default boolean showOwnLoot()
	{
		return true;
	}

	// --- Filters ---------------------------------------------------------------

	@Range(min = 1, max = 15)
	@ConfigItem(
		keyName = "maxCards",
		name = "Max cards",
		description = "How many cards can be on screen at once. The oldest fades out when a new one arrives.",
		section = filtersSection,
		position = 0
	)
	default int maxCards()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "useGroundItemsLists",
		name = "Use Ground Items lists",
		description = "Also apply the Ground Items plugin's Hidden items and Highlighted items lists, "
			+ "so items already set up there do not have to be listed again.<br>"
			+ "The two lists below still win: an item in Always show gets a card even if Ground Items "
			+ "hides it.",
		section = filtersSection,
		position = 1
	)
	default boolean useGroundItemsLists()
	{
		return false;
	}

	@Range(min = 0, max = 100_000_000)
	@Units(" gp")
	@ConfigItem(
		keyName = "minValue",
		name = "Minimum value",
		description = "Hide loot worth less than this. 0 shows everything.<br>"
			+ "Group broadcasts state their own value, so they are judged on that. Your own drops are "
			+ "priced from the Grand Exchange instead, and untradeables (pets, ornament kits) have no "
			+ "price there - so any value above 0 hides them. Add those to Always show.",
		section = filtersSection,
		position = 2
	)
	default int minValue()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "alwaysShow",
		name = "Always show",
		description = "Comma-separated item names that always get a card, ignoring the minimum value.",
		section = filtersSection,
		position = 3
	)
	default String alwaysShow()
	{
		return "";
	}

	@ConfigItem(
		keyName = "neverShow",
		name = "Never show",
		description = "Comma-separated item names that never get a card. This wins over Always show.",
		section = filtersSection,
		position = 4
	)
	default String neverShow()
	{
		return "";
	}

	// --- Highlights ------------------------------------------------------------

	@ConfigItem(
		keyName = "highlightItems",
		name = "Highlighted items",
		description = "Comma-separated item names worth calling out. These get a brighter card, play "
			+ "the alert below, and always show regardless of the minimum value.<br>"
			+ "Ground Items' own Highlighted items are included too while Use Ground Items lists is on."
			+ "<br>Wildcards such as <code>*bow*</code> are supported.",
		section = highlightsSection,
		position = 0
	)
	default String highlightItems()
	{
		return "";
	}

	@Alpha
	@ConfigItem(
		keyName = "highlightBackgroundColor",
		name = "Highlight background",
		description = "Card background for a highlighted item.",
		section = highlightsSection,
		position = 1
	)
	default Color highlightBackgroundColor()
	{
		return new Color(197, 137, 29, 156);
	}

	@Alpha
	@ConfigItem(
		keyName = "highlightBorderColor",
		name = "Highlight border",
		description = "Card border for a highlighted item.",
		section = highlightsSection,
		position = 2
	)
	default Color highlightBorderColor()
	{
		return new Color(255, 183, 91, 199);
	}

	@ConfigItem(
		keyName = "highlightSound",
		name = "Alert sound",
		description = "Sound played when a highlighted item drops. Set to None for silence.",
		section = highlightsSection,
		position = 3
	)
	default HighlightSound highlightSound()
	{
		return HighlightSound.ARCADE;
	}

	@Range(min = 0, max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "highlightSoundVolume",
		name = "Alert volume",
		description = "Volume of the alert sound. 0 is silent.",
		section = highlightsSection,
		position = 4
	)
	default int highlightSoundVolume()
	{
		return 70;
	}

	// --- Appearance ------------------------------------------------------------

	@Range(min = 140, max = 340)
	@Units(Units.PIXELS)
	@ConfigItem(
		keyName = "cardWidth",
		name = "Card width",
		description = "Width of each card. Long item names wrap onto a second line and the card grows "
			+ "to fit rather than being cut short.",
		section = appearanceSection,
		position = 0
	)
	default int cardWidth()
	{
		return 250;
	}

	@ConfigItem(
		keyName = "nameAboveItem",
		name = "Name above item",
		description = "Put the player name on its own line with the item name beneath it.<br>"
			+ "Turn this off for the more compact single line, \"Zezima: Twisted bow\".",
		section = appearanceSection,
		position = 1
	)
	default boolean nameAboveItem()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showIcon",
		name = "Show item icon",
		description = "Draw the item's sprite on the card.",
		section = appearanceSection,
		position = 2
	)
	default boolean showIcon()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showValue",
		name = "Show value",
		description = "Add the Grand Exchange value on the right of the card, as \"GE: 2.2M\".",
		section = appearanceSection,
		position = 3
	)
	default boolean showValue()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showSourceAccent",
		name = "Show source accent",
		description = "Tint a bar on the left edge of the card to show where the loot came from.",
		section = appearanceSection,
		position = 4
	)
	default boolean showSourceAccent()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "backgroundColor",
		name = "Background",
		description = "Card background colour.",
		section = appearanceSection,
		position = 5
	)
	default Color backgroundColor()
	{
		return new Color(60, 42, 28, 205);
	}

	@Alpha
	@ConfigItem(
		keyName = "borderColor",
		name = "Border",
		description = "Card border colour.",
		section = appearanceSection,
		position = 6
	)
	default Color borderColor()
	{
		return new Color(116, 95, 60, 255);
	}

	@Alpha
	@ConfigItem(
		keyName = "nameColor",
		name = "Player name",
		description = "Colour of the player name on the card.",
		section = appearanceSection,
		position = 7
	)
	default Color nameColor()
	{
		return new Color(255, 200, 90);
	}

	@Alpha
	@ConfigItem(
		keyName = "itemColor",
		name = "Item name",
		description = "Colour of the item name on the card.",
		section = appearanceSection,
		position = 8
	)
	default Color itemColor()
	{
		return Color.WHITE;
	}

	@Alpha
	@ConfigItem(
		keyName = "bossAccentColor",
		name = "Boss accent",
		description = "Accent colour for boss and raid loot.",
		section = appearanceSection,
		position = 9
	)
	default Color bossAccentColor()
	{
		return new Color(239, 32, 255);
	}

	@Alpha
	@ConfigItem(
		keyName = "groupAccentColor",
		name = "Group accent",
		description = "Accent colour for Group Ironman broadcasts.",
		section = appearanceSection,
		position = 10
	)
	default Color groupAccentColor()
	{
		return new Color(75, 156, 255);
	}

	@Alpha
	@ConfigItem(
		keyName = "ownAccentColor",
		name = "Own loot accent",
		description = "Accent colour for your own drops.",
		section = appearanceSection,
		position = 11
	)
	default Color ownAccentColor()
	{
		return new Color(88, 200, 120);
	}

	// --- Timing & motion -------------------------------------------------------

	@Range(min = 2, max = 60)
	@Units(Units.SECONDS)
	@ConfigItem(
		keyName = "displaySeconds",
		name = "Display time",
		description = "How long a card stays on screen before it fades out.",
		section = timingSection,
		position = 0
	)
	default int displaySeconds()
	{
		return 4;
	}

	@Range(min = 0, max = 3000)
	@Units(Units.MILLISECONDS)
	@ConfigItem(
		keyName = "fadeMillis",
		name = "Fade time",
		description = "How long the fade out takes. 0 makes cards disappear instantly.",
		section = timingSection,
		position = 1
	)
	default int fadeMillis()
	{
		return 500;
	}

	@ConfigItem(
		keyName = "slideIn",
		name = "Slide in",
		description = "Slide new cards in from the left instead of just fading them in.",
		section = timingSection,
		position = 2
	)
	default boolean slideIn()
	{
		return true;
	}

	// --- Positioning & debug ---------------------------------------------------

	@ConfigItem(
		keyName = "previewCard",
		name = "Preview card",
		description = "Show a sample card so the stack can be positioned while no loot is on screen.<br>"
			+ "Hold Alt and drag it, then turn this back off.",
		section = positioningSection,
		position = 0
	)
	default boolean previewCard()
	{
		return false;
	}

	@ConfigItem(
		keyName = "previewOnAlt",
		name = "Preview while holding Alt",
		description = "Show the sample card whenever Alt is held, so the stack can be grabbed even when empty.",
		section = positioningSection,
		position = 1
	)
	default boolean previewOnAlt()
	{
		return true;
	}

	@ConfigItem(
		keyName = "debugLogUnmatched",
		name = "Log every chat line",
		description = "Write every chat line to the client log, not just the loot-looking ones this "
			+ "plugin failed to recognise - those are always logged. Only useful when a broadcast is "
			+ "not being picked up and its wording needs identifying.",
		section = positioningSection,
		position = 2
	)
	default boolean debugLogUnmatched()
	{
		return false;
	}
}
