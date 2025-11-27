const { onCall } = require("firebase-functions/v2/https");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { logger } = require("firebase-functions");
const admin = require("firebase-admin");
const nodemailer = require("nodemailer");

admin.initializeApp();

// ======================================================
// 1. ✉️ Email Verification (네 기존 코드 그대로 유지)
// ======================================================

// 환경변수 가져오기
const gmailUser = process.env.GMAIL_USER;
const gmailPass = process.env.GMAIL_PASS;

// Gmail SMTP 설정
const transporter = nodemailer.createTransport({
  service: "gmail",
  auth: {
    user: gmailUser,
    pass: gmailPass,
  },
});

// 이메일 인증 코드 발송
exports.sendVerificationCode = onCall(async (req) => {
  const rawEmail = req.data?.email || "";
  const email = rawEmail.trim().toLowerCase();
  if (!email) throw new Error("Missing email");

  const code = String(Math.floor(100000 + Math.random() * 900000)); // 6자리 코드
  const expiresAt = Date.now() + 10 * 60 * 1000;

  await admin.firestore().collection("email_verifications")
    .doc(email).set({ code, expiresAt });

  await transporter.sendMail({
    from: `"LendMark" <${gmailUser}>`,
    to: email,
    subject: "[LendMark] Email Authentication Code",
    html: `
      <div style="font-family:sans-serif;">
        <h2>Welcome to the LendMark application!</h2>
        <p>Please enter the authentication code below into the app:</p>
        <h1 style="letter-spacing:4px;">${code}</h1>
        <p>Valid time: 10 minutes</p>
      </div>
    `,
  });

  return { ok: true };
});

exports.verifyEmailCode = onCall(async (req) => {
  const rawEmail = req.data?.email || "";
  const email = rawEmail.trim().toLowerCase();
  const code = String((req.data?.code || "").toString().trim());

  const snap = await admin.firestore().collection("email_verifications")
    .doc(email).get();
  if (!snap.exists) return { ok: false, reason: "NOT_FOUND" };

  const { code: saved, expiresAt } = snap.data();

  console.log("verify", { email, inputCode: code, saved, expiresAt });

  if (Date.now() > expiresAt) return { ok: false, reason: "EXPIRED" };
  if (saved !== code)         return { ok: false, reason: "INVALID" };

  await snap.ref.delete();
  return { ok: true };
});

// ======================================================
// 2. 📌예약 기능 추가 (여기부터 새 기능!!!)
// ======================================================

// (A) 지난 7일 지난 예약 → 자동 expired 처리 (15분마다 실행)
/**
 * 7일 지난 예약을 자동으로 expired 처리하는 스케줄러
 * --> 15분마다 실행됨
 */
exports.expireOldReservations = onSchedule("every 15 minutes", async () => {
  const now = Date.now();
  const expireThreshold = now - 7 * 24 * 60 * 60 * 1000; // 7일 전 timestamp

  const db = admin.firestore();

  // status = approved 인 예약 중에서 오래된 항목 탐색
  const snapshot = await db.collection("reservations")
    .where("status", "==", "approved")
    .where("timestamp", "<=", expireThreshold)
    .get();

  if (snapshot.empty) {
    console.log("No reservations to expire.");
    return null;
  }

  console.log(`Found ${snapshot.size} old reservations. Expiring...`);

  const batch = db.batch();

  snapshot.forEach((doc) => {
    batch.update(doc.ref, { status: "expired" });
  });

  await batch.commit();
  console.log("Expiration completed!");

  return null;
});

// (B) 예약 생성 시 충돌 체크 + 저장 (안드로이드에서 호출)
exports.createReservation = onCall(async (req) => {
  const db = admin.firestore();

  const {
    userId,
    userName,
    major,
    people,
    purpose,
    buildingId,
    roomId,
    day,
    date,
    periodStart,
    periodEnd
  } = req.data;

  // 1) 충돌(오버랩) 검사
  const conflict = await db.collection("reservations")
    .where("buildingId", "==", buildingId)
    .where("roomId", "==", roomId)
    .where("date", "==", date)
    .where("status", "==", "approved")
    .get();

  for (const doc of conflict.docs) {
    const r = doc.data();
    const s = r.periodStart;
    const e = r.periodEnd;

    const overlapped = !(periodEnd < s || periodStart > e);
    if (overlapped) {
      return { success: false, reason: "TIME_CONFLICT" };
    }
  }

  // 2) 충돌 없으면 예약 저장
  const newReservation = {
    userId,
    userName,
    major,
    people,
    purpose,
    buildingId,
    roomId,
    day,
    date,
    periodStart,
    periodEnd,
    timestamp: Date.now(),
    status: "approved"
  };

  await db.collection("reservations").add(newReservation);
  return { success: true };
});
