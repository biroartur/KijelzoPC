const router = require('express').Router();
const soap   = require('../soap');
const fs     = require('fs');
const path   = require('path');

const PROPS_PATH = process.env.SERVER_PROPS_PATH ||
  path.join('C:', 'KijelzoPC', 'server.properties');

function setClockInPropsFile(value) {
  // "off": KijelzoPC saves the clock itself (setClock), e.g. on the Raspberry Pi
  if (PROPS_PATH === 'off') return;
  try {
    let content = fs.readFileSync(PROPS_PATH, 'utf8');
    content = content.replace(/^(content\.time\s*=\s*)\d/m, `$1${value}`);
    fs.writeFileSync(PROPS_PATH, content, 'utf8');
  } catch (err) {
    console.error('[PROPS] Failed to write server.properties:', err.message);
  }
}

// Display endpoints

router.post('/hymn', async (req, res) => {
  const { title, number } = req.body;
  if (!title || number === undefined || number === null) {
    return res.status(422).json({ error: 'validation_failed' });
  }
  const num = parseInt(number, 10);
  if (isNaN(num) || num < 0 || num > 999) {
    return res.status(422).json({ error: 'validation_failed' });
  }
  try {
    await soap.setTitleAndNumber(title, num);
    res.json({ ok: true });
  } catch (err) {
    console.error('[SOAP] setTitleAndNumber failed:', err.message);
    res.status(503).json({ error: 'soap_unavailable' });
  }
});

router.post('/strings', async (req, res) => {
  const { line1, line2 } = req.body;
  if (line1 === undefined || line2 === undefined) {
    return res.status(422).json({ error: 'validation_failed' });
  }
  try {
    await soap.setStrings(String(line1), String(line2));
    res.json({ ok: true });
  } catch (err) {
    console.error('[SOAP] setStrings failed:', err.message);
    res.status(503).json({ error: 'soap_unavailable' });
  }
});

router.post('/blank', async (_req, res) => {
  try {
    await soap.setBlank();
    res.json({ ok: true });
  } catch (err) {
    console.error('[SOAP] setBlank failed:', err.message);
    res.status(503).json({ error: 'soap_unavailable' });
  }
});

// What the display shows now. Servers before v1.5 have no getCurrent: they answer with a SOAP
// fault, reported as { supported: false } so the page simply hides the line.
router.get('/current', async (_req, res) => {
  try {
    const xml = await soap.getCurrent();
    res.json({ supported: true, ...soap.parseGetCurrentResponse(xml) });
  } catch (err) {
    if (err.response) return res.json({ supported: false });
    console.error('[SOAP] getCurrent failed:', err.message);
    res.status(503).json({ error: 'soap_unavailable' });
  }
});

router.get('/clock', async (_req, res) => {
  try {
    const xml = await soap.getClock();
    const clock = soap.parseGetClockResponse(xml);
    res.json({ clock });
  } catch (err) {
    console.error('[SOAP] getClock failed:', err.message);
    res.status(503).json({ error: 'soap_unavailable' });
  }
});

router.post('/clock', async (req, res) => {
  const { value } = req.body;
  if (value !== '0' && value !== '1') {
    return res.status(422).json({ error: 'validation_failed' });
  }
  // Write to server.properties first (best-effort, never blocks the response)
  setClockInPropsFile(value);
  try {
    await soap.setClock(value);
    res.json({ ok: true });
  } catch (err) {
    console.error('[SOAP] setClock failed:', err.message);
    res.status(503).json({ error: 'soap_unavailable' });
  }
});

module.exports = router;
