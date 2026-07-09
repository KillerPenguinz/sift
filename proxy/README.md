# Sift OAuth token-exchange proxy

Notion's public OAuth is a confidential-client flow: the token exchange needs the client
secret. That secret must never ship in the Android app (it is extractable from an APK), so
this tiny proxy performs the exchange server side. The app sends the authorization code here;
the proxy adds the secret and returns Notion's token JSON unchanged.

## Contract

`POST /` with JSON body:

```json
{ "grant_type": "authorization_code", "code": "<code>", "redirect_uri": "sift://oauth" }
```

Returns Notion's token response (`access_token`, `workspace_id`, `workspace_name`, `bot_id`).
This matches `OAuthRequestFactory.buildTokenExchangeRequest` / `parseTokenResponse` in `:core`.

## Deploy (Cloudflare Workers)

Run every command **from this `proxy/` folder**. You need Node (you have it) and a free
Cloudflare account. Use `npx wrangler` so nothing has to be installed globally.

1. Log in to Cloudflare (opens a browser):
   ```
   npx wrangler login
   ```
2. Deploy the worker (creates it; first deploy asks you to register a free `workers.dev`
   subdomain):
   ```
   npx wrangler deploy
   ```
   Copy the deployed URL it prints (for example `https://sift-oauth-proxy.<you>.workers.dev`).
3. Set the secrets from your Notion public integration (each prompts you to paste the value):
   ```
   npx wrangler secret put NOTION_CLIENT_ID
   npx wrangler secret put NOTION_CLIENT_SECRET
   ```
4. Put the deployed URL and the client id in `local.properties` (see below).

Any host works (Vercel, Cloud Run, a small Express server); only the contract above matters.
If you use another host, keep the client secret in server env, not in the response or the app.

## Wire the app

Add to `local.properties` (git-ignored, never committed):

```
notion.clientId=<public integration client id>
notion.oauthProxyUrl=https://sift-oauth-proxy.<you>.workers.dev
# optional, debug only: a personal integration token to dogfood without OAuth
notion.devToken=ntn_xxx
```

`app/build.gradle.kts` reads these into `BuildConfig`; `AppContainer` uses them.

## Notion integration setup

- Public integration (for the shipped OAuth flow): capabilities **Read content, Insert
  content, Update content** only. Copy the client id + secret. Notion only accepts https
  redirect URIs, so register the proxy callback as the Redirect URI:
  `https://<your-worker>.workers.dev/oauth/callback`. The worker bounces that to the app's
  `sift://oauth` scheme (which the Android manifest intercepts); do not register `sift://oauth`
  with Notion directly, it will be rejected.
- Internal integration (for debug dogfooding via `notion.devToken`): same three capabilities;
  share a page with it so Door A has somewhere to create the database.
