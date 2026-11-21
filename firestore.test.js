const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validSpotPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    contributorLabel: "Alice",
    venueName: "Bar Zum Anker",
    venueCategory: "BAR",
    ssid: "Anker_Free_WiFi",
    password: "prost2026",
    securityType: "WPA2",
    sharedPublicly: true,
    notes: "Schild direkt über der Theke",
    cityOrArea: "Kreuzberg",
    latitude: 52.4986,
    longitude: 13.4183,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("wifi_spots").where("sharedPublicly", "==", true).get()
  );
  await assertFails(
    unauthDb.collection("wifi_spots").doc("spot_1").set(validSpotPayload(ALICE_UID))
  );
  await assertFails(
    unauthDb.collection("spot_feedback").where("publicFeedback", "==", true).get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("favorites").get()
  );
});

test("Authenticated user can create, update, and delete own wifi_spot matching Kotlin schema", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const payload = validSpotPayload(ALICE_UID);

  await assertSucceeds(aliceDb.collection("wifi_spots").doc("spot_1").set(payload));

  // Partial update preserving immutable fields (userId, createdAt)
  await assertSucceeds(
    aliceDb.collection("wifi_spots").doc("spot_1").update({
      venueName: "Bar Zum Anker Neu",
      notes: "",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject @DocumentId "id" key inside document body
  await assertFails(
    aliceDb.collection("wifi_spots").doc("spot_2").set({
      ...payload,
      id: "spot_2",
    })
  );

  // Reject ghost field
  await assertFails(
    aliceDb.collection("wifi_spots").doc("spot_3").set({
      ...payload,
      isAdminVerified: true,
    })
  );

  // Reject immutable createdAt mutation
  await assertFails(
    aliceDb.collection("wifi_spots").doc("spot_1").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("wifi_spots").doc("spot_4").set(
      validSpotPayload(ALICE_UID, {
        createdAt: new Date(Date.now() + 60000),
        updatedAt: new Date(Date.now() + 60000),
      })
    )
  );

  // Owner can delete own spot
  await assertSucceeds(aliceDb.collection("wifi_spots").doc("spot_1").delete());
});

test("Community list query requires sharedPublicly == true or userId == uid; private spots stay isolated", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db
      .collection("wifi_spots")
      .doc("public_spot")
      .set(validSpotPayload(BOB_UID, { sharedPublicly: true }));
    await db
      .collection("wifi_spots")
      .doc("private_bob")
      .set(validSpotPayload(BOB_UID, { sharedPublicly: false }));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Matches Kotlin Radar query: .whereEqualTo("sharedPublicly", true)
  await assertSucceeds(
    aliceDb.collection("wifi_spots").where("sharedPublicly", "==", true).get()
  );

  // Matches Kotlin MySpots query: .whereEqualTo("userId", uid)
  await assertSucceeds(
    aliceDb.collection("wifi_spots").where("userId", "==", ALICE_UID).get()
  );

  // Unfiltered list query must be rejected
  await assertFails(aliceDb.collection("wifi_spots").get());

  // Cannot read or modify Bob's private spot
  await assertFails(aliceDb.collection("wifi_spots").doc("private_bob").get());
  await assertFails(
    aliceDb.collection("wifi_spots").doc("public_spot").update({
      venueName: "Hijacked",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(aliceDb.collection("wifi_spots").doc("public_spot").delete());
});

test("Spot feedback enforces deterministic ID, parent existence, and owner-only updates", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx
      .firestore()
      .collection("wifi_spots")
      .doc("spot_10")
      .set(validSpotPayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const feedbackId = `spot_10_${ALICE_UID}`;
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb.collection("spot_feedback").doc(feedbackId).set({
      spotId: "spot_10",
      userId: ALICE_UID,
      working: true,
      publicFeedback: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("spot_feedback").doc(feedbackId).update({
      working: false,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Matches Kotlin repository query
  await assertSucceeds(
    aliceDb.collection("spot_feedback").where("publicFeedback", "==", true).get()
  );

  // Unfiltered query must fail
  await assertFails(aliceDb.collection("spot_feedback").get());

  // Feedback for non-existent spot must fail
  await assertFails(
    aliceDb.collection("spot_feedback").doc(`missing_${ALICE_UID}`).set({
      spotId: "missing",
      userId: ALICE_UID,
      working: true,
      publicFeedback: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob cannot overwrite Alice's feedback
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("spot_feedback").doc(feedbackId).update({
      working: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("User favorites subcollection enforces strict owner isolation and parent existence", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx
      .firestore()
      .collection("wifi_spots")
      .doc("spot_20")
      .set(validSpotPayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("favorites")
      .doc("spot_20")
      .set({
        userId: ALICE_UID,
        spotId: "spot_20",
        createdAt: now,
      })
  );

  // Owner list succeeds even when subcollection is empty
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).collection("favorites").get()
  );

  // Cross-user access fails
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("favorites").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("favorites")
      .doc("spot_20")
      .delete()
  );

  // Non-existent spot favorite fails
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("favorites")
      .doc("nonexistent")
      .set({
        userId: ALICE_UID,
        spotId: "nonexistent",
        createdAt: now,
      })
  );
});
