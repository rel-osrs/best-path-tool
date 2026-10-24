package com.bestpath.core.sea;

/**
 * What development code reads from a {@link Dock} that the plugin itself never needs: the gangplank or mooring
 * point itself (the sea file stores it, the Data Tool draws it) and checks made when the data is made.
 */
public final class DockDiagnostics
{
	private DockDiagnostics()
	{
	}

	public static Dock.Kind kind(Dock dock)
	{
		return dock.kind;
	}

	/** South-west tile of the gangplank or mooring point. */
	public static int landX(Dock dock)
	{
		return dock.landX;
	}

	public static int landY(Dock dock)
	{
		return dock.landY;
	}

	public static int landPlane(Dock dock)
	{
		return dock.landPlane;
	}

	/** Width (= height) of the gangplank or mooring point in tiles. */
	public static int landSize(Dock dock)
	{
		return dock.landSize;
	}

	/** "Gangplank", "Mooring point". */
	public static String label(Dock.Kind kind)
	{
		return kind.label;
	}

	/** "level 62, The Fremennik Trials" or "level 1". */
	public static String requirementText(Dock dock)
	{
		return "level " + dock.getLevel() + (dock.getQuest() == null ? "" : ", " + dock.getQuest());
	}

	/** Whether sailable water next to the dock's buoy is on the main ocean ({@link SeaPieces#mainPiece()}). */
	public static boolean onMainOcean(Dock dock, SeaPieces pieces)
	{
		if (pieces.mainPiece() < 0)
		{
			return false;
		}
		for (int x = dock.getBuoyX() - 1; x <= dock.getBuoyX() + dock.getBuoySize(); x++)
		{
			for (int y = dock.getBuoyY() - 1; y <= dock.getBuoyY() + dock.getBuoySize(); y++)
			{
				if (pieces.pieceAt(x, y, 0) == pieces.mainPiece())
				{
					return true;
				}
			}
		}
		return false;
	}
}
