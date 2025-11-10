const puppeteer = require('puppeteer');
const admin = require('firebase-admin');
const dotenv = require('dotenv');
const fs = require('fs');

dotenv.config();

// 1. Firebase 초기화
admin.initializeApp({
  credential: admin.credential.cert(require('./serviceAccountKey.json')),
  projectId: process.env.FIREBASE_PROJECT_ID,
});
const db = admin.firestore();

// 2. 학기 설정 (1=1학기, 2=2학기)
const SEMESTER = '2';
const TERM_NAME = '2025-fall';

(async () => {
  const departments = JSON.parse(fs.readFileSync('./departments.json', 'utf-8'));
  console.log(`Total ${departments.length} department search starting...`);

  const browser = await puppeteer.launch({ headless: 'new' });
  const page = await browser.newPage();

  await page.goto('https://for-s.seoultech.ac.kr/html/pub/schedule.jsp', { waitUntil: 'networkidle2' });
  await page.select('#cbo_lang', 'en');
  await new Promise(resolve => setTimeout(resolve, 500));
  await page.select('#cbo_Smst', SEMESTER);
  await new Promise(resolve => setTimeout(resolve, 300));

  for (const { college, department } of departments) {
    console.log(`${college} - ${department}`);

    await page.reload({ waitUntil: 'networkidle2' });
    await page.select('#cbo_lang', 'en');
    await page.select('#cbo_Smst', SEMESTER);
    await new Promise(resolve => setTimeout(resolve, 300));

    // 학과 옵션 찾기
    const departmentOptions = await page.$$eval('#cbo_Less option', options =>
      options.map(o => ({ value: o.value, text: o.textContent.trim() }))
    );
    const matched = departmentOptions.find(opt => opt.text === department);
    if (!matched) {
      console.warn(`Department not found: ${department}`);
      continue;
    }

    await page.select('#cbo_Less', matched.value);
    await new Promise(resolve => setTimeout(resolve, 300));

    try {
      await Promise.all([
        page.click('#btn_ReportSearch'),
        page.waitForSelector('#grd_ScheduleMain tbody tr', { timeout: 10000 })
      ]);
    } catch {
      console.warn(`No data for: ${department}`);
      continue;
    }

    const lectures = await page.$$eval('#grd_ScheduleMain tbody tr', trs => {
      return trs.map(tr => {
        const tds = Array.from(tr.querySelectorAll('td'));
        const subject = tds[3]?.getAttribute('title')?.trim() || '';
        const timeText = tds[10]?.getAttribute('title')?.trim() || '';
        const room = tds[19]?.getAttribute('title')?.trim() || '';

        const match = timeText.match(/^([A-Za-z]+)\(([^)]+)\)/);
        let day = '', periods = [];
        if (match) {
          day = match[1];
          const numbers = match[2].match(/\d+/g);
          if (numbers) periods = numbers.map(Number);
        }

        return { subject, day, periods, room };
      }).filter(l => l.subject && l.room);
    });

    if (lectures.length === 0) {
      console.warn(`${department} has no valid lecture data`);
      continue;
    }

    const deptId = department
      .toLowerCase()
      .replace(/department-of-/g, '')
      .replace(/ /g, '-')
      .replace(/[^a-z0-9-]/g, '');

    await db.collection('timetables')
      .doc(TERM_NAME)
      .collection('departments')
      .doc(deptId)
      .set({
        college,
        department,
        lectures
      });

    console.log(`Saved: ${department} (${lectures.length} lectures)`);
    await new Promise(resolve => setTimeout(resolve, 200));
  }

  await browser.close();
  console.log('All timetable data uploaded to Firestore.');
})();
