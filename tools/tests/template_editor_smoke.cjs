const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { chromium } = require('playwright');

async function run() {
  const root = path.resolve(__dirname, '../..');
  const browser = await chromium.launch({ headless: true, channel: process.env.PLAYWRIGHT_CHANNEL || 'msedge' });
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, acceptDownloads: true });
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    page.on('dialog', dialog => dialog.accept());
    const file = path.join(root, 'tools/等保取证设备类型模板编辑器.html');
    await page.goto(pathToFileURL(file).href);
    const input = { formatVersion: 1, templates: [{ name: 'Firewall', items: [
      { name: 'Policy', displayOrder: 0, isKeyScreenshot: true, wordOrder: 0, excludedFromProgress: false, verificationMethod: 'show policy\n<config>& "test"' },
      { name: 'Other legacy', displayOrder: 1, isKeyScreenshot: false, wordOrder: null, excludedFromProgress: false },
      { name: '其他', displayOrder: 2, isKeyScreenshot: false, wordOrder: null, excludedFromProgress: true, verificationMethod: 'Other instructions' },
    ] }] };
    await page.locator('#fileInput').setInputFiles({ name: 'templates.json', mimeType: 'application/json', buffer: Buffer.from(JSON.stringify(input)) });
    const methods = page.locator('[data-field="verification-method"]');
    await methods.first().waitFor();
    assert.equal(await methods.first().inputValue(), input.templates[0].items[0].verificationMethod);
    assert.equal(await methods.nth(1).inputValue(), '');
    const updated = 'show config\n检查命令：<test> & "quotes"\n</textarea><script>throw Error("unsafe")</script>';
    await methods.first().fill(updated);
    await page.locator('[data-action="duplicate-template"]').click();
    assert.equal(await methods.first().inputValue(), updated);
    assert.equal(await methods.last().inputValue(), 'Other instructions');
    const pending = page.waitForEvent('download');
    await page.locator('#exportButton').click();
    const download = await pending;
    const output = JSON.parse(fs.readFileSync(await download.path(), 'utf8'));
    assert.equal(output.templates.length, 2);
    assert.equal(output.templates[0].items[0].verificationMethod, updated);
    assert.equal(output.templates[1].items[0].verificationMethod, updated);
    assert.equal(output.templates[1].items.at(-1).verificationMethod, 'Other instructions');
    const reportDir = path.join(root, 'app/build/reports/template-editor');
    fs.mkdirSync(reportDir, { recursive: true });
    await page.screenshot({ path: path.join(reportDir, 'desktop.png'), fullPage: true });
    await page.setViewportSize({ width: 390, height: 844 });
    await page.screenshot({ path: path.join(reportDir, 'mobile.png'), fullPage: true });
    assert.deepEqual(errors, []);
    console.log('PASS: old JSON compatibility, multiline method editing, escaped markup, template copying including Other, JSON export round trip, no JS errors');
  } finally { await browser.close(); }
}
run().catch(error => { console.error(error); process.exitCode = 1; });
