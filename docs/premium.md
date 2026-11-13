# Premium

`billing/Entitlements.kt` defines the seam:

- `BillingProvider` reports whether a store is configured and whether a
  verified purchase exists. This build ships `NotConfiguredBilling`
  (no Google Play Billing library), so **nothing can be purchased** and the
  Premium screen says so.
- `EntitlementResolver` turns (purchase, dev-available, dev-enabled) into an
  `Entitlement` with its source: `PLAY_BILLING`, `DEVELOPMENT` or `NONE`.
- `DEVELOPMENT` exists only when `BuildConfig.DEV_ENTITLEMENT` is true (debug
  builds). The switch is labelled *testing only* and the app shows *Testing
  switch (not a purchase)* wherever Premium status appears.

Premium features: more than 3 workflows, scheduled workflows, shortcuts and
the Quick Settings tile, other-app triggers, NDEF emulation, NDEF Lab.
Everything that shipped free before Premium existed stays free.

To add Play Billing: implement `BillingProvider` with the Play Billing
Library (query purchases, verify, acknowledge), bind it in `di/AppModule.kt`,
and enable the purchase button. No feature code changes.
