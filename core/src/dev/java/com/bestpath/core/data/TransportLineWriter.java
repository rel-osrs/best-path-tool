package com.bestpath.core.data;

/**
 * Writes transport lines of {@code transports.tsv}, as {@link TransportLine#parse} reads them (used when the data is
 * made).
 */
public final class TransportLineWriter
{
	private TransportLineWriter()
	{
	}

	/** The line (without a line break). */
	public static String write(TransportLine t)
	{
		StringBuilder flags = new StringBuilder();
		flags.append(t.isQuestOnly() ? "q" : "").append(t.isCanFail() ? "f" : "").append(t.isCooldown() ? "c" : "")
			.append(t.ticksEstimate ? "e" : "").append(t.requirementsBack != null ? "b" : "")
			.append(t.getLargestBoat() > 0 ? "s" + t.getLargestBoat() : "");
		MenuText menu = t.getMenu();
		MenuText menuBack = t.getMenuBack();
		return String.join("\t", t.getId(), t.getKind().name(), t.getName(), t.getCategory(),
			t.getKind() == TransportLine.Kind.TELEPORT ? "" : t.getStartX() + "," + t.getStartY() + "," + t.getStartPlane(),
			t.getEndX() + "," + t.getEndY() + "," + t.getEndPlane(), String.valueOf(t.getTicks()), flags, t.getNetwork(),
			t.getRequirements(), t.requirementsBack == null ? "" : t.requirementsBack, t.getTeleportCategory(),
			t.getWildernessLimit() == null ? "" : String.valueOf(t.getWildernessLimit()),
			t.getObjectId() < 0 ? "" : t.getObjectId() + "@" + t.getObjectX() + "," + t.getObjectY() + "," + t.getObjectPlane(),
			t.getPlayerNote(), menu == null ? "" : menu.format(), menuBack == null ? "" : menuBack.format());
	}
}
