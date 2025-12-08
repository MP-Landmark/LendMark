/*******************************************************
 * 0. 기본 의존성 & 초기화
 *******************************************************/
require("dotenv").config();
const { onCall } = require("firebase-functions/v2/https");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { logger } = require("firebase-functions");
const admin = require("firebase-admin");
const nodemailer = require("nodemailer");
const OpenAI = require("openai").default;

admin.initializeApp();

const REGION = "asia-northeast3";

/* ======================================================
   공통: 날짜 + 교시 → 시작/종료 Timestamp 계산
   ====================================================== */
function buildStartEndTimestamp(dateStr, periodStart, periodEnd) {
  // dateStr: "YYYY-MM-DD"
  const [year, month, day] = dateStr.split("-").map(Number);
  const baseHour = 8; // 0교시 = 08시 기준

  // 시작 시간: 8 + periodStart 시
  const startDate = new Date(year, month - 1, day, baseHour + periodStart, 0, 0);
  // 종료 시간: 8 + (periodEnd + 1) 시 (교시 끝나는 시각)
  const endDate = new Date(year, month - 1, day, baseHour + periodEnd + 1, 0, 0);

  return {
    startAt: admin.firestore.Timestamp.fromDate(startDate),
    endAt: admin.firestore.Timestamp.fromDate(endDate),
  };
}


/*******************************************************
 * 1. 이메일 인증
 *******************************************************/
const gmailUser = process.env.GMAIL_USER;
const gmailPass = process.env.GMAIL_PASS;

const transporter = nodemailer.createTransport({
  service: "gmail",
  auth: { user: gmailUser, pass: gmailPass },
});

// 1-1 이메일 인증코드 발송
exports.sendVerificationCode = onCall({ region: "us-central1" }, async (req) => {
  const email = (req.data?.email || "").trim().toLowerCase();
  if (!email) throw new Error("Missing email");

  const code = String(Math.floor(100000 + Math.random() * 900000));
  const expiresAt = Date.now() + 10 * 60 * 1000;

  await admin.firestore()
    .collection("email_verifications")
    .doc(email)
    .set({ code, expiresAt });

  await transporter.sendMail({
    from: `"LendMark" <${gmailUser}>`,
    to: email,
    subject: "[LendMark] Email Authentication Code",
    html: `
      <div style="font-family:sans-serif;">
        <h2>Welcome to LendMark!</h2>
        <p>Please enter the authentication code below:</p>
        <h1 style="letter-spacing:4px;">${code}</h1>
        <p>Valid for 10 minutes.</p>
      </div>
    `,
  });

  return { ok: true };
});

// 1-2 인증코드 검증
exports.verifyEmailCode = onCall({ region: "us-central1" }, async (req) => {
  const email = (req.data?.email || "").trim().toLowerCase();
  const code = (req.data?.code || "").trim();

  const snap = await admin.firestore()
    .collection("email_verifications")
    .doc(email)
    .get();

  if (!snap.exists) return { ok: false, reason: "NOT_FOUND" };

  const { code: saved, expiresAt } = snap.data();
  if (Date.now() > expiresAt) return { ok: false, reason: "EXPIRED" };
  if (saved !== code) return { ok: false, reason: "INVALID" };

  await snap.ref.delete();
  return { ok: true };
});



/*******************************************************
 * 2. 예약 상태 자동 업데이트
 *******************************************************/

// 2-1 지난 예약 finished 처리 (30분마다 실행)
exports.finishPastReservations = onSchedule(
  { schedule: "every 30 minutes", region: REGION },
  async () => {
    const db = admin.firestore();
    const now = new Date();

    const yyyy = now.getFullYear();
    const mm = String(now.getMonth() + 1).padStart(2, "0");
    const dd = String(now.getDate()).padStart(2, "0");
    const todayStr = `${yyyy}-${mm}-${dd}`;

    const snapshot = await db
      .collection("reservations")
      .where("status", "==", "approved")
      .get();

    const batch = db.batch();

    snapshot.forEach((doc) => {
      const r = doc.data();
      const dateStr = r.date;
      const endHour = 8 + (r.periodEnd + 1);

      if (dateStr < todayStr) {
        batch.update(doc.ref, { status: "finished" });
      } else if (dateStr === todayStr && now.getHours() >= endHour) {
        batch.update(doc.ref, { status: "finished" });
      }
    });

    await batch.commit();
    logger.info("finishPastReservations: done");
  }
);

// 2-2 finished 후 1주 지난 예약 expired 처리
exports.expireOldReservations = onSchedule(
  { schedule: "every day 00:00", region: REGION },
  async () => {
    const db = admin.firestore();
    const oneWeekAgo = Date.now() - 7 * 24 * 60 * 60 * 1000;

    const snap = await db
      .collection("reservations")
      .where("status", "==", "finished")
      .where("timestamp", "<", oneWeekAgo)
      .get();

    const batch = db.batch();
    snap.forEach((doc) => batch.update(doc.ref, { status: "expired" }));
    await batch.commit();

    logger.info(`expireOldReservations: expired ${snap.size}`);
  }
);



/* ======================================================
   3. 예약 생성 (충돌 검사 + startAt/endAt + 알림 스케줄)
   ====================================================== */
