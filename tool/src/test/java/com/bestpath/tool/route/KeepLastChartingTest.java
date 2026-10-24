package com.bestpath.tool.route;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.bestpath.core.data.PluginData;
import com.bestpath.tool.cache.Backups;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** When a game update changes what the charting import reads, the export keeps the plugin's charting file as it is. */
class KeepLastChartingTest
{
	@TempDir
	File ws;

	@Test
	void theEarlierFileIsKeptUnchangedAndReadsBack() throws Exception
	{
		File real = new File("../" + Backups.PLUGIN_DATA + "/" + PluginData.CHARTING);
		assumeTrue(real.isFile(), "run from the tool folder of the workspace");
		Path copy = new File(ws, Backups.PLUGIN_DATA + "/" + PluginData.CHARTING).toPath();
		Files.createDirectories(copy.getParent());
		Files.copy(real.toPath(), copy);

		PluginExportCli.Built b = new PluginExportCli.Built();
		PluginExportCli.keepLastCharting(b, ws, new IllegalStateException("client script 9001 is not shaped as expected"));

		assertArrayEquals(Files.readAllBytes(real.toPath()), b.chartingKept);
		assertEquals(com.bestpath.core.data.ChartingTasks.parse(Files.readAllLines(real.toPath())).getTasks().size(),
			b.charting.getTasks().size());
	}

	@Test
	void withNoEarlierFileThereIsNoCharting() throws Exception
	{
		PluginExportCli.Built b = new PluginExportCli.Built();
		PluginExportCli.keepLastCharting(b, ws, new IllegalStateException("table 154 is not in the cache"));
		assertNull(b.chartingKept);
		assertNull(b.charting);
	}
}
