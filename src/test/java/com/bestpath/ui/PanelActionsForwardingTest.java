package com.bestpath.ui;

import static org.junit.Assert.assertTrue;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.Test;

/**
 * The sidebar hands its tabs a wrapper around the plugin's {@link PanelActions}. A method the wrapper doesn't override
 * runs the interface's default instead of the plugin's, silently (the Slayer star and "Go to the nearest place" were
 * lost that way): every method must be forwarded.
 */
public class PanelActionsForwardingTest
{
	@Test
	public void theWrapperForwardsEveryAction() throws Exception
	{
		PanelActions plugin = (PanelActions) Proxy.newProxyInstance(PanelActions.class.getClassLoader(),
			new Class<?>[]{PanelActions.class}, (proxy, method, args) -> method.getReturnType() == int.class ? -1
				: method.getReturnType() == boolean.class ? Boolean.FALSE : null);
		Object[] wrapper = new Object[1];
		SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				BestPathPanel panel = new BestPathPanel(plugin, IconProvider.NONE);
				Field tab = BestPathPanel.class.getDeclaredField("settingsTab");
				tab.setAccessible(true);
				Field actions = SettingsTab.class.getDeclaredField("actions");
				actions.setAccessible(true);
				wrapper[0] = actions.get(tab.get(panel));
			}
			catch (ReflectiveOperationException e)
			{
				throw new AssertionError(e);
			}
		});
		List<String> missing = new ArrayList<>();
		for (Method m : PanelActions.class.getMethods())
		{
			if (Modifier.isStatic(m.getModifiers()))
			{
				continue;
			}
			try
			{
				wrapper[0].getClass().getDeclaredMethod(m.getName(), m.getParameterTypes());
			}
			catch (NoSuchMethodException e)
			{
				missing.add(m.getName());
			}
		}
		assertTrue("not forwarded: " + missing, missing.isEmpty());
	}
}