exports.createReservation = onCall({ region: REGION }, async (req) => {

  console.log("REQ DATA =", req.data);

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
    date,        // "YYYY-MM-DD"
    periodStart, // 숫자 (0 = 08시, 1 = 09시 ...)
    periodEnd,   // 숫자
  } = req.data;

  /* 3-1. 시간 충돌 체크 (기존 로직 그대로) */
  const snap = await db
    .collection("reservations")
    .where("buildingId", "==", buildingId)
    .where("roomId", "==", roomId)
    .where("date", "==", date)
    .where("status", "==", "approved")
    .get();

  for (const doc of snap.docs) {
    const r = doc.data();
    const s = r.periodStart;
    const e = r.periodEnd;

    const overlapped = !(periodEnd < s || periodStart > e);
    if (overlapped) {
      return { success: false, reason: "TIME_CONFLICT" };
    }
  }

  /* 3-2. startAt / endAt 계산 */

  const ps = Number(periodStart);
  const pe = Number(periodEnd);
  const { startAt, endAt } = buildStartEndTimestamp(date, ps, pe);
  console.log("startAt=", startAt.toDate(), "endAt=", endAt.toDate());


  /* 3-3. 예약 저장 */
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
    startAt,   // ← 새로 추가
    endAt,     // ← 새로 추가
    timestamp: Date.now(),
    status: "approved",
  };

  const reservationRef = await db.collection("reservations").add(newReservation);

  /* 3-4. 유저 FCM 토큰 조회 */
  const userDoc = await db.collection("users").doc(userId).get();
  const fcmToken = userDoc.get("fcmToken");

  // 토큰이 없으면 예약까지만 하고 끝
  if (!fcmToken) {
    logger.warn(`createReservation: no fcmToken for user ${userId}`);
    return { success: true, warning: "NO_FCM_TOKEN" };

  }

  console.log("Saved reservation:", newReservation);


  /* 3-5. 알림 시간 계산 (시작 30분 전 / 종료 10분 전) */
  const startDate = startAt.toDate();
  const endDate = endAt.toDate();

  const startMinus30 = new Date(startDate.getTime() - 30 * 60 * 1000);
  const endMinus10 = new Date(endDate.getTime() - 10 * 60 * 1000);

  const sendAtStart = admin.firestore.Timestamp.fromDate(startMinus30);
  const sendAtEnd = admin.firestore.Timestamp.fromDate(endMinus10);

  /* 3-6. scheduled_notifications 컬렉션에 스케줄 2개 저장 */

  // ① 시작 30분 전 알림
  await db.collection("scheduled_notifications").add({
    userId,
    reservationId: reservationRef.id,
    token: fcmToken,
    title: "예약 시작 30분 전 알림",
    body: `${buildingId} ${roomId} 예약이 30분 뒤에 시작됩니다.`,
    sendAt: sendAtStart,
    sent: false,
    type: "start",
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  // ② 종료 10분 전 알림
  await db.collection("scheduled_notifications").add({
    userId,
    reservationId: reservationRef.id,
    token: fcmToken,
    title: "예약 종료 10분 전 알림",
    body: `${buildingId} ${roomId} 예약 종료까지 10분 남았습니다.`,
    sendAt: sendAtEnd,
    sent: false,
    type: "end",
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  return { success: true };
});




/*******************************************************
 * 5. AI 강의실 추천
 *******************************************************/
exports.chatbotAvailableRoomsV2 = onCall({ region: REGION }, async (req) => {
  try {
    const openai = new OpenAI({
      apiKey: process.env.OPENAI_API_KEY,
    });

    const { buildingId, buildingName, date, hour } = req.data;
    if (!buildingId || !buildingName || !date || hour === undefined)
      throw new Error("Missing required fields");

    const db = admin.firestore();

    const targetPeriod = hour - 8;
    const dayCode = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"][
      new Date(date).getDay()
    ];

    const buildingDoc = await db.collection("buildings").doc(buildingId).get();
    const timetable = buildingDoc.get("timetable") ?? {};

    const reservationSnap = await db.collection("reservations")
      .where("buildingId", "==", buildingId)
      .where("date", "==", date)
      .where("status", "==", "approved")
      .get();

    const reservations = reservationSnap.docs.map((d) => d.data());

    // 강의실 체크 함수
    function isAvailable(roomId, roomData) {
      // (A) 수업 중인지
      for (const ev of roomData?.schedule ?? []) {
        if (ev.day === dayCode) {
          if (!(targetPeriod < ev.periodStart || targetPeriod > ev.periodEnd))
            return false;
        }
      }

      // (B) 예약 중인지
      for (const r of reservations) {
        if (r.roomId === roomId) {
          if (!(targetPeriod < r.periodStart || targetPeriod > r.periodEnd))
            return false;
        }
      }

      return true;
    }

    const availableRooms = Object.entries(timetable)
      .filter(([roomId, roomData]) => isAvailable(roomId, roomData))
      .map(([roomId]) => roomId);

    const prettyList =
      availableRooms.length > 0
        ? availableRooms.map((r) => `- ${r}`).join("\n")
        : "없음";

    const prompt = `
날짜: ${date}
시간: ${hour}시
건물: ${buildingName}

가능한 강의실 목록:
${prettyList}

학생이 이해하기 쉽게 자연스럽게 설명해줘.
`;

    const completion = await openai.chat.completions.create({
      model: "gpt-4o-mini",
      messages: [{ role: "user", content: prompt }],
    });

    return {
      ok: true,
      answer: completion.choices[0].message.content,
      rooms: availableRooms,
    };
  } catch (err) {
    logger.error(err);
    return { ok: false, error: err.message };
  }
});
