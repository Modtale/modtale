# Web localization

User-facing copy lives in `locales/<locale>.ts` and is accessed through `react-i18next`. English is the fallback and currently the only shipped locale.

To add a language:

1. Copy `locales/en.ts`, preserving its complete key structure.
2. Add the locale and its native display name/direction to `localeMetadata`.
3. Add its lazy loader in `resources.ts`.

The language selector appears automatically once a second locale is registered. Locale selection is persisted under `modtale-locale`; otherwise the browser language is negotiated. The root provider updates both `lang` and `dir`, while `useLocalization` supplies locale-aware number, date, and list formatting.

Keep dynamic values in interpolation variables, use i18next plural suffixes (`_one`, `_other`) for counts, and avoid assembling translated sentences from fragments.
