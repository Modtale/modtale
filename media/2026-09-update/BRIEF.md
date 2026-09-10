# Modtale update media brief

Prepared September 10, 2026. Local review drafts; nothing has been entered into Discord or X. The campaign is ready to review, not a claim that every development feature has reached the public deployment.

## 1. Articles: the complete story

Two existing articles are preserved as readable, self-contained HTML drafts with their current videos and stills, plus text copies for editing:

- **Meet the Modtale Launcher** — discovery, downloading, per-world library, CurseForge browsing, updates, account sync, and Wardrobe. `articles/modtale-launcher.html`
- **Modpacks v2: from your world to theirs** — composing releases, per-mod configs, external files, shared lists, and installed pack contents. `articles/modpacks-v2.html`

The articles remain the long-form source of detail. Do not pad the announcement with their full feature list. Their existing September 7 dates are retained in the exports; align those dates with the actual publication date when approved for release. Local previews: http://127.0.0.1:5187/news/modtale-launcher and http://127.0.0.1:5187/news/modpacks-v2.

## 2. Discord: one announcement, one overview trailer

**Audience:** the existing Modtale community. **Message:** build a setup on the site, manage it in the launcher, and make it your own. **Deliverables:** `discord/combined-overview.mp4`, matching cover, and `discord/announcement.txt`.

The 36-second trailer opens on the real Play page, then shows pack creation, downloading, Library search/toggling, CurseForge release notes, configs, account sync, and Wardrobe. It ends with one destination: the news page. Each chapter has a short, readable benefit caption in Modtale's Inter font. It works without sound; no unlicensed music or synthetic narration is included.

Attach the trailer to the announcement. No role ping is included. The standalone sync spotlight preserves the entire loading period; the overview deliberately uses a short excerpt and does not imply an instant restore.

## 3. Twitter/X: an opener, then individual stories

**Audience:** Hytale players and mod creators, including people who have not followed the recent development. **Message:** a quick reason to care, a visible action, then one destination.

The first post uses a separate **15-second** overview, not the longer Discord cut. Ten following feature posts reuse the completed article walkthroughs with a consistent branded heading, retaining their action timing and result holds. Two more posts cover worthwhile site additions since June 10: open-source discovery and project galleries.

Each post has its exact text in a numbered `.txt` file and its corresponding MP4 and cover in `twitter/`. `twitter/posts.json` records order, proposed relative day, source clip, and character count. The review page shows each video beside its tweet. Post 2 can reply to the intro; the remaining feature posts can stand alone. This is a proposed sequence, not a scheduled automation.

The copy uses plain ASCII and one URL per tweet. URL-weighted counts follow X's 23-character link rule; all drafts are below the standard 280-character limit without relying on Premium. Media exports use H.264, YUV 4:2:0, square pixels, 60 fps, and 1280×1024 framing; every clip is below 140 seconds and 512 MB. Covers share the same framing. For the account-sync clip, the small progress copy is intentionally left in the complete recording, with the main action explained in the large caption.

### Additional features found since June 10

Dates below are repository implementation dates, not independently verified public release dates. The audit covers non-merge history reachable from `develop`, June 10–September 10, 2026, and checks current source/UI rather than relying on commit subjects alone.

| Feature | Evidence | Campaign decision |
| --- | --- | --- |
| Open-source discovery filter | `fac0b2c1`, June 20; current Browse filter and license-based backend query | Include with a new capture. Demonstrates real results changing from 69 plugins to 45 open-source plugins in the local catalog. |
| Project gallery carousel, captions, fullscreen navigation | `76592452`, June 15; shared gallery components remain in use | Include with a new LevelingCore gallery capture. Show the feature through real project imagery. |
| YouTube gallery embeds and Markdown embeds | `5351ca2e`, `ac5ba5b2`, June 15 | Supporting gallery capability; omit from tweet claims because this capture focuses on screenshots, not video playback or authoring. |
| Pinned project comments | `cbf78318`, September 3; current comment controls | Defer. Useful, but weaker visually than the selected discovery and gallery posts; do not pad the campaign. |
| Batch gallery uploads and reordering | `0e3a20cd`, September 7 | Defer to a dedicated creator-tools campaign. Current spotlight shows the player-facing gallery. |
| Multi-version filtering and version-family selection | `6868573c`, June 15; `95d3663a`, June 20 | Fold into future discovery coverage; this launch already has several download-related posts. |
| CurseForge project-to-draft import | `f8451bd7`, September 8; removed from upload page by `68328167`, September 10 | **Do not advertise.** This is different from adding a CurseForge file to a modpack, which remains available and has its own post/video. Do not restore the removed feature for marketing. |
| Shared mod-list configs and conversion to packs | `3eddb8b9`, September 7; current shared-list/editor flow | Included in the main update sequence. |
| Launcher config editor, sync, Wardrobe, CurseForge support | Current code and reviewed article demos | Included in the main update sequence. |
| Status, performance, security, and layout fixes | Full history saved in `source/history.txt` | Exclude from this visual launch campaign; operational work is not presented as a new player-facing feature. |

### Claim boundaries

- A Modtale account is optional for browsing/installing; Hytale authentication is separate.
- Account sync covers launcher preferences, Modtale installs, and supported configs. It does not transfer terrain/world saves or promise to sync CurseForge installs.
- CurseForge-containing packs use the launcher; do not imply a redistributable website ZIP.
- Wardrobe previews do not grant ownership of cosmetics.
- Converting a shared list starts a draft; it does not automatically publish a project.
- Open-source discovery follows declared license metadata, not a separate audit of every project's source availability.

### Release handoff

After approving the local wording and media, verify the public launcher/download page and both news URLs are live on the intended release channel. Then publish the articles, the Discord post with its trailer, and the Twitter opener plus the chosen feature sequence. Do not publish the draft files automatically. Article export/edit files have not been committed, because documentation commits require separate permission.

### Platform sources

- X character counting and 23-character URLs: https://docs.x.com/fundamentals/counting-characters
- X video limits for standard accounts: https://help.x.com/en/using-x/x-videos
- Conservative upload encoding constraints: https://docs.x.com/x-api/media/quickstart/best-practices

See `source/combined-overview-edit.json` and `source/intro-edit.json` for exact cuts, `twitter/posts.json` for the copy ledger, and `review/validation.json` for the media checks.
