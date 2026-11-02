const axios = require('axios');
const { SOAP_URL, SOAP_NS } = require('./config');

function escXml(s) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

function envelope(method, paramsXml = '') {
  return `<?xml version="1.0" encoding="UTF-8"?>
<S:Envelope xmlns:S="http://schemas.xmlsoap.org/soap/envelope/">
  <S:Body>
    <ns2:${method} xmlns:ns2="${SOAP_NS}">
      ${paramsXml}
    </ns2:${method}>
  </S:Body>
</S:Envelope>`;
}

async function call(method, paramsXml = '') {
  const body = envelope(method, paramsXml);
  const res = await axios.post(SOAP_URL, body, {
    headers: {
      'Content-Type': 'text/xml; charset=utf-8',
      'SOAPAction': `"${SOAP_NS}${method}"`,
    },
    timeout: 5000,
  });
  return res.data;
}

function stripHtml(s) {
  return s
    .replace(/<[^>]+>/g, '')
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .trim();
}

function parseGetListResponse(xml) {
  // Response contains multiple <return> elements, one per book
  const matches = [...xml.matchAll(/<return>([\s\S]*?)<\/return>/g)];
  return matches
    .map(m => stripHtml(m[1].trim()))
    .filter(Boolean);
}

function parseGetClockResponse(xml) {
  const match = xml.match(/<return>(-?\d+)<\/return>/);
  return match ? parseInt(match[1], 10) : -1;
}

module.exports = {
  getList: () => call('getList'),
  setTitleAndNumber: (title, number) =>
    call('setTitleAndNumber',
      `<title>${escXml(title)}</title><number>${parseInt(number, 10)}</number>`),
  setStrings: (s1, s2) =>
    call('setStrings',
      `<string1>${escXml(s1)}</string1><string2>${escXml(s2)}</string2>`),
  setBlank: () => call('setBlank'),
  getClock: () => call('getClock'),
  setClock: (value) => call('setClock', `<value>${escXml(value)}</value>`),
  addBook: (title) => call('addBook', `<title>${escXml(title)}</title>`),
  deleteBook: (title) => call('deleteBook', `<title>${escXml(title)}</title>`),
  parseGetListResponse,
  parseGetClockResponse,
};
