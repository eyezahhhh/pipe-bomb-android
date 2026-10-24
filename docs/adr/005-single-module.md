# 005. Single Gradle module with package boundaries

Status: accepted

Context: the app is greenfield with one eventual platform target. A multi-module split
adds Gradle configuration, visibility boilerplate, and slower iteration before there is
a proven boundary or build-time problem.

Decision: start with one `app` module and enforce layers through package structure and
the dependency rules in `../architecture.md`. Split modules only when a concrete
pressure appears (build time, parallel team ownership, reusable non-Android code).

Consequences:

- Faster setup and simpler builds while the design is still moving.
- Boundaries are conventions, not compiler-enforced. Reviews must catch violations
  such as UI importing Retrofit or feature code touching ExoPlayer.
- The package map already groups code by eventual module, so extraction is mechanical.
- Revisit when incremental build time becomes painful, when KMP/iOS work starts, or
  when a package needs independent testing/reuse.
