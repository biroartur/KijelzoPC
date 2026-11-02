const router = require('express').Router();
const soap = require('../soap');

router.get(['/', ''], async (_req, res) => {
  try {
    const xml = await soap.getList();
    const books = soap.parseGetListResponse(xml);
    res.json({ books });
  } catch (err) {
    console.error('[SOAP] getList failed:', err.message);
    res.status(503).json({ error: 'soap_unavailable' });
  }
});

router.post('/', async (req, res) => {
  const { title } = req.body;
  if (!title || typeof title !== 'string' || !title.trim()) {
    return res.status(422).json({ error: 'validation_failed' });
  }
  try {
    await soap.addBook(title.trim());
    res.json({ ok: true });
  } catch (err) {
    console.error('[SOAP] addBook failed:', err.message);
    res.status(503).json({ error: 'soap_unavailable' });
  }
});

router.delete('/:title', async (req, res) => {
  const title = decodeURIComponent(req.params.title);
  if (!title) return res.status(422).json({ error: 'validation_failed' });
  try {
    await soap.deleteBook(title);
    res.json({ ok: true });
  } catch (err) {
    console.error('[SOAP] deleteBook failed:', err.message);
    res.status(503).json({ error: 'soap_unavailable' });
  }
});

module.exports = router;
