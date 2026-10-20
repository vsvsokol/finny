# Home-screen performance audit and implementation plan

Date: 2026-10-20. Inspected checkout: `fix/room-transitions`, `5cd236d`.
Reported symptom: lag on the home screen / pet animation.

This is a source audit and implementation plan. No application code or artwork was changed. Cost mechanisms below are confirmed from source; their contribution to the reported lag has not been measured.

## Scope and code map

Finney is an offline Kotlin Android app using Jetpack Compose, coroutines/Flow, Room, DataStore and WorkManager. Inspection concentrated on the home screen, pet and room rendering, resource packing, state mapping and persistence. Mini-game internals and every other screen were not exhaustively audited.

```text
FinneyApplication -> AppContainer
  content/                   load and validate JSON game content
  domain/game/               Game rules -> GameStore serialized commands -> Session
  data/db/, data/repository/  Room persistence and domain/entity mapping
  navigation/                start routing and screen ViewModels
  ui/home/                   HomeViewModel -> HomeUiState -> HomeContent
    ui/room/                 RoomScene -> room backgrounds, toys, lighting, shadows
    ui/pet/                  layered PetView + pose, blink and idle animation
  ui/sound/                  application SoundPool + streaming music
  notifications/             WorkManager scheduling from saved game state
  res/drawable-nodpi/         packed original artwork used at runtime
  tools/                     offline asset processing; not frame-time game code
  src/test/, src/androidTest/ domain/ViewModel tests and device UI tests
```

Key entry points: `FinneyApplication.kt:14`, `MainActivity.kt:34`, `AppContainer.kt:26`, `ui/home/HomeScreen.kt:207`. Paths without a prefix in findings are under `app/src/main/java/ru/finney/pet/`.

Existing optimizations to preserve:

- `ui/pet/PetAnimation.kt`, `rememberPoseProvider`: pose state is read inside drawing/layer callbacks rather than rebuilding the pet on every frame.
- `ui/home/HomeScreen.kt:377`, `ui/motion/SteadyFrameRate.kt:23`: home already requests a mode near 60 Hz. Verify the actual selected mode; another frame cap is not the first fix.
- `ui/room/RoomLight.kt:140,812`: lighting uses quarter-resolution maps and fitted, cached shadow layers.
- `tools/pack_room.py:48`: room artwork is already cropped and packed at 1080 px width. Do not blindly rerun packers or reduce all assets.
- `data/db/GameDao.kt:130`: ledger/attempt/use insertion already appends only the missing tail.
- `ui/home/HomeViewModel.kt:290`: UI state collection already stops after its subscription timeout; home uses lifecycle-aware collection.

## Prioritized findings

### 1. Walking and entering the sleep capsule invalidate room composition

**Category:** repeated work. **Confidence:** high for the mechanism. **Likely impact:** high during movement; does not explain every idle frame.

`ui/room/RoomScene.kt:652` passes animation providers, but `RoomScene.kt:820` calls `stroll()` while composing `RoomCanvas`. `RoomScene.kt:894` similarly calls `walk()` in the capsule branch. Walking therefore invalidates that composition scope each frame. The scope also builds toy mappings and room children. The pet uses a value-based offset at `RoomScene.kt:947`. A moving ground rectangle also changes the occluder registration keys in `ui/room/RoomLight.kt:761`.

**Implementation:** isolate moving pet placement from the room's static composition. Read position in a placement or layer callback and let shadow geometry read the same current position during drawing. Keep capsule arc/scale, mirrored room coordinates, pet hit targets, toy-box clearance and shadow position synchronized. Merely wrapping the provider in another lambda will not help if it is still invoked in composition.

**Proof:** compare composition activity and main-thread slices during identical walks before/after; manually check walking, room changes, interrupted transitions and sleeping/waking. Use existing home motion UI tests as behavior checks.

### 2. Lighting combines repeated layer recording with per-body source allocations

**Category:** rendering inefficiency and allocation pressure. **Confidence:** high for repeated work; medium for its performance importance. **Likely impact:** potentially high for steady animation and scenes with many toys.

`ui/room/RoomLight.kt:769` records each body's content on redraw; lines 780 and 795 also record its light map and final offscreen composite. `RoomLight.kt:595` calls `glows()` per body; line 393 constructs lists and source objects. The room independently records source and combined maps at lines 526 and 542. Shadow caching already exists: this is additional work around it, not evidence that every shadow is rebuilt from scratch.

**Implementation:** first profile body recording, light-map recording and GPU time separately. Separate stable body artwork, changing poses and lighting invalidation; reuse a light map when its size, position, lamp/window state and UFO state are unchanged. Reuse a source snapshot for equivalent lighting inputs rather than rebuilding it per body. Continue updating animated silhouettes and moving shadows; avoid storing snapshot state in a cache without an explicit invalidation rule.

