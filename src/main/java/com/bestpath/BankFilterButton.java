package com.bestpath;

import com.bestpath.route.RouteResult;
import com.bestpath.route.RouteStep;
import com.bestpath.route.StepInfo;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;

/**
 * A button in the bank that shows only the items the route asks for from the bank, like Quest Helper's. Works through
 * RuneLite's Bank Tags plugin, as a tag of its own ({@value #TAG}, never saved): {@link TagManager#registerTag} with
 * the route's items, opened and closed with {@link BankTagsService}.
 *
 * <p>Where: the bank's top bar, right of its two small buttons (bank interface 12: the item count at x 3-35, two 21x21
 * buttons at x 34-55 and 58-79, the title centred), so x 82-107. That keeps it clear of Quest Helper's button at
 * x 408-433, by the close button. Shown only while the route has items to take out. Client thread only.
 */
final class BankFilterButton
{
	static final String TAG = "best path";
	static final int X = 82;
	static final int Y = 5;
	static final int SIZE = 25;
	static final String SHOW = "Show route items";
	static final String SHOW_ALL = "Show all items";
	/** The button's menu while Bank Tags is off; the plugin never posts chat messages, so the menu says it instead. */
	static final String NEEDS_BANK_TAGS = "Needs RuneLite's Bank Tags plugin";
	static final String NAME = "<col=ff9040>Best Path</col>";

	private final Client client;
	private final BankTagsService tags;
	private final TagManager tagManager;
	private final Supplier<RouteResult> route;
	private final IntUnaryOperator canonical;
	private final BooleanSupplier bankTagsOn;
	/** The items the route asks for from the bank (every item of each "any of"). */
	private Set<Integer> items = Set.of();
	private Widget background;
	private Widget icon;

	/**
	 * @param canonical   an item as the bank holds it -> the item (placeholders, notes)
	 * @param bankTagsOn  whether RuneLite's Bank Tags plugin is switched on (the filter needs it; while it is off the
	 *                    button's menu says so and a click does nothing)
	 */
	BankFilterButton(Client client, BankTagsService tags, TagManager tagManager, Supplier<RouteResult> route,
		IntUnaryOperator canonical, BooleanSupplier bankTagsOn)
	{
		this.client = client;
		this.tags = tags;
		this.tagManager = tagManager;
		this.route = route;
		this.canonical = canonical;
		this.bankTagsOn = bankTagsOn;
	}

	/** The items a route asks for from the bank: every item of each of its bank steps' take-outs. */
	static Set<Integer> routeItems(RouteResult r)
	{
		Set<Integer> out = new HashSet<>();
		if (r == null)
		{
			return out;
		}
		for (RouteStep s : r.getSteps())
		{
			if (s.getKind() == RouteStep.Kind.BANK)
			{
				for (StepInfo.TakeOut t : s.getInfo().getTakeOut())
				{
					for (int id : t.getIds())
					{
						out.add(id);
					}
				}
			}
		}
		return out;
	}

	/** The bank opened: the button (hidden without route items). */
	void bankOpened()
	{
		Widget parent = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
		if (parent == null)
		{
			return;
		}
		background = graphic(parent, SpriteID.Miscgraphics3.UNKNOWN_BUTTON_SQUARE_SMALL, X, Y, SIZE);
		background.setAction(0, bankTagsOn.getAsBoolean() ? SHOW : NEEDS_BANK_TAGS);
		background.setName(NAME);
		background.setOnOpListener((JavaScriptCallback) e -> toggle());
		background.setHasListener(true);
		background.setNoClickThrough(true);
		icon = graphic(parent, SpriteID.Mapfunction.TRANSPORTATION, X + 3, Y + 3, SIZE - 6);
		routeChanged();
	}

	private static Widget graphic(Widget parent, int sprite, int x, int y, int size)
	{
		Widget w = parent.createChild(-1, WidgetType.GRAPHIC);
		w.setSpriteId(sprite);
		w.setOriginalX(x);
		w.setOriginalY(y);
		w.setOriginalWidth(size);
		w.setOriginalHeight(size);
		w.revalidate();
		return w;
	}

	/** The route changed (or the bank opened): the items, the button shown or not; a filter without items is closed. */
	void routeChanged()
	{
		items = routeItems(route.get());
		boolean any = !items.isEmpty();
		if (isOpen() && !any)
		{
			tags.closeBankTag();
		}
		show(any);
	}

	private void show(boolean visible)
	{
		if (background == null || icon == null)
		{
			return;
		}
		background.setHidden(!visible);
		icon.setHidden(!visible);
		boolean open = isOpen();
		background.setSpriteId(open ? SpriteID.Miscgraphics3.UNKNOWN_BUTTON_SQUARE_SMALL_SELECTED
			: SpriteID.Miscgraphics3.UNKNOWN_BUTTON_SQUARE_SMALL);
		background.setAction(0, !bankTagsOn.getAsBoolean() ? NEEDS_BANK_TAGS : open ? SHOW_ALL : SHOW);
	}

	/** True while the bank shows only the route's items. */
	boolean isOpen()
	{
		return TAG.equals(tags.getActiveTag());
	}

	/** A click: only the route's items, or all of them again. */
	void toggle()
	{
		if (!bankTagsOn.getAsBoolean())
		{
			show(!items.isEmpty()); // its menu says Bank Tags is needed
			return;
		}
		if (isOpen())
		{
			tags.closeBankTag();
		}
		else if (!items.isEmpty())
		{
			// the tag reads the newest items each time the bank asks (the route may change while it is open)
			tagManager.registerTag(TAG, id -> items.contains(canonical.applyAsInt(id)) || items.contains(id));
			tags.openBankTag(TAG, BankTagsService.OPTION_NO_LAYOUT);
		}
		show(!items.isEmpty());
	}

	/** The plugin stopped: the filter closed, the tag and the button gone. */
	void shutDown()
	{
		if (isOpen())
		{
			tags.closeBankTag();
		}
		tagManager.unregisterTag(TAG);
		for (Widget w : new Widget[]{background, icon})
		{
			if (w != null)
			{
				w.setHidden(true);
			}
		}
		background = null;
		icon = null;
	}
}
