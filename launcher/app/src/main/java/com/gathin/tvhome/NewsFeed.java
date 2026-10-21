package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.util.Xml;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The news row on Home: RSS headlines from the owner's sources, one card each, mixed round-robin
 * so a small regional feed is not drowned by the big ones. Sources are plain RSS 2.0 / Atom URLs
 * kept in Config.SETTINGS_NAME (news_source_&lt;key&gt; on/off, news_custom_url extra feed); every
 * source is on by default, so a fresh install shows the whole mix. The fetched mix is cached to a
 * file for 30 minutes, so Home is instant and a dead network only hides the row instead of
 * blocking the launcher. Fetching happens off the main thread; nothing here touches the UI.
 */
final class NewsFeed {
    interface Listener { void onHeadlines(List<Headline> headlines); }

    /** One headline: what it says, where to read it, which source it came from, when, and its lead photo. */
    static final class Headline {
        final String title;
        final String link;
        final String source;
        final long published;   // epoch ms, 0 when the feed gave no parseable date
        final String image;     // lead photo URL (media:content / enclosure / an <img> in the summary), or ""

        Headline(String title, String link, String source, long published, String image) {
            this.title = title;
            this.link = link;
            this.source = source;
            this.published = published;
            this.image = image == null ? "" : image;
        }
    }

    /** One RSS source of the mix; nameRes 0 means a custom feed, whose URL is its own name. */
    static final class Source {
        final String key;       // preference suffix: news_source_<key>
        final int nameRes;
        final String url;

        Source(String key, int nameRes, String url) {
            this.key = key;
            this.nameRes = nameRes;
            this.url = url;
        }

        String name(Context ctx) {
            if (nameRes != 0) return ctx.getString(nameRes);
            String host = Uri.parse(url).getHost();
            return host == null ? url : host.replaceFirst("^www\\.", "");
        }
    }

    /** The built-in mix, all on unless the owner turned one off. */
    static List<Source> catalog() {
        return Arrays.asList(
                new Source("leouve", R.string.news_leouve, "https://leouve.com.br/feed/"),
                new Source("g1", R.string.news_g1, "https://g1.globo.com/rss/g1/"),
                new Source("uol", R.string.news_uol, "https://rss.uol.com.br/feed/noticias.xml"),
                new Source("folha", R.string.news_folha, "https://feeds.folha.uol.com.br/folha/cotidiano/rss091.xml"),
                new Source("sul21", R.string.news_sul21, "https://www.sul21.com.br/feed/"));
    }

    private static final String CACHE_NAME = "news_cache.json";
    private static final long REFRESH_MS = 30 * 60 * 1000L;          // refetch after 30 minutes
    private static final int CONNECT_TIMEOUT_MS = 6000, READ_TIMEOUT_MS = 8000;
    private static final int PER_SOURCE = 8, MAX_HEADLINES = 28;     // cards in the row
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AtomicBoolean FETCHING = new AtomicBoolean();

    private NewsFeed() {}

    /** The prefs keys, all inside the launcher's own file. */
    static boolean enabled(SharedPreferences choices) {
        return choices.getBoolean("news_enabled", true);
    }

    static boolean sourceOn(SharedPreferences choices, Source s) {
        return choices.getBoolean("news_source_" + s.key, true);
    }

    static String customUrl(SharedPreferences choices) {
        return choices.getString("news_custom_url", "");
    }

    /** The cached mix, whatever its age; Home shows it at once while a refresh runs. */
    static List<Headline> cached(Context ctx) {
        return readCache(new File(ctx.getFilesDir(), CACHE_NAME));
    }

    /** True when the cache is gone or over REFRESH_MS old; the row refetches then. */
    static boolean stale(Context ctx) {
        File file = new File(ctx.getFilesDir(), CACHE_NAME);
        return !file.exists() || System.currentTimeMillis() - file.lastModified() > REFRESH_MS;
    }

    /** Drops the cached mix, so the next Home fetches the owner's sources again (settings changed). */
    static void clearCache(Context ctx) {
        //noinspection ResultOfMethodCallIgnored
        new File(ctx.getFilesDir(), CACHE_NAME).delete();
    }

    /** Fetches the enabled mix off the main thread; the listener runs on it. One flight at a time. */
    static void fetch(Context ctx, Listener listener) {
        if (!FETCHING.compareAndSet(false, true)) return;
        Context app = ctx.getApplicationContext();
        new Thread(() -> {
            List<Headline> mix = fetchMix(app);
            File cache = new File(app.getFilesDir(), CACHE_NAME);
            if (!mix.isEmpty()) writeCache(cache, mix);
            else mix = readCache(cache);     // offline: yesterday's headlines beat none
            List<Headline> result = mix;
            MAIN.post(() -> {
                FETCHING.set(false);
                listener.onHeadlines(result);
            });
        }, "news").start();
    }

