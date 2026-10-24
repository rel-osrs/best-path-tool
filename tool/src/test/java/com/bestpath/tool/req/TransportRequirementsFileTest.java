package com.bestpath.tool.req;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.req.Requirement;
import com.bestpath.core.req.RequirementText;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The real {@code data/manual/changes.jsonl}: every transport's requirement lines (as last changed) can be read. */
class TransportRequirementsFileTest
{
	@Test
	void everyRequirementLineParses() throws Exception
	{
		File f = new File("../data/manual/changes.jsonl");
		if (!f.isFile())
		{
			f = new File("data/manual/changes.jsonl");
		}
		Map<String, JsonObject> last = new LinkedHashMap<>();
		for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8))
		{
			if (line.isBlank())
			{
				continue;
			}
			JsonObject e = new JsonParser().parse(line).getAsJsonObject();
			if (e.has("type") && "TRANSPORT".equals(e.get("type").getAsString()) && e.has("transport"))
			{
				JsonObject t = e.getAsJsonObject("transport");
				last.put(t.get("id").getAsString(), t);
			}
		}
		assertTrue(last.size() > 1000, "transports read: " + last.size());
		List<String> bad = new ArrayList<>();
		for (JsonObject t : last.values())
		{
			for (String field : new String[]{"requirements", "requirementsBack"})
			{
				if (!t.has(field) || t.get(field).isJsonNull())
				{
					continue;
				}
				try
				{
					ItemGroupFile.parse(t.get(field).getAsString());
				}
				catch (RequirementText.ParseException ex)
				{
					bad.add(t.get("id").getAsString() + " " + t.get("name").getAsString() + " " + field + ": " + ex.getMessage());
				}
			}
		}
		assertEquals(List.of(), bad);
	}

	@Test
	void houseThingNamesWithAnAmpersand() throws Exception
	{
		Requirement r = ItemGroupFile.parse("one of (house has spirit tree | house has spirit tree & fairy ring)");
		Requirement.AnyOf any = (Requirement.AnyOf) r;
		assertEquals("spirit tree & fairy ring", ((Requirement.House) any.getAlternatives().get(1)).getWhat());
	}
}
