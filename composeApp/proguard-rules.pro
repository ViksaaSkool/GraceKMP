# Grace — Android R8/ProGuard rules.
#
# Release minification is enabled in Phase 11 of PLAN.md. The file exists from
# the start so the release build type can reference it.
#
# KMP/Compose ship their own consumer rules, so nothing app-specific is needed
# yet. Add keep rules here only if a future dependency requires them.
