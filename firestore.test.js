const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_user_001";
const BOB_UID = "bob_user_002";
const ADMIN_UID = "owner_bayzid_001";
const ADMIN_EMAIL = "mdbayzidh882@gmail.com";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msOffset = 2000) {
  return new Date(Date.now() - msOffset);
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

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      userName: "Alice",
      plan: "free",
      banned: false,
      preferredLanguage: "bn",
      preferredVoice: "female",
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
  await assertFails(unauthDb.collection("app_config").doc("global").get());
  await assertFails(unauthDb.collection("premium_codes").doc("CODE-123").get());
});

test("User can create free profile, update preferences, but cannot self-promote or tamper fields", async () => {
  const aliceDb = testEnv
    .authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true })
    .firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      userName: "Alice",
      email: "alice@example.com",
      plan: "free",
      banned: false,
      preferredLanguage: "bn",
      preferredVoice: "female",
      createdAt: pastDate(5000),
      updatedAt: pastDate(5000),
    })
  );

  // Self-creating directly as premium must fail
  await assertFails(
    testEnv
      .authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true })
      .firestore()
      .collection("users")
      .doc(BOB_UID)
      .set({
        userId: BOB_UID,
        userName: "Bob",
        plan: "premium",
        banned: false,
        preferredLanguage: "bn",
        preferredVoice: "male",
        createdAt: pastDate(),
        updatedAt: pastDate(),
      })
  );

  // Valid preference update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      preferredVoice: "male",
      updatedAt: pastDate(1000),
    })
  );

  // Trying to self-upgrade without an active code must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      plan: "premium",
      updatedAt: pastDate(500),
    })
  );

  // Bob cannot read or modify Alice's profile
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({ userName: "Hacked", updatedAt: pastDate(500) })
  );
  await assertFails(bobDb.collection("users").get());
});

test("User memories are strictly isolated to owner and validate schema", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1").set({
      userId: ALICE_UID,
      category: "preference",
      title: "পছন্দের খাবার",
      content: "বিরিয়ানি ও ইলিশ মাছ পছন্দ করি",
      createdAt: pastDate(5000),
      updatedAt: pastDate(5000),
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("memories").get());
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1").update({
      content: "গরম চা ও ইলিশ মাছ পছন্দ করি",
      updatedAt: pastDate(1000),
    })
  );

  // Cross-user access denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("memories").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1").delete()
  );

  // Ghost field rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_2").set({
      userId: ALICE_UID,
      category: "note",
      title: "Test",
      content: "Body",
      extraSecret: true,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1").delete()
  );
});

test("Admin email spoofing with email_verified=false is rejected", async () => {
  const spoofAdminDb = testEnv
    .authenticatedContext("attacker_uid", {
      email: ADMIN_EMAIL,
      email_verified: false,
    })
    .firestore();

  await assertFails(spoofAdminDb.collection("premium_codes").get());
  await assertFails(
    spoofAdminDb.collection("app_config").doc("global").set({
      cameraEnabledForFree: true,
      longTermMemoryForFree: true,
      translationForFree: true,
      dayPlanningForFree: true,
      announcementText: "Hacked",
      announcementActive: true,
      updatedBy: "attacker_uid",
      updatedAt: pastDate(),
    })
  );
});

test("Verified admin can generate codes, toggle config, log activity, and single-use code redeems atomically", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, {
      email: ADMIN_EMAIL,
      email_verified: true,
    })
    .firestore();
  const aliceDb = testEnv
    .authenticatedContext(ALICE_UID, {
      email: "alice@example.com",
      email_verified: true,
    })
    .firestore();

  // Admin creates single-use 1-month code and owner code
  await assertSucceeds(
    adminDb.collection("premium_codes").doc("SATHI-1M-TEST1").set({
      codeType: "1_month",
      status: "active",
      createdBy: ADMIN_UID,
      note: "For friend",
      createdAt: pastDate(5000),
      updatedAt: pastDate(5000),
    })
  );
  await assertSucceeds(
    adminDb.collection("premium_codes").doc("SATHI-OWNER-MASTER").set({
      codeType: "owner",
      status: "active",
      createdBy: ADMIN_UID,
      createdAt: pastDate(5000),
      updatedAt: pastDate(5000),
    })
  );

  // Normal user cannot list codes
  await assertFails(aliceDb.collection("premium_codes").get());

  // Alice creates free profile
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      userName: "Alice",
      plan: "free",
      banned: false,
      preferredLanguage: "bn",
      preferredVoice: "female",
      createdAt: pastDate(5000),
      updatedAt: pastDate(5000),
    })
  );

  // Alice redeems SATHI-1M-TEST1 in an atomic WriteBatch
  const batch = aliceDb.batch();
  const nowTs = pastDate(1000);
  batch.update(aliceDb.collection("premium_codes").doc("SATHI-1M-TEST1"), {
    status: "used",
    usedBy: ALICE_UID,
    updatedAt: nowTs,
  });
  batch.update(aliceDb.collection("users").doc(ALICE_UID), {
    plan: "premium",
    redeemedCodeId: "SATHI-1M-TEST1",
    planExpiresAt: Date.now() + 30 * 24 * 60 * 60 * 1000,
    updatedAt: nowTs,
  });
  await assertSucceeds(batch.commit());

  // Reusing the same single-use code must fail
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      userName: "Bob",
      plan: "free",
      banned: false,
      preferredLanguage: "bn",
      preferredVoice: "female",
      createdAt: pastDate(5000),
      updatedAt: pastDate(5000),
    })
  );
  const bobBatch = bobDb.batch();
  bobBatch.update(bobDb.collection("premium_codes").doc("SATHI-1M-TEST1"), {
    status: "used",
    usedBy: BOB_UID,
    updatedAt: pastDate(500),
  });
  bobBatch.update(bobDb.collection("users").doc(BOB_UID), {
    plan: "premium",
    redeemedCodeId: "SATHI-1M-TEST1",
    planExpiresAt: Date.now() + 30 * 24 * 60 * 60 * 1000,
    updatedAt: pastDate(500),
  });
  await assertFails(bobBatch.commit());

  // Admin writes global config and immutable activity log
  await assertSucceeds(
    adminDb.collection("app_config").doc("global").set({
      cameraEnabledForFree: false,
      longTermMemoryForFree: false,
      translationForFree: true,
      dayPlanningForFree: false,
      announcementText: "স্বাগতম সাথী অ্যাপে!",
      announcementActive: true,
      updatedBy: ADMIN_UID,
      updatedAt: pastDate(500),
    })
  );

  // Normal user can read global config, cannot write it
  await assertSucceeds(aliceDb.collection("app_config").doc("global").get());
  await assertFails(
    aliceDb.collection("app_config").doc("global").update({
      cameraEnabledForFree: true,
      updatedAt: pastDate(200),
    })
  );

  // Activity log create succeeds for admin, update/delete fail
  await assertSucceeds(
    adminDb.collection("activity_logs").doc("log_1").set({
      actorUid: ADMIN_UID,
      actorEmail: ADMIN_EMAIL,
      action: "CREATE_CODE",
      target: "SATHI-1M-TEST1",
      details: "Created 1 month code",
      createdAt: pastDate(500),
    })
  );
  await assertFails(adminDb.collection("activity_logs").doc("log_1").delete());
  await assertFails(aliceDb.collection("activity_logs").get());
});
