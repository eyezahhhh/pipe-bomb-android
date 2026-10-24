# 006. Web client is the visual source of truth

Status: accepted

Context: Pipe Bomb already has a mature web client with a distinct dark, dense,
green-accented visual language. Android users should recognise the same product.
Stock Material 3 defaults, dynamic colour, and a separate design system would make
the app feel like a different product.

Decision: treat `website/src/app/theme.css`, `globals.scss`, and the component styles
as canonical. Reimplement them in a Compose token layer plus shared components,
documented in `../design-tokens.md`, `../shapes-and-motion.md`, and
`../components.md`. Dark theme only at launch; dynamic colour disabled. Adapt layout
and interaction for touch, not the visual language.

Consequences:

- Visual review compares Android against the web, not against Material guidelines.
- Tokens are duplicated in Kotlin and must be updated with the web theme in the same
  change; a token drift check or screenshot tests are worthwhile later.
- Compose components are custom, not thin wrappers over Material defaults.
- No Material You personalisation or light theme until the product explicitly adds
  them (which would require superseding this ADR).
- The web client can evolve, but Android must follow deliberately, not silently.
