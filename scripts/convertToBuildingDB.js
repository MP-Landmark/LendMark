// scripts/convertToBuildingDB.js
/**
 * timetables/{term}/departments/<deptId> 의 lectures를
 * buildings/{code} 구조로 집계해서 저장.
 *
 * Building 문서 스키마:
 * {
 *   name: string,                // 건물명 (e.g., "Areum Hall")
 *   code: number,                // 건물 고유 코드 (정수, e.g., 54)
 *   roomCount: number,           // 강의실 개수
 *   imageUrl: string,            // 썸네일 (초기엔 빈문자열)
 *   naverMapLat: number,         // 지도 위도 (초기 0)
 *   naverMapLng: number,         // 지도 경도 (초기 0)
 *   timetable: {
 *     [roomNumber: string]: {
 *       schedule: Array<{
 *         day: string,           // "Mon" ~ "Fri"
 *         periodStart: number,   // 시작 교시 index
 *         periodEnd: number,     // 종료 교시 index
 *         subject: string,       // 과목명
 *         department: string     // 학과명
 *       }>,
 *       reviews: string[]        // 초기 []
 *     }
 *   }
 * }
 */

const fs = require("fs");
const path = require("path");
const admin = require("firebase-admin");
require("dotenv").config({ path: path.join(process.cwd(), ".env") });

// ---- 0) Firebase 초기화 ----
const keyPath = path.join(__dirname, "serviceAccountKey.json");
const svcKey = JSON.parse(fs.readFileSync(keyPath, "utf-8"));

if (!admin.apps.length) {
  admin.initializeApp({
    credential: admin.credential.cert(svcKey),
    projectId: process.env.FIREBASE_PROJECT_ID || svcKey.project_id,
  });
}
const db = admin.firestore();
console.log("Firebase initialized");

// ---- 1) 유틸: department 이름 → Firestore 문서 ID 슬러그 ----
function slugifyDepartment(name) {
  return (name || "")
    .toLowerCase()
    .replace(/&/g, "and")
    .replace(/\s+/g, "-")
    .replace(/[^a-z0-9-]/g, "");
}

// ---- 2) 유틸: 강의실 문자열에서 여러 빌딩/호실 파싱 ----
// 예: "Areum Hall(054)-219 Sangsang Hall(053)-714"
function* parseRooms(roomStr) {
  if (!roomStr) return;
  const re = /([A-Za-z][A-Za-z\s]*?Hall)\((\d+)\)-(\d+)/g;
  let m;
  while ((m = re.exec(roomStr)) !== null) {
    const buildingName = m[1].trim();
    const buildingCodeStr = m[2].replace(/^0+/, ""); // 054 -> 54
    const buildingCode = Number(buildingCodeStr);
    const roomNumber = m[3];
    yield { buildingName, buildingCode, roomNumber };
  }
}

// ---- 3) 메인 변환 함수 ----
async function convertDepartmentsToBuildings(term = "2025-fall") {
  console.log(`Converting by reading: timetables/${term}/departments/\*`);


  // 3-1) 로컬 학과 목록 로드
  const depJsonPath = path.join(__dirname, "departments.json");
  const departments = JSON.parse(fs.readFileSync(depJsonPath, "utf-8"));

  // 3-2) 누적용 메모리
  /**
   * @type {{
   *   [code: number]: {
   *     name: string;
   *     code: number;
   *     roomCount: number;
   *     imageUrl: string;
   *     naverMapLat: number;
   *     naverMapLng: number;
   *     timetable: {
   *       [room: string]: {
   *         schedule: any[];
   *         reviews: string[];
   *       };
   *     };
   *   };
   * }}
   */
  const buildings = {};


  // 3-3) 각 학과 문서 순회
  for (const { department } of departments) {
    const deptId = slugifyDepartment(department);
    const docRef = db
      .collection("timetables")
      .doc(term)
      .collection("departments")
      .doc(deptId);

    const snap = await docRef.get();
    if (!snap.exists) {
      console.warn(`Not found: departments/${deptId}`);
      continue;
    }

    const { lectures = [] } = snap.data() || {};
    if (!Array.isArray(lectures) || lectures.length === 0) {
      console.warn(`Empty lectures: ${department}`);
      continue;
    }

    // 3-4) 강의 배열 → 빌딩 단위 집계
    for (const lec of lectures) {
      const day = lec.day || "";
      const periods = Array.isArray(lec.periods) ? lec.periods : [];
      if (periods.length === 0) continue;

      const periodStart = periods[0];
      const periodEnd = periods[periods.length - 1];
      const subject = lec.subject || "";
      const roomStr = lec.room || "";

      // 빌딩/호수 여러 개일 수 있음
      let parsedAny = false;
      for (const { buildingName, buildingCode, roomNumber } of parseRooms(
        roomStr
      )) {
        parsedAny = true;

        // 메모리에 빌딩 객체 보장
        if (!buildings[buildingCode]) {
          buildings[buildingCode] = {
            name: buildingName,
            code: buildingCode,
            roomCount: 0,
            imageUrl: "",
            naverMapLat: 0,
            naverMapLng: 0,
            timetable: {},
          };
        }
        const B = buildings[buildingCode];

        // 방 객체 보장
        if (!B.timetable[roomNumber]) {
          B.timetable[roomNumber] = { schedule: [], reviews: [] };
          B.roomCount++;
        }

        // 스케줄 push
        B.timetable[roomNumber].schedule.push({
          day,
          periodStart,
          periodEnd,
          subject,
          department,
        });
      }

      // 포맷이 달라 파싱 실패한 경우 로그만 남김(필요 시 추가 포맷 처리)
      if (!parsedAny && roomStr) {
        console.warn("Unparsed room string:", roomStr, "| dept:", department);
      }
    }
  }

  // 3-5) Firestore 저장 (batch, 400개 단위)
  const codes = Object.keys(buildings);
  console.log(`Writing ${codes.length} buildings...`);

  const chunkSize = 400;
  for (let i = 0; i < codes.length; i += chunkSize) {
    const batch = db.batch();
    const part = codes.slice(i, i + chunkSize);
    for (const code of part) {
      const data = buildings[code];
      const ref = db.collection("buildings").doc(String(code));
      batch.set(ref, data, { merge: true });
    }
    await batch.commit();
    console.log(`  - committed ${i + part.length}/${codes.length}`);
  }

  console.log("Done.");
}

// ---- 4) 실행 ----
convertDepartmentsToBuildings("2025-fall").catch((e) => {
  console.error("Error during conversion:", e);
  process.exit(1);
});
