const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');
const sharp = require('sharp');

const root = path.resolve(__dirname, '../..');
const reportDir = path.join(root, 'app/build/reports/pc-viewer');

async function run() {
  fs.mkdirSync(reportDir, { recursive: true });
  const photos = await Promise.all(['#2e43a8', '#2d8a57'].map(async (background, index) => {
    const bitmap = await sharp({ create: { width: 1600, height: 1200, channels: 3, background } })
      .composite([{ input: { create: { width: 400, height: 300, channels: 3, background: '#fffdf8' } }, left: 80, top: 80 }])
      .png().toBuffer();
    const uri = `data:image/png;base64,${bitmap.toString('base64')}`;
    return index === 0
      ? { id: index + 1, sortOrder: index, originalPath: 'originals/test.heic', browserPath: uri, thumbnailPath: uri }
      : { id: index + 1, sortOrder: index, originalPath: uri, thumbnailPath: uri };
  }));
  const data = {
    project: { name: 'PC Viewer Test', createdAt: Date.now() },
    exportedAt: Date.now(),
    devices: [{
      id: 1, name: 'Firewall 01', typeName: 'Firewall', createdAt: Date.now(),
      recordedCount: 3, totalCount: 4, missingKeyScreenshotCount: 0,
      items: [
        { id: 1, name: 'Policy', displayOrder: 0, isRecorded: true, isCompliant: true, isKeyScreenshot: true, photos },
        { id: 2, name: 'Empty', displayOrder: 1, isRecorded: false, isCompliant: true, photos: [] },
        { id: 3, name: 'Failed', displayOrder: 2, isRecorded: true, isCompliant: false, note: 'Failed check', photos: [] },
        { id: 4, name: 'Not applicable', displayOrder: 3, isRecorded: true, isCompliant: true, isNotApplicable: true, photos: [] },
      ],
    }],
  };
  const html = fs.readFileSync(path.join(root, 'app/src/main/assets/project_viewer.html'), 'utf8')
    .replace('<script src="data.js"></script>', `<script>window.EVIDENCE_EXPORT = ${JSON.stringify(data)};</script>`);
  const browser = await chromium.launch({ headless: true, channel: process.env.PLAYWRIGHT_CHANNEL || 'msedge' });
  try {
    const errors = [];
    const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
    page.on('pageerror', error => errors.push(error.message));
    await page.setContent(html);
    assert.equal(await page.title(), data.project.name);
    assert.equal(await page.locator('.item-section .badge').filter({ hasText: '关键截图' }).count(), 0);
    assert.equal(await page.locator('#modal-download').count(), 0);
    await page.locator('.photo').first().click();
    await page.locator('#modal-image').evaluate(image => image.decode());
    assert.equal(await page.locator('#modal-image').getAttribute('src'), photos[0].browserPath);
    const transform = () => page.locator('#modal-image').evaluate(image => {
      const matrix = new DOMMatrixReadOnly(getComputedStyle(image).transform);
      return { scale: matrix.a, x: matrix.e, y: matrix.f };
    });
    const viewport = await page.locator('#modal-viewport').boundingBox();
    const center = { x: viewport.x + viewport.width / 2, y: viewport.y + viewport.height / 2 };
    await page.mouse.move(center.x, center.y);
    for (let index = 0; index < 8; index++) await page.mouse.wheel(0, -120);
    await page.waitForFunction(() => new DOMMatrixReadOnly(getComputedStyle(document.getElementById('modal-image')).transform).a > 2);
    await page.mouse.down();
    await page.mouse.move(center.x + 100, center.y + 60, { steps: 5 });
    const dragged = await transform();
    assert.ok(Math.abs(dragged.x - 100) < 1, `Horizontal drag was ${dragged.x}px`);
    assert.ok(Math.abs(dragged.y - 60) < 1, `Vertical drag was ${dragged.y}px`);
    await page.mouse.move(center.x + 5000, center.y, { steps: 5 });
    await page.mouse.up();
    const clamped = await transform();
    const maxX = await page.locator('#modal-image').evaluate(image => {
      const scale = new DOMMatrixReadOnly(getComputedStyle(image).transform).a;
      return Math.max(0, (image.offsetWidth * scale - document.getElementById('modal-viewport').clientWidth) / 2);
    });
    assert.ok(Math.abs(clamped.x - maxX) < 1, `Drag edge was ${clamped.x}px; expected ${maxX}px`);
    await page.mouse.move(center.x, center.y);
    await page.screenshot({ path: path.join(reportDir, 'desktop-zoomed.png') });
    await page.keyboard.press('ArrowRight');
    assert.deepEqual(await transform(), { scale: 1, x: 0, y: 0 });
    assert.ok((await page.locator('#modal-caption').innerText()).includes('2'));
    await page.keyboard.press('ArrowLeft');
    assert.ok((await page.locator('#modal-caption').innerText()).includes('1'));
    for (let index = 0; index < 8; index++) await page.mouse.wheel(0, -120);
    await page.waitForFunction(() => new DOMMatrixReadOnly(getComputedStyle(document.getElementById('modal-image')).transform).a > 2);
    for (let index = 0; index < 20; index++) await page.mouse.wheel(0, 120);
    await page.waitForFunction(() => new DOMMatrixReadOnly(getComputedStyle(document.getElementById('modal-image')).transform).a === 1);
    assert.deepEqual(await transform(), { scale: 1, x: 0, y: 0 });
    await page.locator('#modal-close').click();
    assert.equal(await page.locator('#photo-modal').isVisible(), false);
    await page.locator('.photo').first().click();
    assert.deepEqual(await transform(), { scale: 1, x: 0, y: 0 });
    await page.keyboard.press('Escape');
    await page.screenshot({ path: path.join(reportDir, 'desktop-records.png') });
    await page.locator('#compliance').selectOption('compliant');
    assert.deepEqual(await page.locator('.item-title').allTextContents(), ['Policy']);
    await page.locator('#compliance').selectOption('noncompliant');
    assert.deepEqual(await page.locator('.item-title').allTextContents(), ['Failed']);
    await page.locator('#compliance').selectOption('notApplicable');
    assert.deepEqual(await page.locator('.item-title').allTextContents(), ['Not applicable']);
    await page.locator('#compliance').selectOption('all');
    assert.deepEqual(await page.locator('.item-title').allTextContents(), ['Policy', 'Empty', 'Failed', 'Not applicable']);
    await page.locator('[data-type-filter="Firewall"]').check();
    assert.equal(await page.locator('.item-title').count(), 4);
    await page.locator('#compliance').selectOption('compliant');
    assert.deepEqual(await page.locator('.item-title').allTextContents(), ['Policy']);
    await page.locator('[data-mode="item"]').click();
    assert.equal(await page.locator('.item-title').filter({ hasText: 'Empty' }).count(), 0);
    await page.locator('[data-mode="device"]').click();
    await page.setViewportSize({ width: 390, height: 844 });
    await page.locator('.photo').first().click();
    await page.locator('#modal-image').evaluate(image => image.decode());
    await page.screenshot({ path: path.join(reportDir, 'mobile-photo.png') });
    const closeBox = await page.locator('#modal-close').boundingBox();
    assert.ok(closeBox.x >= 0 && closeBox.x + closeBox.width <= 390 && closeBox.y + closeBox.height <= 844);
    const mobileViewport = await page.locator('#modal-viewport').boundingBox();
    assert.ok(mobileViewport.x >= 0 && mobileViewport.x + mobileViewport.width <= 390);
    await page.locator('#modal-close').click();
    assert.deepEqual(errors, []);
    console.log('PASS: conclusion filters exclude unrecorded items, type filtering, badges, removed download, wheel zoom, 1:1 drag, bounds, keyboard navigation, reset, responsive close button, no JS errors');
  } finally {
    await browser.close();
  }
}

run().catch(error => { console.error(error); process.exitCode = 1; });
