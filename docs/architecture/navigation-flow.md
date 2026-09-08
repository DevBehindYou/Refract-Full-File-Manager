# Navigation Flow

Full route graph, back behaviour, and predictive-back handling.
The authoritative rules live in `NAVIGATION.md`; this file is the picture.

## 1. Route graph

```mermaid
flowchart TD
    START([Launch]) --> SPLASH[Splash]
    SPLASH --> GATE{First run or<br/>insufficient access?}
    GATE -->|yes| ONB[Onboarding] --> PSETUP[Permission setup] --> ROOT
    GATE -->|no| ROOT[Root scaffold<br/>+ LiquidBottomBar]

    ROOT --> HOME[Home]
    ROOT --> BROWSE[Browse]
    ROOT --> SEARCH[Search]
    ROOT --> STORAGE[Storage]
    ROOT --> MORE[More]

    HOME --> CAT[Category]
    HOME --> FOLD[Folder]
    HOME --> RECENTS[Recents]
    HOME --> FAVS[Favourites]
    BROWSE --> FOLD
    FOLD --> FOLD
    SEARCH --> FOLD
    STORAGE --> CAT
    STORAGE --> LARGE[Largest files]
    CAT --> FOLD

    FOLD --> PREV[Preview host]
    CAT --> PREV
    SEARCH --> PREV
    RECENTS --> PREV
    PREV --> IMG[Image viewer]
    PREV --> VID[Video]
    PREV --> AUD[Audio]
    PREV --> TXT[Text/code]
    PREV --> PDF[PDF · V1]
    PREV --> APK[APK info · V1]

    MORE --> ARCH[Archive manager]
    MORE --> OPS[Operations]
    MORE --> SET[Settings]
    MORE --> ABOUT[About]
    MORE --> TROUBLE[Permission troubleshooting]

    FOLD -.modal.-> INFO[File info sheet]
    FOLD -.modal.-> SORT[Sort sheet]
    FOLD -.modal.-> CONF[Conflict dialog]
    FOLD -.destination.-> PICK[Destination picker]
```

## 2. Back priority

```mermaid
flowchart TD
    BACK[Back pressed / gesture] --> D1{Dialog open?}
    D1 -->|yes| C1[Dismiss dialog] --> END([done])
    D1 -->|no| D2{Sheet expanded?}
    D2 -->|yes| C2[Collapse sheet] --> END
    D2 -->|no| D3{Selection mode?}
    D3 -->|yes| C3[Exit selection] --> END
    D3 -->|no| D4{Search focused with text?}
    D4 -->|yes| C4[Clear and unfocus] --> END
    D4 -->|no| D5{Inside a folder?}
    D5 -->|yes| C5[Up one level] --> END
    D5 -->|no| D6{Not on Home tab?}
    D6 -->|yes| C6[Switch to Home] --> END
    D6 -->|no| C7[Exit app] --> END
```

Predictive back previews whichever destination this chain resolves to, so the preview is
always truthful.

## 3. Tab back stacks

```mermaid
flowchart LR
    subgraph HomeStack
        H1[Home] --> H2[Category: Images] --> H3[Folder: DCIM]
    end
    subgraph BrowseStack
        B1[Browse] --> B2[Folder: /] --> B3[Folder: Download]
    end
    subgraph SearchStack
        S1[Search]
    end
    TAP{{Tap Browse tab}} -.restoreState.-> B3
    TAP2{{Tap Home tab}} -.restoreState.-> H3
```

Each tab keeps its own stack and scroll position. Switching tabs never resets the other tab.
