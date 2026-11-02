require('dotenv').config();

module.exports = {
  SOAP_URL: process.env.SOAP_URL || 'http://192.168.1.12:7777/wsdl/WSServer',
  SOAP_NS:  'http://modules.ws.server.com/',
  PORT:     parseInt(process.env.PORT || '3000', 10),
};