    /** The enabled sources, fetched separately and interleaved one headline each at a time. */
    private static List<Headline> fetchMix(Context ctx) {
        SharedPreferences choices = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        List<List<Headline>> perSource = new ArrayList<>();
        for (Source s : catalog()) {
            if (!sourceOn(choices, s)) continue;
            List<Headline> got = fetchSource(ctx, s);
            if (!got.isEmpty()) perSource.add(got);
        }
        String custom = customUrl(choices);
        if (!custom.isEmpty()) {
            List<Headline> got = fetchSource(ctx, new Source("custom", 0, custom));
            if (!got.isEmpty()) perSource.add(got);
        }
        List<Headline> mix = new ArrayList<>();
        for (int i = 0; mix.size() < MAX_HEADLINES; i++) {
            boolean any = false;
            for (List<Headline> source : perSource) {
                if (i >= source.size()) continue;
                any = true;
                mix.add(source.get(i));
                if (mix.size() == MAX_HEADLINES) break;
            }
            if (!any) break;
        }
        return mix;
    }

    /** One feed's headlines, newest first; an unreachable or broken feed simply brings none. */
    private static List<Headline> fetchSource(Context ctx, Source source) {
        List<Headline> out = new ArrayList<>();
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(source.url).openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) NewsRow/1.0");
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) return out;
            XmlPullParser p = Xml.newPullParser();
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            // Feeds are not always UTF-8 (UOL is ISO-8859-1): honour the charset the server declared,
            // else the XML prolog, else UTF-8. Reading it wrong turns ç/ã/º into tofu boxes on screen.
            p.setInput(conn.getInputStream(), charsetOf(conn.getContentType()));
            String name = source.name(ctx);
            boolean inItem = false;
            String title = null, link = null, date = null, image = null, summary = null;
            for (int e = p.getEventType(); e != XmlPullParser.END_DOCUMENT && out.size() < PER_SOURCE; e = p.next()) {
                String tag = p.getName();
                if (e == XmlPullParser.START_TAG) {
                    if ("item".equals(tag) || "entry".equals(tag)) {
                        inItem = true;
                        title = null; link = null; date = null; image = null; summary = null;
                    } else if (inItem && "title".equals(tag)) {
                        title = text(p);
                    } else if (inItem && "link".equals(tag)) {
                        // RSS keeps the URL as text, Atom as the href attribute.
                        String href = p.getAttributeValue(null, "href");
                        link = href != null ? href : text(p);
                    } else if (inItem && date == null
                            && ("pubDate".equals(tag) || "published".equals(tag) || "updated".equals(tag))) {
                        date = text(p);
                    } else if (inItem && image == null) {
                        // The lead photo the full-screen view shows behind the headline.
                        image = leadImage(tag, p);
                        if (image == null && ("description".equals(tag) || "summary".equals(tag)
                                || "encoded".equals(tag) || "content".equals(tag))) {
                            summary = text(p);         // may hold an <img>, picked up at the item's end
                        }
                    }
                } else if (e == XmlPullParser.END_TAG && ("item".equals(tag) || "entry".equals(tag))) {
                    inItem = false;
                    if (title != null && !title.isEmpty()) {
                        title = clean(title);
                        if (!title.isEmpty()) {
                            if (image == null) image = firstImgInHtml(summary);
                            out.add(new Headline(title, link == null ? "" : link.trim(), name,
                                    parseDate(date), image == null ? "" : image.trim()));
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // A feed that is down, moved or not RSS at all simply brings nothing.
        } finally {
            if (conn != null) conn.disconnect();
        }
        return out;
    }

    /** The charset a feed declared in its Content-Type, defaulting to UTF-8 when it did not. */
    static String charsetOf(String contentType) {
        if (contentType == null) return "UTF-8";
        for (String part : contentType.split(";")) {
            String t = part.trim();
            int eq = t.indexOf('=');
            if (eq > 0 && "charset".equalsIgnoreCase(t.substring(0, eq).trim())) {
                String cs = t.substring(eq + 1).trim().replace("\"", "");
                if (!cs.isEmpty()) return cs;
            }
        }
        return "UTF-8";
    }

    /** The image a media tag points at, or null when the tag carries none (only the first one counts). */
    private static String leadImage(String tag, XmlPullParser p) {
        if ("media:content".equals(tag) || "media:thumbnail".equals(tag) || "enclosure".equals(tag)) {
            String url = p.getAttributeValue(null, "url");
            String type = p.getAttributeValue(null, "type");
            // An <enclosure> can be audio or video; only take the ones that are pictures.
            if (url != null && (type == null || type.startsWith("image"))) return url;
        }
        return null;
    }

    /** The first <img src> in an HTML summary, for feeds that hide the photo there. */
    private static String firstImgInHtml(String html) {
        if (html == null) return null;
        int at = html.indexOf("<img");
        if (at < 0) return null;
        int src = html.indexOf("src=\"", at);
        if (src < 0) return null;
        int start = src + 5;
        int end = html.indexOf('"', start);
        return end > start ? html.substring(start, end) : null;
    }

    /** The text of the element the parser is on, empty when it holds markup or nothing. */
    private static String text(XmlPullParser p) {
        try {
            int e = p.next();
            if (e != XmlPullParser.TEXT) return "";
            String value = p.getText();
            p.next();                       // stand on the element's END_TAG again
            return value;
        } catch (Exception e) {
            return "";
        }
    }

    /** One plain line: markup stripped, entities decoded, run together, capped at 160. */
    private static String clean(String raw) {
        String s = Html.fromHtml(raw, Html.FROM_HTML_MODE_COMPACT).toString();
        s = s.replaceAll("\\s+", " ").trim();
        return s.length() > 160 ? s.substring(0, 157) + "…" : s;
    }

    private static final String[] DATE_PATTERNS = {
            "EEE, dd MMM yyyy HH:mm:ss Z",      // RSS, RFC 822
            "yyyy-MM-dd'T'HH:mm:ssXXX",         // Atom, ISO 8601
            "yyyy-MM-dd'T'HH:mm:ss'Z'"};

    private static long parseDate(String raw) {
        if (raw == null || raw.isEmpty()) return 0;
        raw = raw.trim();
        for (String pattern : DATE_PATTERNS) {
            try {
                Date d = new SimpleDateFormat(pattern, Locale.ENGLISH).parse(raw);
                if (d != null) return d.getTime();
            } catch (Exception ignored) {
                // The next pattern may still match.
            }
        }
        return 0;
    }

    private static void writeCache(File file, List<Headline> headlines) {
        try {
            JSONArray array = new JSONArray();
            for (Headline h : headlines) {
                JSONObject o = new JSONObject();
                o.put("t", h.title);
                o.put("l", h.link);
                o.put("s", h.source);
                o.put("w", h.published);
                o.put("i", h.image);
                array.put(o);
            }
            JSONObject root = new JSONObject();
            root.put("items", array);
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(root.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
            // A full disk only costs the next refresh its head start.
        }
    }

    private static List<Headline> readCache(File file) {
        List<Headline> out = new ArrayList<>();
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[(int) file.length()];
            int got = 0;
            while (got < buf.length) {
                int n = in.read(buf, got, buf.length - got);
                if (n < 0) break;
                got += n;
            }
            JSONArray array = new JSONObject(new String(buf, 0, got, StandardCharsets.UTF_8))
                    .optJSONArray("items");
            if (array == null) return out;
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o != null) out.add(new Headline(
                        o.optString("t"), o.optString("l"), o.optString("s"), o.optLong("w"),
                        o.optString("i")));
            }
        } catch (Exception ignored) {
            // No cache yet, or one this build cannot read: fetch instead.
        }
        return out;
    }

    // ---- lead photos (full-screen view) -------------------------------------------------------

    /** How long a downloaded photo is reused before it is fetched again. */
    private static final long IMAGE_MAX_AGE_MS = 24 * 60 * 60 * 1000L;   // a day

    /**
     * The headline's photo as a local file, downloading it once and keeping it on disk so the
     * full-screen rotation never refetches a picture it already showed. Runs off the main thread;
     * returns null when there is no photo or the network is down (the view then shows the text alone).
     */
    static File imageFile(Context ctx, String url) {
        if (url == null || url.isEmpty()) return null;
        File dir = new File(ctx.getFilesDir(), "news_images");
        // One file per URL, named by a stable hash of it.
        File file = new File(dir, Integer.toHexString(url.hashCode()) + ".img");
        if (file.exists() && System.currentTimeMillis() - file.lastModified() < IMAGE_MAX_AGE_MS) return file;
        HttpURLConnection conn = null;
        try {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) NewsRow/1.0");
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            try (InputStream in = conn.getInputStream();
                 FileOutputStream out = new FileOutputStream(file)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            return file;
        } catch (Exception ignored) {
            // A photo that will not come costs the slide its picture, never its headline.
            //noinspection ResultOfMethodCallIgnored
            file.delete();
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
