const { onCall } = require("firebase-functions/v2/https");
const { logger } = require("firebase-functions");
const admin = require("firebase-admin");
const nodemailer = require("nodemailer");

admin.initializeApp();

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

  const code = String(Math.floor(100000 + Math.random() * 900000)); // "123456"
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
        `,});

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

  // 디버그 로그: Cloud Functions 로그에서 비교값 확인
  console.log("verify", { email, inputCode: code, saved, expiresAt });

  if (Date.now() > expiresAt) return { ok: false, reason: "EXPIRED" };
  if (saved !== code)       return { ok: false, reason: "INVALID" };

  await snap.ref.delete();
  return { ok: true };
});