**Proof:** idle, pet jump, toy drag, lamp toggle, UFO flight and interior transition must retain correct light and shadows. Compare screenshots at controlled animation times and record CPU/GPU frame time plus graphics memory. Additional caches must not increase retained memory enough to cancel the benefit.

### 3. Pet layers pay for a full transparent 512 x 512 canvas

**Category:** memory use and texture area. **Confidence:** high for dimensions; savings need alpha-bound measurement. **Likely impact:** medium for memory, loading and texture pressure; not proven to dominate idle rendering.

Read-only WebP header inspection found 480 packed WebP resources. Pet layers such as `pushistik_left_hand.webp`, its growth variants, face and blink layers are 512 x 512. At four bytes per pixel each represents 1 MiB of pixels, even when a limb's compressed file is only about 1–3 KiB. A typical awake pet references five body layers, a face, a blink overlay and a mouth: about 8 MiB of pixel area before accessories, transitions and graphics layers. This is an arithmetic estimate, not a measured resident-memory total.

`ui/pet/PetView.kt:120,148,154,167,272` loads these resources; `tools/split_pet_base.py:58` sets the 512 px canvas. The largest room backgrounds are already 1080 x 1800, representing about 7.42 MiB each. These are per-image estimates; the app does not necessarily retain every theme or character at once.

**Implementation:** measure alpha bounds, then crop existing limb, blink and mouth images without changing their pixels, colors or visual size. Carry normalized source bounds/placement metadata so full-canvas pivots, neck joins, eyelid masks, accessories and growth transforms remain correct. Update the original packing pipeline and the generated skin contract together; generated `PetLookSkins.kt` must not become a hand-maintained fork. Keep full source art. Begin with one character and compare every supported mood/growth state before extending.

**Proof:** reconstruct each old full canvas from the crop and metadata and compare pixels; inspect animated joints and silhouettes. Measure decoded image/graphics memory. Compression alone saves package/storage bytes, not the full decoded canvas cost.

### 4. Home rebuilds the whole progression roadmap, including a quadratic history pass

**Category:** algorithmic repeated work. **Confidence:** high. **Likely impact:** medium on save-driven UI updates, increasing with long history; not a continuous animation cost.

`ui/home/HomeViewModel.kt:460` builds `levelNodes(...)` before taking the small `miniPathWindow` slice. `ui/progress/LevelPath.kt:75` groups completed periods using `game.levelAfter(state, it.number - 1)` for each period. `domain/game/Game.kt:153` scans all periods for every such call: this grouping becomes O(P²) in period count. The entire home projection is recomputed for each upstream saved-game emission at `HomeViewModel.kt:289`.

**Implementation:** compute historical level assignment in one ordered pass, preserving failed retries, passed-period rules and maximum-level behavior. Reuse static unlock presentation and build only the visible mini-path nodes for home where practical. Move CPU-heavy pure UI projection to `Dispatchers.Default` through a controlled Flow boundary if profiling confirms main-thread contention; preserve ordered/latest-state delivery and ViewModel cancellation.

**Proof:** compare old/new nodes for demo mode, failed retries, level cap and existing progression fixtures. Benchmark fresh and long synthetic histories; verify labels/unlocks and full roadmap remain identical.

### 5. Every save still maps and reloads complete growing history

**Category:** repeated mapping/I/O and scalability. **Confidence:** high. **Likely impact:** medium on command-driven stutters in older profiles; lower priority for idle animation.

`data/db/Entities.kt:151` relates a profile to all periods, ledger entries, attempts and uses. `data/db/Mappers.kt:39` maps full history before `GameDao.writeGame` discards already stored prefixes. `Mappers.kt:64` sorts and maps complete history on read. `domain/game/GameStore.kt:67` loads stored state before executing a command. `domain/game/Session.kt:34` exposes a cold active-game Flow, so independent UI/notification consumers can establish their own observation chains. Room's suspend database calls already run through Room: this is not proof of synchronous SQL on the main thread. Pure mapping after a suspend call can still run in the caller's context.

**Implementation:** profile long-history actions first. Offload pure mapping/rule work with explicit dispatcher ownership; remove repeated mapping of unchanged prefixes while keeping serialized commands and atomic saves. Consider a shared active-profile observation with explicit lifetime, switching and deletion semantics. Keep historic records in Room; introduce summaries/paged history only as a later compatibility-safe change after tracing every rule that needs full history.

**Proof:** existing GameStore, period, task, Room storage and migration tests; concurrent commands, profile switch, reset/delete and restart must preserve balances, rewards, stock and history exactly. Never trade durable saves for an unflushed debounce.

### 6. First-use image decoding can interrupt home transitions

**Category:** main-thread loading. **Confidence:** high for loading API behavior; medium for occurrence in this app. **Likely impact:** episodic when entering home or changing mood/theme/character.

