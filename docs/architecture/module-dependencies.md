# Module and Package Dependencies

## MVP — one Gradle module, enforced package boundaries

```mermaid
flowchart TD
    subgraph app[":app"]
        FEAT["feature.*<br/>home · browse · search · storage<br/>preview · operations · settings · permissions"]
        CUI["core.ui"]
        CDS["core.designsystem"]
        CNAV["core.navigation"]
        DOMAIN["domain<br/>⚠ no android.*"]
        DATA["data"]
        CDB["core.database"]
        CDST["core.datastore"]
        CCOM["core.common"]
    end
    BENCH[":benchmark"]

    FEAT --> CUI --> CDS --> CCOM
    FEAT --> DOMAIN
    FEAT --> CNAV
    DOMAIN --> CCOM
    DATA -.wired in :app only.-> DOMAIN
    DATA --> CDB
    DATA --> CDST
    DATA --> CCOM
    BENCH -.measures.-> app
```

**The invariant worth protecting:** `feature.*` never imports `data`. Features depend on
`domain` interfaces; Hilt binds implementations in `:app`. This is what makes the eventual
module split mechanical rather than a refactor.

## Allowed dependencies

| From | May depend on |
|---|---|
| `feature.*` | `domain`, `core.ui`, `core.designsystem`, `core.navigation`, `core.common` |
| `core.ui` | `core.designsystem`, `core.common`, `domain` (models only) |
| `core.designsystem` | `core.common` |
| `domain` | `core.common`, Kotlin, coroutines |
| `data` | `domain`, `core.database`, `core.datastore`, `core.common`, Android |
| `core.database` / `core.datastore` | `core.common` |
| `core.common` | nothing |

## Forbidden (Lint-enforced, build-failing)

* `domain` → anything Android
* `feature.a` → `feature.b`
* `feature.*` → `data`
* `core.designsystem` → `domain` business models
* Any cycle whatsoever

## V1 — Gradle module split

```mermaid
flowchart TD
    app --> f1[feature:home] & f2[feature:browse] & f3[feature:search]
    app --> f4[feature:storage] & f5[feature:preview] & f6[feature:operations] & f7[feature:settings]
    app --> data
    f1 & f2 & f3 & f4 & f5 & f6 & f7 --> cui[core:ui] --> cds[core:design] --> ccom[core:common]
    f1 & f2 & f3 & f4 & f5 & f6 & f7 --> domain --> ccom
    data --> domain
    data --> cdb[core:database] --> ccom
    data --> cdst[core:datastore] --> ccom
    data --> cperm[core:permissions] --> ccom
    bench[":benchmark"] -.-> app
```

Split triggers are listed in `MODULES.md` §4. Do not split before one fires.
