# JDK21 → JDK17 backport (OpenRewrite)

Generates a JDK17/`javax` build of the Adyen plugin from the JDK21/`jakarta` source of
truth. Run `rewrite.yml` (recipe `com.adyen.backport.Jdk21ToJdk17`) over the plugin
source; the JDK21 tree stays canonical.

This README is also the **inventory of forced incompatibilities** — the spec for the
recipe. Keeping this list small and current is what makes the approach sustainable.

## Verified scope (from the real `v13.4.0` → `v13.4.0_jdk21` delta + current source)

The migration diff was 136 files / ~950 lines, but only a small, mechanical subset is
actually JDK-forced. Everything else is version-agnostic and must NOT be reversed.

### A. Fully recipe-handled — mechanical, deterministic

| Change | Where | How |
|---|---|---|
| `jakarta.servlet[.http]` → `javax.servlet[.http]` | 63 + 6 imports | `ChangePackage` |
| `jakarta.annotation` → `javax.annotation` (`@Resource`, `@PostConstruct`…) | 38 imports | `ChangePackage` |
| `jakarta.validation[.constraints]` → `javax.validation` | 12 imports | `ChangePackage` |
| `jakarta.ws.rs[.core]` → `javax.ws.rs` | 3 imports | `ChangePackage` |
| `commons.collections4.CollectionUtils` → `commons.collections` | 10 files, only `isEmpty`/`isNotEmpty` | `ChangePackage` |
| `<import type="jakarta.xml.bind…"/>` in `*-beans.xml` | 2 plugin + 2 example beans.xml | `FindAndReplace` |
| JAX-RS API coordinate `jakarta.ws.rs:…:3.1.0` → `javax.ws.rs:…:2.1.1` | `adyenv6core/external-dependencies.xml` | `FindAndReplace` |

### B. Recipe transforms the packages, but the rewritten LOGIC needs JDK17-platform verification

These files were structurally rewritten during migration. Their new imports are all
**long-stable platform APIs that also exist on JDK17 Hybris**, so they compile after the
package swap — but the *behaviour* must be tested on the JDK17 platform, not assumed.
This is a test concern, not a transformation gap.

| File | Forced lines (non-pkg) | Why it diverged | Action |
|---|---|---|---|
| `adyenv6backoffice/.../cancel/AdyenCancelOrderAction.java` | ~54 | Base class `omsbackoffice…CancelOrderAction` changed; reimplemented on `CockpitAction` | **Verify** cancel behaviour on JDK17 platform |
| `adyenv6b2ccheckoutaddon/.../interceptors/SameSiteCookieHandlerInterceptorAdapter.java` (+ `filters/SameSiteCookiePostProcessFilter.java`, `utils/SameSiteCookieAttributeAppenderUtils.java`) | ~10 | SameSite handling reworked Filter ↔ Interceptor | **Verify** SameSite cookie emitted on JDK17 |
| `adyenv6backoffice/.../capture/AdyenCaptureOrderAction.java` | ~4 | Same `CockpitAction` family, minor | Eyeball; likely OK |

> The 5 `adyensapdigitalpaymentbackoffice/.../*DPA.java` actions also implement
> `CockpitAction` but have **0** non-package changes — they are category A, not B.

### C. Out of scope — version-agnostic, do NOT reverse

Present in the diff but compiles/works identically on JDK17; reversing it would only add
risk and noise. The recipe deliberately ignores all of it:

- `autowire="byName"` → explicit `<property ref=…/>`, `lazy-init="true"`, `name`→`id`,
  dead-bean removal in `*-spring.xml`
- `org.apache.log4j.Logger` (97 files) — log4j-1.2 bridge is on both platforms
- JSP JSTL taglib URIs — plugin already uses legacy `http://java.sun.com/jsp/jstl/*`
- `.ts` request-payload changes (`text/plain` → JSON), `storefrontVersion`, README,
  locale `.properties`, build numbers/timestamps

## Caveats to confirm against the real JDK17 platform/classpath

- **`adyen-java-api-library:40.0.0`** (declared in `external-dependencies.xml`) and
  **`httpclient5`** must have JDK17-viable versions on the build. If the bundled SDK is
  jakarta-only, that is a genuine blocker the recipe cannot fix — confirm the SDK line
  that targets `javax`/Servlet-API on the JDK17 platform.
- **`commons-collections` 3.x** must be resolvable on the JDK17 build (it is on the
  Hybris platform classpath; confirm it is actually pulled).
- **`javax.ws.rs-api:2.1.1`** is the highest `javax` JAX-RS API — confirm it satisfies the
  Adyen SDK's JAX-RS usage on JDK17.

## Running it (Hybris is Ant, OpenRewrite expects Maven/Gradle)

A self-contained Gradle shim lives in this directory — it does **not** compile Hybris, it
only feeds the plugin's source roots + platform jars to OpenRewrite. Files:

| File | Role |
|---|---|
| `build.gradle` | discovers every extension's Java source roots, adds platform jars as `compileOnly` for attribution, exposes the two XML descriptors as plain-text, runs the recipe |
| `settings.gradle`, `gradle.properties` | isolate the shim; 3 GB heap, daemon off |
| `rewrite.yml` | the recipe (`com.adyen.backport.Jdk21ToJdk17`) |
| `backport.sh` | convenience wrapper: `--dry` / `--in-place` / `--out <dir>` |

### Prerequisites
- **JDK21 JVM** (the running JVM picks the Java parser grammar; sources are Java 21/jakarta).
- **Gradle 8.x**. No wrapper is committed. One-time bootstrap from this dir:
  `gradle wrapper --gradle-version 8.8` (needs any Gradle on PATH once), or just use a
  system `gradle`. The plugin version in `build.gradle` (`org.openrewrite.rewrite 6.27.0`)
  may need bumping to the latest 6.x.

### Commands (run from `openrewrite/`)
```bash
./backport.sh --dry             # rewriteDryRun -> build/reports/rewrite/rewrite.patch (no changes)
./backport.sh --out /tmp/jdk17  # copy the tree, rewrite the copy (safe local run)
./backport.sh --in-place        # rewrite the current tree (CI: on a JDK21 checkout you can mutate)
```
Equivalent raw invocation: `gradle -p openrewrite rewriteDryRun` (or `rewriteRun`).

> Alternative runner: the **`mod` (Moderne) CLI** over the plugin tree with
> `--recipe-config openrewrite/rewrite.yml` — no Gradle needed.

> Note on the XML edits: `text.FindAndReplace` only runs on plain-text sources, so the shim
> sets `plainTextMasks` for `*-beans.xml` and `external-dependencies.xml`. Always confirm
> them in the `--dry` patch; if your OpenRewrite version handles `plainTextMasks`
> differently, fall back to a 3-line `sed` step for those two descriptors.

## CI (non-negotiable)

Build **and test both variants**, each against its own platform:

- JDK21 tree → JDK21 platform (canonical).
- JDK21 tree → `rewriteRun` → JDK17 tree → JDK17 platform → run the integration tests,
  with explicit coverage of the **category-B** behaviours (order cancel/capture backoffice
  actions, SameSite cookie). A green JDK21 build does not imply a green JDK17 one.

When a future change adds a new `jakarta`/`collections4` surface or a new forced rewrite,
update the table above **and** `rewrite.yml` in the same PR.
