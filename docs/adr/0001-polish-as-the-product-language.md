# ADR-0001 — Polish for the product, English for the engineering

- **Status:** Accepted
- **Context:** The digest is written in Polish, the prompts are in Polish, the documentation is in
  English, and until this was written down nobody could say whether that was a decision or an
  accident. The audit (F14) found both languages inside single files and inside single comments,
  which is what an undecided question looks like in a codebase.

## Decision

Two languages, split by audience rather than by file.

**Polish** is the language of everything the reader sees and everything that produces it: the
rendered digest, the subject line, the alert emails, the system prompt, the interest profile, and
the comments in `application.yaml`, which is the file the author tunes while thinking about the
product.

**English** is the language of everything an engineer reads: type names, method names, Javadoc,
inline comments in Java, ADRs, `README.md` and `docs/`.

Where the two meet — a Java constant holding a Polish string, a Javadoc sentence explaining a
Polish heading — the code stays English and the string stays Polish. A comment does not switch
language to match the literal it describes.

## Consequences

The product can be Polish without the repository being unreadable to anyone who is not. A reviewer
reads English throughout; a reader receives Polish throughout; the translation boundary is exactly
the string literals and the prompt files.

The cost is that a Polish user-facing string sits inside an English-documented method, which looks
inconsistent in a diff until you know the rule. That is the trade this ADR exists to make explicit:
the alternative — English output — would make the digest worse for its one reader, and the
alternative — Polish Javadoc — would close the repository to the audience the showcase is for.

Mixed-language *sentences* remain a defect. "Zwraca the first matching signal" is not covered by
this decision and is fixed on sight.
