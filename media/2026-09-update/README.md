# September update media

Review locally with `python3 -m http.server 5188 --directory media/2026-09-update` from the repository root, then open http://127.0.0.1:5188/.

This package contains the two article exports, a 36-second Discord trailer and announcement draft, and 13 Twitter posts with matching videos and covers. Nothing is scheduled or posted. See BRIEF.md for rollout notes and feature evidence.

The final website media lives in frontend/public/assets/news. Article exports include their own copies so this folder is portable. Temporary renders and duplicate ZIP archives are excluded.

## Sources and validation

Source scripts resolve paths relative to this checkout. Python dependencies: Pillow, BeautifulSoup4, NumPy, and opencv-python; video processing requires FFmpeg and ffprobe. Fonts are included.

- source/build-review.py rebuilds the review page.
- source/prepare.py refreshes article exports from the running preview at port 5187 and prepares tweet drafts; review exported article styling afterward.
- source/render-social.py renders social edits using website media and the included additional demo captures. It skips existing feature video exports.
- source/render-capture.py renders the included open-source.json and project-galleries.json manifests. Run --check first, then specify --output-dir when rendering.
- source/validate.py fully decodes all 14 social videos, verifies format and character limits, checks article assets, and regenerates contact sheets.

The review folder retains the completed validation report and visual review sheets. Edit manifests preserve the overview pacing. Source screenshots contain the actual reviewed interface.
