package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * How many items each paging source returned before filtering (N48).
 *
 * <p>These were five adjacent {@code int} components on {@link ResearchResult}. Five same-typed
 * positional parameters is the one shape in this codebase where the compiler is guaranteed not to
 * help: transposing any two of them compiles cleanly and silently corrupts the stored payload, the
 * footer's "Wybrano N z M" and the yield denominators — a wrong number that looks like a right one.
 *
 * <p>Grouping them into a record is only half the fix, because a record of five ints has the same
 * hazard in its own constructor. {@link #builder()} is the other half: it is how this is built, and
 * it names every value at the call site. The canonical constructor stays available for Jackson and
 * for the handful of places that genuinely want all five at once in a known order.
 *
 * <p>Only these five are counted. The other sources return a complete, unpaged result, so "raw" and
 * "kept" are the same number for them and {@link ResearchResult#rawTotalCount()} reads their sizes
 * directly instead of carrying a count that could disagree with the list beside it.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RawCounts(
        @JsonProperty("tweets") int tweets,
        @JsonProperty("hacker_news") int hackerNews,
        @JsonProperty("github") int github,
        @JsonProperty("rss") int rss,
        @JsonProperty("reddit") int reddit
) {

    /** Nothing fetched — and what an edition stored before this record existed degrades to. */
    public static final RawCounts NONE = new RawCounts(0, 0, 0, 0, 0);

    public static Builder builder() {
        return new Builder();
    }

    @JsonIgnore
    public int total() {
        return tweets + hackerNews + github + rss + reddit;
    }

    /** Names every count at the call site, which is the entire point of this type existing. */
    public static final class Builder {

        private int tweets;
        private int hackerNews;
        private int github;
        private int rss;
        private int reddit;

        private Builder() {
        }

        public Builder tweets(int count) {
            this.tweets = count;
            return this;
        }

        public Builder hackerNews(int count) {
            this.hackerNews = count;
            return this;
        }

        public Builder github(int count) {
            this.github = count;
            return this;
        }

        public Builder rss(int count) {
            this.rss = count;
            return this;
        }

        public Builder reddit(int count) {
            this.reddit = count;
            return this;
        }

        public RawCounts build() {
            return new RawCounts(tweets, hackerNews, github, rss, reddit);
        }
    }
}
