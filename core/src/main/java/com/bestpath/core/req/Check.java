package com.bestpath.core.req;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * The result of checking a requirement against a player: met, met once an
 * item is fetched from the bank, or not met; with what is missing and what
 * the player has to do on the way (take off a hat, wear a ring). Met with the
 * bank: also each thing to take out, with its items and count
 * ({@link #getBankItems()}). And the player answers not given yet that the
 * outcome rests on ({@link #getUnanswered()}): an unanswered question does not
 * stop a route, the step card asks it. Immutable.
 */
public final class Check
{
	/** Ordered from best to worst. */
	public enum Outcome
	{
		MET,
		NEEDS_BANK,
		NOT_MET
	}

	public static final Check MET = new Check(Outcome.MET, List.of(), List.of(), List.of(), List.of());

	private final Outcome outcome;
	private final List<String> missing;
	private final List<String> todo;
	private final List<BankItem> bankItems;
	private final List<String> unanswered;

	private Check(Outcome outcome, List<String> missing, List<String> todo, List<BankItem> bankItems, List<String> unanswered)
	{
		this.outcome = outcome;
		this.missing = missing;
		this.todo = todo;
		this.bankItems = bankItems;
		this.unanswered = unanswered;
	}

	public static Check of(Outcome outcome, List<String> missing, List<String> todo)
	{
		return of(outcome, missing, todo, List.of());
	}

	/** {@code bankItems}: what to take out of the bank (kept only for {@link Outcome#NEEDS_BANK}). */
	public static Check of(Outcome outcome, List<String> missing, List<String> todo, List<BankItem> bankItems)
	{
		return of(outcome, missing, todo, bankItems, List.of());
	}

	/**
	 * As {@link #of(Outcome, List, List, List)}, with the player answers not given yet that this outcome rests on
	 * ({@link #getUnanswered()}; each once, in order).
	 */
	public static Check of(Outcome outcome, List<String> missing, List<String> todo, List<BankItem> bankItems,
		List<String> unanswered)
	{
		if (outcome == Outcome.MET && missing.isEmpty() && todo.isEmpty() && unanswered.isEmpty())
		{
			return MET;
		}
		return new Check(outcome, Collections.unmodifiableList(new ArrayList<>(missing)),
			Collections.unmodifiableList(new ArrayList<>(todo)),
			outcome == Outcome.NEEDS_BANK ? Collections.unmodifiableList(new ArrayList<>(bankItems)) : List.of(),
			unanswered.isEmpty() ? List.of() : Collections.unmodifiableList(new ArrayList<>(new LinkedHashSet<>(unanswered))));
	}

	static Check notMet(String why)
	{
		return of(Outcome.NOT_MET, List.of(why), List.of());
	}

	/** Not met, resting on these answers not given yet (a "not answer ..." whose answer may still be no). */
	static Check notMet(String why, List<String> unanswered)
	{
		return of(Outcome.NOT_MET, List.of(why), List.of(), List.of(), unanswered);
	}

	/** Met only if the player's answer, not given yet, is the one hoped for (the card asks it). */
	static Check metIfAnswered(String answer)
	{
		return of(Outcome.MET, List.of(), List.of(), List.of(), List.of(answer));
	}

	/** Met with the bank: {@code what} in words ("... from the bank"), {@code item} what to take out. */
	static Check bank(String what, BankItem item)
	{
		return of(Outcome.NEEDS_BANK, List.of(what), List.of(), List.of(item));
	}

	static Check todo(String what)
	{
		return of(Outcome.MET, List.of(), List.of(what));
	}

	public Outcome getOutcome()
	{
		return outcome;
	}

	/** True when the player can use it now, without the bank. */
	public boolean isMet()
	{
		return outcome == Outcome.MET;
	}

	/** Not met: what is missing. Needs bank: the items to fetch. */
	public List<String> getMissing()
	{
		return missing;
	}

	/** Things to do before using it (take off a hat, wear a ring). */
	public List<String> getTodo()
	{
		return todo;
	}

	/**
	 * Needs bank: each thing to take out (its words, its items, how many), in the order of {@link #getMissing()};
	 * empty otherwise.
	 */
	public List<BankItem> getBankItems()
	{
		return bankItems;
	}

	/**
	 * The player answers (the data's words, "Spirit tree planted at Port Sarim") not given yet that this outcome
	 * rests on: each is taken as yes until answered, so the outcome holds while each is yes and may
	 * change when one is answered no. Only for a player whose unanswered questions are asked
	 * ({@link PlayerState#isAskUnanswered()}); empty otherwise and when nothing rests on an answer.
	 */
	public List<String> getUnanswered()
	{
		return unanswered;
	}

	@Override
	public String toString()
	{
		return outcome + (missing.isEmpty() ? "" : " missing " + missing) + (todo.isEmpty() ? "" : " todo " + todo)
			+ (unanswered.isEmpty() ? "" : " unanswered " + unanswered);
	}
}