The `painterResource` calls in `ui/pet/PetView.kt` and `ui/room/RoomInteriorSwap.kt:321` decode resources during composition when uncached. A room background alone represents 7.42 MiB of pixel area. Android's Compose performance codelab documents main-thread resource loading.

**Implementation:** after cropping and measurement, prepare only the active scene's imminent images asynchronously through a bounded loader/cache. Key by resource and configuration/size as needed; discard stale requests after theme/profile changes and respond to memory pressure. Retain the previous valid image until its replacement is ready. Avoid preloading all 480 images, duplicating Compose caches, or unloading/reloading artwork every frame.

**Proof:** record first home entry, first mood change and repeated interior changes. Compare decode slices and peak/retained memory after repeated changes and background/foreground cycles.

## Secondary candidates

- `ui/room/RoomScene.kt:329` creates lighting animation state for all four rooms; `RoomLight.kt:461` starts a breathing transition for each. Share its timebase or activate only visible lighting while preserving transitions. Extra animation bookkeeping is confirmed; its significance needs tracing.
- `PetView.kt:97` resolves a generated look skin on recomposition; `PetLookSkins.kt:13` creates copies and lists. Memoize by character/appearance if allocation traces show significance. This is smaller than the room composition problem.
- `app/build.gradle.kts:58` explicitly disables release optimization. Compare a debuggable build with a representative optimized, profileable release before judging final performance. Enable R8 only in a separate validated change; inspect serialization, Room, WorkManager and navigation behavior. Add a baseline profile for startup/home after code changes stabilize.
- No evidence supports deleting domain/history code or calling any retained resources a memory leak. Public/framework entry points and generated assets were not treated as dead code.

## Delivery order and stop conditions

1. **Establish a baseline.** Use the same physical device, save, screen configuration and compilation mode. Capture separate 30-second runs of idle home, walking/jumping, dragging toys, room changes, sleep/wake and interior selection. Record missed deadlines, P50/P95/P99 frame duration, main-thread versus RenderThread/GPU work, allocations and memory. Repeat runs to establish normal variation. At 60 Hz the nominal frame budget is about 16.7 ms; actual deadline misses and tail latency matter more than average FPS.
2. **Small CPU changes.** Fix composition-time movement reads and the quadratic roadmap pass in independently reviewable changes. Verify behavior and compare each with baseline before combining.
3. **Rendering changes.** Optimize light-map invalidation/source reuse only where traces show repeated work. Preserve the existing low-resolution and shadow caches. Revert a candidate that causes stale lighting or no measurable benefit.
4. **Memory changes.** Pilot crop/placement metadata on one character, then extend with image equivalence checks. Add bounded asynchronous loading only for measured decode stalls. Measure active-scene and post-transition memory; disk caching local packaged artwork is not automatically useful.
5. **Long-profile scalability and release tuning.** Address full-history mapping/observation and release optimization as separate changes, with domain/persistence regression checks. Avoid combining storage redesign with renderer changes.

Each change should reduce the targeted trace cost beyond run-to-run noise, preserve visible behavior and avoid new memory growth. Stop after the reported home interactions are smooth on the target device and no remaining measured bottleneck justifies another change. No fixed percentage speedup is promised.

## Validation and current limits

- Existing behavior checks: `app/src/androidTest/java/ru/finney/pet/ui/HomeTransientMotionTest.kt`, `HomeMotionReviewTest.kt`, `HomeDesignerLayoutTest.kt`, `HomeCareDetailsTest.kt`; progression/domain/ViewModel tests and Room storage/migration tests cover their respective changes. UI screenshot/motion tests do not establish frame performance.
- For implementation, run relevant unit tests plus `assembleDebug`, `assembleDebugAndroidTest` and `lintDebug`; run device tests for the changed interactions. Use optimized-build device traces for performance conclusions. Existing CI builds device tests but does not execute them or benchmark frame timing.
- A connected Samsung SM-A256E (Galaxy A25) was discovered through ADB. `ru.finney.pet.debug` was backgrounded (`visibility=8`) and gfxinfo contained only one frame. Its 650 ms startup frame cannot establish steady home performance; no benchmark baseline or current memory-leak diagnosis is available. The installed APK was not verified against this checkout. The device/app was not brought to foreground or reconfigured.
- No build/tests were run for this documentation-only audit. Other working-tree changes were left untouched; their status changed during the audit, with mini-game edits present at the final check. Recheck the live diff before implementing this plan.

## Primary references

- [Compose phases and deferred state reads](https://developer.android.com/develop/ui/compose/performance/phases)
- [Compose performance codelab: tracing and main-thread image loading](https://developer.android.com/codelabs/jetpack-compose-performance)
- [Bitmap memory usage](https://developer.android.com/topic/performance/issues/bitmap-memory-usage)
- [Compose performance: release builds and profiling](https://developer.android.com/develop/ui/compose/performance)
