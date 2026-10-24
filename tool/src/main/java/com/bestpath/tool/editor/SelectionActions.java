package com.bestpath.tool.editor;

import com.bestpath.tool.edit.Edit;
import com.bestpath.tool.edit.TransportRecord;

/**
 * What the Confirm, Delete, Test later and One-way / two-way buttons do to the editor's selected transport or
 * suggestion: each records changes in the edit store and then updates the selection.
 */
final class SelectionActions
{
	private final EditorContext ctx;

	SelectionActions(EditorContext ctx)
	{
		this.ctx = ctx;
	}

	/** See {@link EditorContext#confirmSelected}. */
	void confirm()
	{
		EditorContext.Selection s = ctx.getSelection();
		if (s == null)
		{
			return;
		}
		if (s.teleport != null)
		{
			TransportRecord r = s.draft != null ? s.draft.copy() : ctx.recordFromTeleport(s.teleport);
			testListOff(s.teleport.id());
			ctx.pendTransport(r);
			ctx.select(EditorContext.Selection.ofRecord(r, true), null, false);
			return;
		}
		if (s.candidate != null && (s.suggestion != null || s.draft != null))
		{
			TransportRecord r = s.draft != null ? s.draft.copy()
				: ctx.recordFromCandidate(s.candidate, s.suggestion.getStart(), s.suggestion.getDestX(),
				s.suggestion.getDestY(), s.suggestion.getDestPlane(), "suggestion: " + s.suggestion.getRule());
			testListOff(s.candidate.id());
			ctx.pendTransport(r);
			ctx.select(EditorContext.Selection.ofRecord(r, true), null, false);
		}
		else if (s.record != null)
		{
			testListOff(EditorContext.objectKey(s.record)); // a tested transport: confirmed now
			if (!s.pending)
			{
				return;
			}
			ctx.select(s, null, false);
		}
	}

	/** See {@link EditorContext#deleteSelected}. */
	void delete()
	{
		EditorContext.Selection s = ctx.getSelection();
		if (s == null)
		{
			ctx.deleteChannel();
			return;
		}
		if (s.teleport != null)
		{
			testListOff(s.teleport.id());
			Edit e = new Edit();
			e.type = Edit.Type.REJECT;
			e.target = s.teleport.id();
			e.on = true;
			e.x = s.teleport.x;
			e.y = s.teleport.y;
			e.plane = s.teleport.plane;
			ctx.edits.add(e);
			ctx.select(null, null, false);
			return;
		}
		if (s.candidate != null)
		{
			testListOff(s.candidate.id());
			Edit e = new Edit();
			e.type = Edit.Type.REJECT;
			e.target = s.candidate.id();
			e.on = true;
			e.x = s.candidate.getX();
			e.y = s.candidate.getY();
			e.plane = s.candidate.getPlane();
			ctx.edits.add(e);
		}
		else if (s.record != null)
		{
			deleteRecord(s.record);
		}
		ctx.select(null, null, false);
	}

	/** Deletes a transport: a built-in one with a TRANSPORT_DELETE change, one not built in yet by taking it back. */
	private void deleteRecord(TransportRecord record)
	{
		testListOff(EditorContext.objectKey(record));
		boolean applied = ctx.transports != null && ctx.transports.getConfirmed().containsKey(record.id);
		if (applied)
		{
			Edit e = new Edit();
			e.type = Edit.Type.TRANSPORT_DELETE;
			e.target = record.id;
			e.x = record.endX;
			e.y = record.endY;
			e.plane = record.endPlane;
			ctx.edits.add(e);
		}
		else
		{
			ctx.edits.cancel(transportLike(record.id)); // not built yet: taken back
		}
	}

	/** See {@link EditorContext#testLaterSelected}. */
	void testLater()
	{
		EditorContext.Selection s = ctx.getSelection();
		if (s == null)
		{
			return;
		}
		TransportRecord r;
		String key;
		if (s.teleport != null)
		{
			r = s.draft != null ? s.draft.copy() : ctx.recordFromTeleport(s.teleport);
			key = s.teleport.id();
		}
		else if (s.candidate != null)
		{
			int[] end = s.end();
			r = s.draft != null ? s.draft.copy() : end == null ? null : ctx.recordFromCandidate(s.candidate, s.suggestion != null ? s.suggestion.getStart() : null,
				end[0], end[1], end[2], s.suggestion != null ? "suggestion: " + s.suggestion.getRule() : "candidate");
			key = s.candidate.id();
		}
		else
		{
			r = s.record.copy();
			key = EditorContext.objectKey(s.record);
			boolean applied = ctx.transports != null && ctx.transports.getConfirmed().containsKey(s.record.id);
			if (!applied)
			{
				ctx.edits.cancel(transportLike(s.record.id)); // not built yet: taken back
			}
		}
		Edit e = new Edit();
		e.type = Edit.Type.TEST_LATER;
		e.target = key;
		e.on = true;
		e.transport = r;
		int[] at = r != null ? new int[]{r.endX, r.endY, r.endPlane}
			: new int[]{s.candidate.getX(), s.candidate.getY(), s.candidate.getPlane()};
		e.x = at[0];
		e.y = at[1];
		e.plane = at[2];
		ctx.edits.add(e);
		ctx.select(null, null, false);
	}

	/** See {@link EditorContext#toggleTwoWaySelected}. */
	void toggleTwoWay()
	{
		EditorContext.Selection s = ctx.getSelection();
		if (s == null)
		{
			return;
		}
		if (s.record != null)
		{
			if (!s.record.hasStart() || s.record.kind == TransportRecord.Kind.NETWORK)
			{
				return; // a teleport has no start, and a network stop goes to every other stop
			}
			TransportRecord r = s.record.copy();
			r.kind = otherWay(r.kind);
			ctx.pendTransport(r);
			ctx.select(EditorContext.Selection.ofRecord(r, true), null, false);
		}
		else if (s.candidate != null && (s.draft != null || s.suggestion != null))
		{
			TransportRecord d = s.draft != null ? s.draft.copy()
				: ctx.recordFromCandidate(s.candidate, s.suggestion.getStart(), s.suggestion.getDestX(),
				s.suggestion.getDestY(), s.suggestion.getDestPlane(), "suggestion: " + s.suggestion.getRule());
			if (!d.hasStart())
			{
				return;
			}
			d.kind = otherWay(d.kind);
			ctx.select(EditorContext.Selection.ofDraft(s.candidate, s.suggestion, d), null, false);
		}
	}

	/** Takes {@code key} off the "test in game" list if it is on it. */
	private void testListOff(String key)
	{
		if (ctx.effectiveTestLater().containsKey(key))
		{
			Edit off = new Edit();
			off.type = Edit.Type.TEST_LATER;
			off.target = key;
			off.on = false;
			if (ctx.state != null && ctx.state.getTestLater().containsKey(key))
			{
				ctx.edits.add(off); // on the applied list: a change takes it off
			}
			else
			{
				ctx.edits.cancel(off); // not built in yet: just take it back
			}
		}
	}

	private static TransportRecord.Kind otherWay(TransportRecord.Kind k)
	{
		return k == TransportRecord.Kind.TWO_WAY ? TransportRecord.Kind.ONE_WAY : TransportRecord.Kind.TWO_WAY;
	}

	/** A pattern matching every change (made or deleted) to transport {@code id}, for {@link com.bestpath.tool.edit.EditStore#cancel}. */
	private static Edit transportLike(String id)
	{
		Edit like = new Edit();
		like.type = Edit.Type.TRANSPORT_DELETE;
		like.target = id;
		return like;
	}
}
