# 0.1.1.0 — working draft
- Local planning uses a deterministic distribution with daily limits and excluded weekdays. Inferred effort is labelled as a suggestion, not an AI guarantee.
- Private homes and named lists use new optional record fields. Legacy unassigned furniture and products remain visible under virtual defaults. Existing backups are supported.
- Shared lists and furniture use versioned Firestore resources. Publication is explicit and leaves a private recovery copy; old private history, photos and inventory links are not published. Shared lists/furniture are edited online. Full offline reconciliation for published resources is not implemented.
- Home permissions inherit only to child furniture; independent furniture grants must not reveal sibling furniture or the parent document. The child includes the home name for grouping. New rules and indexes must be deployed and emulator tests passed before real use.
- Profile avatar and optional login prompt are included. A Google login does not transfer guest data automatically.
- OCR operates on-device; handwriting accuracy is not guaranteed. User confirms recognized lines.
- Android build and Firebase emulator tests must run before merging. No production credentials or release artifacts included.
