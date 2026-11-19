// A fictional MusicFree-format plugin used only by unit tests.
// It talks to made-up hosts (*.example.com) that the tests answer with a fake HTTP client.
// It is not a real music source.
'use strict';
const axios = require('axios');
const CryptoJS = require('crypto-js');
const dayjs = require('dayjs');
const qs = require('qs');
const he = require('he');
const cheerio = require('cheerio');
const bigInt = require('big-integer');
const webdav = require('webdav'); // not supported by ByMusic: must load as a stub

module.exports = {
  platform: '虚构音源',
  version: '1.2.0',
  author: 'ByMusic tests',
  srcUrl: 'https://plugins.example.com/fictional.js',
  primaryKey: ['id'],
  cacheControl: 'no-cache',
  supportedSearchType: ['music', 'album'],
  userVariables: [{ key: 'token', name: '令牌', hint: '随便填' }],

  async search(query, page, type) {
    const res = await axios.get('https://api.example.com/search', {
      params: { q: query, page: page, type: type },
      headers: { 'X-Token': env.getUserVariables().token || '' }
    });
    return {
      isEnd: res.data.isEnd,
      data: res.data.songs.map(function (s) {
        return {
          id: String(s.id),
          title: he.decode(s.name),
          artist: s.singer,
          album: s.album,
          duration: s.duration,
          artwork: s.pic
        };
      })
    };
  },

  async getMediaSource(item, quality) {
    if (quality === 'super' || quality === 'high') throw new Error('没有 ' + quality);
    return {
      url: 'https://cdn.example.com/' + item.id + '/' + quality + '.mp3',
      quality: quality,
      headers: { Referer: 'https://www.example.com/' },
      userAgent: 'FictionalUA/1.0'
    };
  },

  async getLyric(item) {
    return { rawLrc: '[00:01.00]' + item.title, translation: '[00:01.00]译文' };
  },

  // ---- probes for the tests (not part of the protocol) ----
  async libs() {
    const $ = cheerio.load('<ul><li class="a">一</li><li class="a">二</li></ul>');
    return {
      md5: CryptoJS.MD5('bymusic').toString(),
      sha256: CryptoJS.SHA256('abc').toString(),
      aes: CryptoJS.AES.decrypt(
        CryptoJS.AES.encrypt('秘密', CryptoJS.enc.Utf8.parse('0123456789abcdef'), { iv: CryptoJS.enc.Utf8.parse('fedcba9876543210') }).toString(),
        CryptoJS.enc.Utf8.parse('0123456789abcdef'),
        { iv: CryptoJS.enc.Utf8.parse('fedcba9876543210') }
      ).toString(CryptoJS.enc.Utf8),
      date: dayjs(0).add(1, 'day').valueOf(),
      qs: qs.stringify({ a: 1, b: ['x', 'y'] }, { arrayFormat: 'brackets', encode: false }),
      he: he.encode('<a>'),
      cheerio: $('li.a').map(function (i, el) { return $(el).text(); }).get().join(','),
      big: bigInt('123456789012345678901234567890').multiply(2).toString(),
      base64: Buffer.from('音乐').toString('base64'),
      btoa: btoa('abc')
    };
  },

  async post(body) {
    const res = await axios.post('https://api.example.com/echo', body);
    return { status: res.status, data: res.data };
  },

  async sandbox() {
    let fsError = null;
    try { require('fs').readFileSync('/data/data/app/files/x'); } catch (e) { fsError = String(e.message); }
    return {
      std: typeof std,
      os: typeof os,
      process: typeof process.binding,
      fsError: fsError,
      globalRequire: typeof globalThis.require,
      hostHttp: typeof globalThis.__bm_host_http
    };
  },

  async spin() { for (;;) {} },

  async hog() {
    const parts = [];
    for (;;) parts.push(new Array(100000).fill('x'.repeat(16)));
  },

  async wait(ms) {
    await new Promise(function (r) { setTimeout(r, ms); });
    return 'done';
  }
};
