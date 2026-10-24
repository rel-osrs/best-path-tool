package com.bestpath.tool.publish;

import com.bestpath.tool.ToolSettings;
import java.io.File;

/**
 * {@code gradlew -p tool publishPlugin [--args="[public folder] [--build]"]}: the same as the Data Tool's "Publish
 * plugin..." ({@link PluginPublisher}). Default folder: "best-path" beside the workspace. --build also builds the
 * public copy on its own.
 */
public final class PluginPublishCli
{
	private PluginPublishCli()
	{
	}

	public static void main(String[] args) throws Exception
	{
		File ws = ToolSettings.findWorkspace();
		File target = PluginPublisher.defaultFolder(ws);
		boolean build = false;
		for (String a : args)
		{
			if (a.equals("--build"))
			{
				build = true;
			}
			else
			{
				target = new File(a);
			}
		}
		PluginPublisher.publish(ws, target, System.out::println);
		if (build && PluginPublisher.buildCheck(target, System.out::println) != 0)
		{
			System.exit(1);
		}
	}
}
