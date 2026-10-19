// F6 — the feedback receiver: the one piece of the loop that cannot live in the batch.
//
// PulseDigest never serves HTTP (ADR-0002, ADR-0006): it is a single-shot process that exits. But a
// 👍 in an email arrives hours later, as a GET from whatever client the reader opened the mail in, so
// something has to be listening. That something is this function, deployed to Supabase Edge
// Functions, and until 2026-10-19 its source was nowhere — the loop's only untracked component.
//
// Deploy:
//   supabase functions deploy feedback --no-verify-jwt
//   supabase secrets set FEEDBACK_SIGNING_SECRET=<the same value the digest signs with>
//
// --no-verify-jwt is required and is not a shortcut: the caller is a mail client following a link,
// which carries no Supabase JWT. What authenticates the request is the HMAC in `sig`, below.
//
// See receiver/README.md for the contract and for what is verified where.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.118.0";

const VOTES = new Map([
  ["up", "UP"],
  ["down", "DOWN"],
  ["save", "SAVE"],
]);

/** Unique-violation. The partial index on (item_url, edition) is doing its job, so this is success. */
const UNIQUE_VIOLATION = "23505";

const supabase = createClient(
  Deno.env.get("SUPABASE_URL")!,
  // Service role: this function writes a row for an unauthenticated reader, which no anon key may do.
  // It is never exposed to the client — the reader's browser only ever sees the HTML at the bottom.
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
);

const signingSecret = Deno.env.get("FEEDBACK_SIGNING_SECRET") ?? "";

Deno.serve(async (request: Request) => {
  const params = new URL(request.url).searchParams;
  const itemUrl = params.get("url") ?? "";
  const rawVote = params.get("vote") ?? "";
  const source = params.get("source") ?? "";
  const category = params.get("category") ?? "";
  const edition = params.get("edition") ?? "";
  const signature = params.get("sig") ?? "";

  // The batch emits lowercase; the column is CHECK (vote IN ('UP','DOWN','SAVE')) and both reading
  // queries match on the uppercase form. Normalising here is this function's defining obligation —
  // FeedbackVoteContractIT in the repository exists because nothing else states it.
  const vote = VOTES.get(rawVote.toLowerCase());
  if (!vote || !itemUrl) {
    return page(400, "Nieprawidłowy link.");
  }

  if (signingSecret) {
    const canonical = [itemUrl, rawVote.toLowerCase(), source, category, edition].join("|");
    if (!await signatureMatches(canonical, signature)) {
      // Deliberately indistinguishable from the 400 above: a verifier that explains which part
      // failed is a verifier that helps someone fix a forgery.
      return page(400, "Nieprawidłowy link.");
    }
  }

  const { error } = await supabase.from("feedback").insert({
    item_url: itemUrl,
    source: source || null,
    category: category || null,
    edition: edition || null,
    vote,
  });

  if (error && error.code !== UNIQUE_VIOLATION) {
    console.error("feedback insert failed", { code: error.code, message: error.message });
    return page(500, "Nie udało się zapisać głosu. Spróbuj ponownie później.");
  }

  // A duplicate reads as success on purpose. A mail scanner prefetching all three links, or a reader
  // clicking twice, must not look like a failure to the reader who clicked — and the first vote is
  // the one that stands, enforced by the index rather than by this code.
  return page(200, confirmation(vote));
});

/**
 * Constant-time comparison of the expected HMAC with the one in the link. `timingSafeEqual` on raw
 * bytes rather than `===` on strings: a short-circuiting comparison leaks, one byte at a time, how
 * much of a guessed signature was right.
 */
async function signatureMatches(canonical: string, provided: string): Promise<boolean> {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(signingSecret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const mac = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(canonical));
  // URL-safe base64 without padding — the form HmacSigner produces on the Java side.
  const expected = btoa(String.fromCharCode(...new Uint8Array(mac)))
    .replaceAll("+", "-")
    .replaceAll("/", "_")
    .replaceAll("=", "");
  return timingSafeEqual(expected, provided);
}

function timingSafeEqual(a: string, b: string): boolean {
  const left = new TextEncoder().encode(a);
  const right = new TextEncoder().encode(b);
  // Lengths are public (the signature's length is fixed), so comparing them early leaks nothing.
  if (left.length !== right.length) {
    return false;
  }
  let difference = 0;
  for (let i = 0; i < left.length; i++) {
    difference |= left[i] ^ right[i];
  }
  return difference === 0;
}

function confirmation(vote: string): string {
  switch (vote) {
    case "UP":
      return "Zapisane: więcej takich. 👍";
    case "DOWN":
      return "Zapisane: mniej takich. 👎";
    default:
      return "Zapisane na później.";
  }
}

/** A page, not JSON: the caller is a person who clicked a link in an email. */
function page(status: number, message: string): Response {
  const body = `<!doctype html>
<html lang="pl"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>PulseDigest</title></head>
<body style="font:16px/1.5 -apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;
             max-width:32rem;margin:4rem auto;padding:0 1rem;color:#111">
<p>${message}</p>
<p style="color:#6b7280;font-size:14px">Możesz zamknąć tę kartę.</p>
</body></html>`;
  return new Response(body, {
    status,
    headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-store" },
  });
}
