require('dotenv').config();

const { BUCKET_PAR, REGION } = process.env;

function getParUrl() {
  const parReg = /\/p\/([\w-]+)\/n\/([\w-]+)\/b\/([\w-]+)\/o\/$/;
  let par = parReg.test(BUCKET_PAR) && BUCKET_PAR;
  if (par) {
    if (!/^https:\/\//.test(par)) {
      par = REGION ? `https://objectstorage.${REGION}.oraclecloud.com${par}` : null;
    }
    return par;
  }
}

function getBucketUrl() {
  const par = getParUrl();
  return par && par.replace(/\/p\/([\w-]+)/, '');
}

module.exports = {
  dist: 'dist',
  rawParUrl: BUCKET_PAR,
  parUrl: getParUrl(),
  bucketUrl: getBucketUrl(),
  cache: {
    maxAge: 604800
  },
};
