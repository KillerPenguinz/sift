/**
 * Sift OAuth token-exchange proxy + redirect bounce.
 *
 * Notion only accepts https redirect URIs, so a native app cannot use its sift://oauth
 * scheme directly. This worker registers as the https redirect and bounces the browser to
 * the app scheme, and it performs the confidential-client token exchange so the client
 * secret never ships in the app.
 *
 * Routes:
 *   GET  /oauth/callback  -> HTML page that opens sift://oauth?<same query> (code + state)
 *   POST (any other path) -> token exchange: forwards { grant_type, code, redirect_uri }
 *                            to Notion with HTTP Basic auth, returns Notion's token JSON.
 *
 * Secrets are environment bindings: NOTION_CLIENT_ID, NOTION_CLIENT_SECRET.
 */
const APP_REDIRECT = "sift://oauth";

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "GET" && url.pathname.endsWith("/oauth/callback")) {
      const error = url.searchParams.get("error");
      if (error) {
        return errorPage(
          error === "access_denied"
            ? "You cancelled the connection. You can close this page and try again in Sift."
            : `Notion returned an error: ${error}. Close this page and try again in Sift.`,
        );
      }

      const target = APP_REDIRECT + url.search;
      return bouncePage(target);
    }

    if (request.method !== "POST") {
      return json({ error: "method_not_allowed" }, 405);
    }

    let body;
    try {
      body = await request.json();
    } catch {
      return json({ error: "invalid_json" }, 400);
    }
    if (!body || typeof body.code !== "string") {
      return json({ error: "missing_code" }, 400);
    }

    const basic = btoa(`${env.NOTION_CLIENT_ID}:${env.NOTION_CLIENT_SECRET}`);
    const notionResponse = await fetch("https://api.notion.com/v1/oauth/token", {
      method: "POST",
      headers: {
        Authorization: `Basic ${basic}`,
        "Content-Type": "application/json",
        "Notion-Version": "2025-09-03",
      },
      body: JSON.stringify({
        grant_type: body.grant_type || "authorization_code",
        code: body.code,
        redirect_uri: body.redirect_uri,
      }),
    });

    const text = await notionResponse.text();
    return new Response(text, {
      status: notionResponse.status,
      headers: { "Content-Type": "application/json" },
    });
  },
};

function json(obj, status) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

/**
 * A brief HTML page that immediately deep-links back to the app, with a manual fallback
 * link in case the scheme redirect does not fire (e.g. app not installed).
 */
function bouncePage(target) {
  return new Response(
    `<!DOCTYPE html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width">
<title>Returning to Sift</title>
<style>
  body { font-family: -apple-system, system-ui, sans-serif; text-align: center;
         padding: 60px 24px; color: #333; background: #fafafa; }
  a { color: #2563eb; }
</style>
</head><body>
<p>Returning to Sift&hellip;</p>
<p style="font-size:14px;color:#888">If the app did not open, <a href="${target}">tap here</a>.</p>
<script>location.href = ${JSON.stringify(target)};</script>
</body></html>`,
    { status: 200, headers: { "Content-Type": "text/html; charset=utf-8" } },
  );
}

function errorPage(message) {
  return new Response(
    `<!DOCTYPE html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width">
<title>Sift</title>
<style>
  body { font-family: -apple-system, system-ui, sans-serif; text-align: center;
         padding: 60px 24px; color: #333; background: #fafafa; }
</style>
</head><body>
<p>${message}</p>
</body></html>`,
    { status: 200, headers: { "Content-Type": "text/html; charset=utf-8" } },
  );
}
