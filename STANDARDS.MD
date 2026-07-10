# Standards

Basic common standards being followed for this repo (sakhi-mobile-app):

1. **Kotlin official style** — detekt clean before every change
2. **Small files** — composables kept small, files ≤ ~250 lines
3. **No `!!`** — deliberate null handling with `?.` / `?:`
4. **MVVM + Hilt** — UI in Compose, state in ViewModels, DI via Hilt
5. **Every screen handles 4 states** — loading / error / empty / success
6. **No magic values** — strings → `strings.xml`, sizes → `Dimens.kt`, colors → `Color.kt`
7. **Localization** — every string added in English + Marathi (`values-mr/`) in the same change
8. **Offline-first** — local DB encrypted with SQLCipher + Keystore key
9. **No secrets in code** — use Keystore / BuildConfig + CI secrets
10. **Never log PII or tokens**
11. **Coroutines** — use `viewModelScope`, never `GlobalScope`
12. **Unit tests mandatory** — every ViewModel/repository covers happy + sad paths
13. **Accessibility** — content descriptions on actionable/visual elements, responsive layouts
14. **Approval-gated workflow** — clarify → plan → test cases → code → summary
15. **Design fidelity** — build to Figma pixel-close, tablet + mobile both required
