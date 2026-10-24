---
name: m3-expressive
description: Material 3 Expressive (M3 Expressive) design system and Jetpack Compose implementation skill (from abhixv/m3-expressive-design-skill and hamen/material-3-skill). Covers the 7 expressive tactics, spring motion physics (MotionScheme.expressive()), 10-step shape scale & optical roundness, shape morphing, emphasized typography, tonal containment, and expressive components.
---

# Material 3 Expressive (M3 Expressive) Design Skill

## Core Philosophy & The 7 Expressive Tactics
1. **Use a variety of shapes**: Mix rounded (pill/`CircleShape`) and squarish (`Large` 16dp, `LargeIncreased` 20dp, `ExtraLarge` 28dp) shapes for visual tension. Use segmented/grouped containers where outer items have large outer corners (`24.dp`–`28.dp`) and inner adjacent edges have small corners (`4.dp`–`8.dp`).
2. **Use rich and nuanced colors**: Leverage the M3 tonal surface hierarchy (`surface`, `surfaceContainerLowest`, `surfaceContainerLow`, `surfaceContainer`, `surfaceContainerHigh`, `surfaceContainerHighest`) and vibrant container roles (`primaryContainer`, `secondaryContainer`, `tertiaryContainer`, `errorContainer`) for focal elements.
3. **Emphasize typography**: Use heavier weights (`FontWeight.Bold`, `FontWeight.ExtraBold`, `FontWeight.Black`) and larger display/headline scales for key metrics (alarm times, active badges, hero CTAs) vs lighter weights (`Normal`/`Medium`) for supporting labels.
4. **Use containment for emphasis**: Group related controls and settings into cohesive tonal cards/surfaces (`surfaceContainerLow` / `surfaceContainer` / `surfaceContainerHigh`) instead of bare rows with full-bleed `HorizontalDivider`s.
5. **Use fluid and natural motion**: Replace rigid duration-based `tween(...)` animations with physics-based springs via `MaterialTheme.motionScheme`:
   - **Spatial springs** (movement, size, bounds, corner radius/shape morphing, scale, rotation):
     - `MaterialTheme.motionScheme.defaultSpatialSpec()` (damping ~0.9, stiffness ~700)
     - `MaterialTheme.motionScheme.fastSpatialSpec()` (damping ~0.6–0.9, stiffness ~800–1400; allows expressive bounce)
     - `MaterialTheme.motionScheme.slowSpatialSpec()` (damping ~0.9, stiffness ~300)
   - **Effects springs** (color, alpha/opacity, elevation — critically damped, NEVER overshoots):
     - `MaterialTheme.motionScheme.defaultEffectsSpec()` (damping 1.0, stiffness 1600)
     - `MaterialTheme.motionScheme.fastEffectsSpec()` (damping 1.0, stiffness 3800)
     - `MaterialTheme.motionScheme.slowEffectsSpec()` (damping 1.0, stiffness 800)
6. **Take advantage of component flexibility**:
   - Theme entry point: `@OptIn(ExperimentalMaterial3ExpressiveApi::class) MaterialExpressiveTheme(colorScheme = ..., motionScheme = MotionScheme.expressive(), shapes = ..., typography = ...)`
   - Buttons: `ButtonDefaults.shapes()` for press-state shape morphing, or animate corner radii on press/selection (`InteractionSource.collectIsPressedAsState()`).
   - Top App Bars: `CenterAlignedTopAppBar`, `MediumFlexibleTopAppBar`, or expressive `TopAppBar` with tonal scroll container colors and spring-animated navigation/action icon containers.
   - Toggles / Chips / Day Pills: Morph between circle (`50%`) and squarish rounded rectangle (`12.dp`–`16.dp`) on selection/press using `animateDpAsState(..., animationSpec = MaterialTheme.motionScheme.fastSpatialSpec())`.
7. **Include hero moments**: Give 1–2 focal elements per screen bold scale, vibrant container color, expressive shape, and spring physics (e.g., the ringing alarm display & Stop button in `AlarmActivity`, the time range & FAB on the home screen, the start/end time cards in the picker).

## 10-Step Shape Scale & Optical Roundness Rule
- **None**: `0.dp`
- **ExtraSmall**: `4.dp` (inner grouped list corners, small badges)
- **Small**: `8.dp` (chips, compact tags)
- **Medium**: `12.dp` (nested cards, input fields)
- **Large**: `16.dp` (standard inner containers, buttons on press)
- **LargeIncreased**: `20.dp` (featured cards, expressive dialogs)
- **ExtraLarge**: `28.dp` (outer cards, sheets, large containers)
- **ExtraLargeIncreased**: `32.dp` (hero cards, modal dialogs)
- **ExtraExtraLarge**: `48.dp` (oversized hero surfaces)
- **Full**: `CircleShape` / `50%` (pills, FABs, unselected day circles, primary CTAs)
- **Optical Roundness Rule (Nested Radii)**: `innerCornerRadius = max(outerCornerRadius - padding, 4.dp)`. Never use the exact same corner radius for both an outer card and a tightly nested inner box/button.

## Strict Guardrails
- Always add `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` where `MaterialExpressiveTheme`, `MotionScheme`, `ButtonDefaults.shapes()`, etc. are used.
- Preserve all business logic, ViewModel calls, state flows, analytics events, haptics, permissions, and onboarding compatibility (`fromOnboarding`).
- Always add explicit `import` statements at the top of the file; never use inline fully-qualified class names.
