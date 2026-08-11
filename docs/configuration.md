# Configuration

All tuneable parameters live in
[`src/main/resources/application.yaml`](../src/main/resources/application.yaml)
under the `report:` prefix.

## Environment override convention

Any value written as `"${VAR:default}"` in `application.yaml` reads from the environment
first and falls back to `default` (or empty). **All other keys below are baked into the
YAML** and require a code change plus a redeploy to override.

The keys currently accepting `${ENV}`:

`OPENAI_API_KEY` · `TWITTER_BEARER_TOKEN` · `RESEND_API_KEY` · `DIGEST_FROM_EMAIL` ·
`DIGEST_TO_EMAIL` · `PRODUCTHUNT_DEVELOPER_TOKEN` · `YOUTUBE_API_KEY` ·
`FEEDBACK_RECEIVER_URL` · `FEEDBACK_SIGNING_SECRET` · plus the three Supabase datasource
keys (`SUPABASE_DB_URL`, `SUPABASE_DB_USERNAME`, `SUPABASE_DB_PASSWORD`).

Tuning thresholds — min-score, lookback windows, limits — are **not** env-overridable by
default. They are project-policy defaults, not per-environment knobs.

## Keys

| Key                            | Default                  | Description                                        |
|--------------------------------|--------------------------|----------------------------------------------------|
| `twitter.max-calls-per-run`    | `10`                     | Hard ceiling on X API search calls per run (config-drift budget guard) |
| `report.pre-scoring.enabled`   | `true`                   | gpt-4o-mini triage of prompt candidates before the gpt-4o call |
| `report.pre-scoring.keep`      | `50`                     | How many of the ~100 selected items reach gpt-4o (pre-score ≥ 90 is immune) |
| `report.history.enabled`       | `true`                   | Read past editions back from `reports` (trend memory, weekly recap, yield ledger) |
| `report.history.lookback-days` | `21`                     | How far back report history is read (≈ 9 editions at Mon/Wed/Fri) |
| `report.watchlist.enabled`     | `true`                   | Render the 🎯 radar block with guaranteed coverage |
| `report.watchlist.technologies`| (10 entries)             | Technologies that always get a line — including `0 wzmianek` |
| `report.reader-profile.enabled`| `true`                   | Distil and inject the living reader model |
| `report.reader-profile.min-votes` | `10`                  | Clicks required before a profile is distilled at all |
| `report.reader-profile.refresh-days` | `7`                | How often the profile is re-distilled (one gpt-4o-mini call) |
| `report.reader-profile.hypothesis-ttl-days` | `60`        | How long a hypothesis survives without fresh confirmation |
| `twitter.min-faves`            | `3`                      | Server-side `min_faves:N` floor appended to queries (enabled; `0` = off). Tier-dependent operator — if your X tier rejects it the source fails gracefully |
| `research.days-back`           | `4`                      | Tweet age window (days) — 96h covers the Fri→Mon gap |
| `research.min-likes`           | `3`                      | Minimum likes for tweets                           |
| `dedup.enabled`                | `true`                   | Drop items already published in recent editions    |
| `dedup.lookback-days`          | `10`                     | Days of prior editions to dedup against (≥ widest window) |
| `interest-profile.persona`     | (CV-derived)             | Reader profile, injected into the synthesis + tech-demand prompts (single source of truth) |
| `interest-profile.relevance-keywords` | ~95 terms         | Keyword set driving the client-side tweet relevance filter |
| `feedback.enabled`             | `true`                   | Act on reader feedback: suppress 👎 URLs **and** nudge per-source weights from net votes |
| `feedback.lookback-days`       | `30`                     | Window for both down-vote suppression and per-source net-vote nudging |
| `feedback.receiver-url`        | `${FEEDBACK_RECEIVER_URL:}` | External endpoint the email 👍/👎 links point at; blank = no links rendered |
| `feedback.signing-secret`      | `${FEEDBACK_SIGNING_SECRET:}` | Shared with the receiver; set = links carry `&sig=` HMAC; blank = unsigned links |
| `hacker-news.keywords`         | `[ai, llm, java, ...]`   | HN search keywords (parallel single-term queries)  |
| `hacker-news.min-score`        | `40`                     | Minimum HN points                                  |
| `hacker-news.limit`            | `15`                     | Max HN items                                       |
| `hacker-news.lookback-hours`   | `80`                     | HN post age window                                 |
| `github.query`                 | `(topic:ai OR topic:machine-learning OR topic:llm)` | GitHub search query              |
| `github.limit`                 | `8`                      | Max GitHub repos                                   |
| `github.lookback-days`         | `4`                      | GitHub `pushed:>=` window                          |
| `rss.limit`                    | `10`                     | Max items per RSS feed                             |
| `rss.lookback-hours`           | `80`                     | RSS item age window                                |
| `reddit.min-score`             | `30`                     | Minimum Reddit upvotes                             |
| `reddit.limit`                 | `15`                     | Max posts per subreddit                            |
| `social.limit`                 | `15`                     | Max social posts (Bluesky + Mastodon, after min-likes) |
| `social.min-likes`             | `5`                      | Min likes/favs for a social post                   |
| `social.bluesky.handles`       | (list)                   | Bluesky handles to pull author-feeds from          |
| `social.mastodon.instance-url` | `https://fosstodon.org`  | Mastodon instance for hashtag timelines            |
| `social.mastodon.hashtags`     | `[java, quarkus, ...]`   | Mastodon hashtags to follow                        |
| `arxiv.max-results`            | `20`                     | Max arXiv papers                                   |
| `arxiv.lookback-hours`         | `80`                     | arXiv paper age window                             |
| `github-releases.lookback-hours` | `80`                   | GitHub Releases age window                         |
| `hugging-face.limit`           | `30`                     | Max HF trending models per fetch                   |
| `hugging-face.min-likes`       | `3`                      | Min HF likes (OR `min-downloads` to qualify)       |
| `hugging-face.min-downloads`   | `50`                     | Min HF downloads (OR `min-likes` to qualify)       |
| `product-hunt.min-votes`       | `100`                    | Min Product Hunt upvotes                           |
| `product-hunt.lookback-hours`  | `80`                     | Product Hunt launch age window                     |
| `security-advisories.lookback-hours` | `72`               | Security Advisory age window                       |
| `security-advisories.limit`    | `20`                     | Max advisories fetched per run                     |
| `tech-demand.enabled`          | `true`                   | Toggle the 📈 tech-demand pulse                    |
| `tech-demand.lookback-days`    | `7`                      | Render only when the HN Who-is-hiring thread is this fresh |
| `tech-demand.max-comments`     | `1000`                   | Cap on hiring posts (comments) analyzed            |
| `tech-demand.max-technologies` | `8`                      | Top-N technologies shown in the ranking            |
| `tech-demand.min-mentions`     | `3`                      | Ignore technologies below this many mentions       |
| `tech-demand.technologies`     | stack + market list      | Vocabulary counted in hiring posts (configurable)  |
| `tech-demand.priority-technologies` | JVM + Python core   | Reader's stack, shown on the „Twój stack" line     |
| `open-jdk.lookback-days`       | `10`                     | OpenJDK JEP age window                             |
| `cncf-landscape.lookback-days` | `7`                      | CNCF landscape change window                       |
| `technology-radar.lookback-months` | `6`                  | Tech Radar edition age window                      |
| `conference-talks.lookback-days`   | `10`                 | YouTube talks age window                           |
| `conference-talks.max-results` | `10`                     | Max items per channel                              |
| `lab-announcements.lookback-hours` | `80`                 | Window for new posts on AI-lab blogs (Anthropic, OpenAI, Google) |
| `lab-announcements.post-fetch-limit` | `15`               | Max post pages fetched per JSONLD source (listings have no dates) |
| `lab-announcements.sources`    | 5 sources                | Per-source `name` + `strategy` (SANITY/JSONLD/OPENAI_DEV) + `listing-url` |

## Related

- What these thresholds feed → [docs/how-it-works.md](how-it-works.md)
- Which of these become GitHub Actions secrets → [docs/running-locally.md](running-locally.md#github-actions)
