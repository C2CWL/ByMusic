/*
 * ByMusic plugin runtime prelude (original code, written for ByMusic).
 *
 * Runs once in every plugin's QuickJS sandbox before the plugin itself. It provides a
 * small, sandboxed subset of the environment MusicFree-format plugins commonly expect:
 * CommonJS `module.exports`, `require()` for a fixed list of libraries, `console`,
 * `env`, timers, base64/UTF-8 helpers, URL/URLSearchParams, a Buffer subset and an
 * axios-style HTTP client. Every capability goes through the host functions below;
 * there is no file system, no process and no native module access.
 *
 * Host functions (defined from Kotlin, captured here and then removed from globalThis):
 *   __bm_host_log(level, text)            sync
 *   __bm_host_random(byteCount) -> hex     sync, secure random bytes
 *   __bm_host_lib(name) -> source|null     sync, only whitelisted bundled libraries
 *   __bm_host_http(requestJson) -> json    async (Promise), OkHttp on the host
 *   __bm_host_sleep(ms)                    async (Promise)
 */
(function (g) {
  'use strict';

  var host = {
    log: g.__bm_host_log,
    random: g.__bm_host_random,
    lib: g.__bm_host_lib,
    http: g.__bm_host_http,
    sleep: g.__bm_host_sleep
  };
  ['__bm_host_log', '__bm_host_random', '__bm_host_lib', '__bm_host_http', '__bm_host_sleep']
    .forEach(function (name) {
      try { delete g[name]; } catch (e) { /* not configurable: keep it, it is harmless */ }
    });

  // ---------------------------------------------------------------------------
  // console
  // ---------------------------------------------------------------------------
  function fmt(value) {
    if (typeof value === 'string') return value;
    if (value instanceof Error) return value.name + ': ' + value.message;
    try {
      var s = JSON.stringify(value);
      return s === undefined ? String(value) : s;
    } catch (e) {
      return String(value);
    }
  }
  function logger(level) {
    return function () {
      var parts = [];
      for (var i = 0; i < arguments.length; i++) parts.push(fmt(arguments[i]));
      var text = parts.join(' ');
      if (text.length > 4000) text = text.slice(0, 4000) + '…';
      try { host.log(level, text); } catch (e) { /* ignore */ }
    };
  }
  g.console = {
    log: logger('log'), info: logger('info'), debug: logger('debug'),
    warn: logger('warn'), error: logger('error'), trace: logger('debug')
  };

  // ---------------------------------------------------------------------------
  // UTF-8 / base64 / hex helpers
  // ---------------------------------------------------------------------------
  function utf8Encode(str) {
    str = String(str);
    var out = [];
    for (var i = 0; i < str.length; i++) {
      var c = str.charCodeAt(i);
      if (c >= 0xd800 && c <= 0xdbff && i + 1 < str.length) {
        var d = str.charCodeAt(i + 1);
        if (d >= 0xdc00 && d <= 0xdfff) {
          c = 0x10000 + ((c - 0xd800) << 10) + (d - 0xdc00);
          i++;
        } else {
          c = 0xfffd;
        }
      } else if (c >= 0xd800 && c <= 0xdfff) {
        c = 0xfffd;
      }
      if (c < 0x80) out.push(c);
      else if (c < 0x800) out.push(0xc0 | (c >> 6), 0x80 | (c & 63));
      else if (c < 0x10000) out.push(0xe0 | (c >> 12), 0x80 | ((c >> 6) & 63), 0x80 | (c & 63));
      else out.push(0xf0 | (c >> 18), 0x80 | ((c >> 12) & 63), 0x80 | ((c >> 6) & 63), 0x80 | (c & 63));
    }
    return new Uint8Array(out);
  }
  function utf8Decode(bytes) {
    var out = '';
    var i = 0;
    var n = bytes.length;
    while (i < n) {
      var b = bytes[i++];
      var c;
      if (b < 0x80) c = b;
      else if (b >= 0xc0 && b < 0xe0 && i < n) c = ((b & 31) << 6) | (bytes[i++] & 63);
      else if (b >= 0xe0 && b < 0xf0 && i + 1 < n) {
        c = ((b & 15) << 12) | ((bytes[i] & 63) << 6) | (bytes[i + 1] & 63);
        i += 2;
      } else if (b >= 0xf0 && i + 2 < n) {
        c = ((b & 7) << 18) | ((bytes[i] & 63) << 12) | ((bytes[i + 1] & 63) << 6) | (bytes[i + 2] & 63);
        i += 3;
      } else c = 0xfffd;
      if (c > 0xffff) {
        c -= 0x10000;
        out += String.fromCharCode(0xd800 + (c >> 10), 0xdc00 + (c & 1023));
      } else {
        out += String.fromCharCode(c);
      }
    }
    return out;
  }
  var B64 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
  var B64REV = {};
  for (var bi = 0; bi < B64.length; bi++) B64REV[B64.charAt(bi)] = bi;
  B64REV['-'] = 62;
  B64REV['_'] = 63;
  function bytesToBase64(bytes) {
    var out = '';
    var i;
    for (i = 0; i + 2 < bytes.length; i += 3) {
      var n = (bytes[i] << 16) | (bytes[i + 1] << 8) | bytes[i + 2];
      out += B64[(n >> 18) & 63] + B64[(n >> 12) & 63] + B64[(n >> 6) & 63] + B64[n & 63];
    }
    var rest = bytes.length - i;
    if (rest === 1) {
      var a = bytes[i] << 16;
      out += B64[(a >> 18) & 63] + B64[(a >> 12) & 63] + '==';
    } else if (rest === 2) {
      var b2 = (bytes[i] << 16) | (bytes[i + 1] << 8);
      out += B64[(b2 >> 18) & 63] + B64[(b2 >> 12) & 63] + B64[(b2 >> 6) & 63] + '=';
    }
    return out;
  }
  function base64ToBytes(str) {
    str = String(str).replace(/[\s=]/g, '');
    var out = [];
    var buf = 0;
    var bits = 0;
    for (var i = 0; i < str.length; i++) {
      var v = B64REV[str.charAt(i)];
      if (v === undefined) throw new Error('Invalid base64 string');
      buf = (buf << 6) | v;
      bits += 6;
      if (bits >= 8) {
        bits -= 8;
        out.push((buf >> bits) & 0xff);
      }
    }
    return new Uint8Array(out);
  }
  function bytesToHex(bytes) {
    var s = '';
    for (var i = 0; i < bytes.length; i++) s += (bytes[i] < 16 ? '0' : '') + bytes[i].toString(16);
    return s;
  }
  function hexToBytes(hex) {
    hex = String(hex);
    var out = new Uint8Array(hex.length >> 1);
    for (var i = 0; i < out.length; i++) out[i] = parseInt(hex.substr(i * 2, 2), 16);
    return out;
  }
  function latin1ToBytes(str) {
    str = String(str);
    var out = new Uint8Array(str.length);
    for (var i = 0; i < str.length; i++) out[i] = str.charCodeAt(i) & 0xff;
    return out;
  }
  function bytesToLatin1(bytes) {
    var s = '';
    for (var i = 0; i < bytes.length; i++) s += String.fromCharCode(bytes[i]);
    return s;
  }
  function toBytes(value) {
    if (value instanceof Uint8Array) return value;
    if (value instanceof ArrayBuffer) return new Uint8Array(value);
    if (ArrayBuffer.isView(value)) return new Uint8Array(value.buffer, value.byteOffset, value.byteLength);
    if (Array.isArray(value)) return new Uint8Array(value);
    return null;
  }

  g.btoa = function (s) {
    s = String(s);
    for (var i = 0; i < s.length; i++) {
      if (s.charCodeAt(i) > 255) throw new Error('btoa: character out of range');
    }
    return bytesToBase64(latin1ToBytes(s));
  };
  g.atob = function (s) { return bytesToLatin1(base64ToBytes(s)); };

  function TextEncoder() {}
  TextEncoder.prototype.encoding = 'utf-8';
  TextEncoder.prototype.encode = function (s) { return utf8Encode(s === undefined ? '' : s); };
  function TextDecoder(label) {
    var l = String(label || 'utf-8').toLowerCase();
    if (l !== 'utf-8' && l !== 'utf8') throw new Error('TextDecoder: only utf-8 is supported');
    this.encoding = 'utf-8';
  }
  TextDecoder.prototype.decode = function (input) {
    if (input === undefined) return '';
    var bytes = toBytes(input);
    if (!bytes) throw new Error('TextDecoder: unsupported input');
    return utf8Decode(bytes);
  };
  g.TextEncoder = TextEncoder;
  g.TextDecoder = TextDecoder;

  // ---------------------------------------------------------------------------
  // Buffer (small subset: from/alloc/concat/isBuffer/byteLength, toString)
  // ---------------------------------------------------------------------------
  function normEnc(enc) {
    var e = String(enc || 'utf8').toLowerCase().replace('-', '');
    if (e === 'binary') return 'latin1';
    if (e === 'base64url') return 'base64';
    return e;
  }
  class Buffer extends Uint8Array {
    toString(enc, start, end) {
      var view = this.subarray(start || 0, end === undefined ? this.length : end);
      switch (normEnc(enc)) {
        case 'utf8': return utf8Decode(view);
        case 'base64': return bytesToBase64(view);
        case 'hex': return bytesToHex(view);
        case 'latin1': case 'ascii': return bytesToLatin1(view);
        default: throw new Error('Buffer: unsupported encoding ' + enc);
      }
    }
    toJSON() { return { type: 'Buffer', data: Array.prototype.slice.call(this) }; }
    equals(other) {
      if (!other || other.length !== this.length) return false;
      for (var i = 0; i < this.length; i++) if (this[i] !== other[i]) return false;
      return true;
    }
    static from(value, encOrOffset, length) {
      if (typeof value === 'string') {
        var bytes;
        switch (normEnc(encOrOffset)) {
          case 'utf8': bytes = utf8Encode(value); break;
          case 'base64': bytes = base64ToBytes(value); break;
          case 'hex': bytes = hexToBytes(value); break;
          case 'latin1': case 'ascii': bytes = latin1ToBytes(value); break;
          default: throw new Error('Buffer: unsupported encoding ' + encOrOffset);
        }
        var b = new Buffer(bytes.length);
        b.set(bytes);
        return b;
      }
      if (value instanceof ArrayBuffer) {
        var off = encOrOffset || 0;
        var len = length === undefined ? value.byteLength - off : length;
        var copy = new Buffer(len);
        copy.set(new Uint8Array(value, off, len));
        return copy;
      }
      var arr = toBytes(value) || (value && value.type === 'Buffer' && Array.isArray(value.data) ? new Uint8Array(value.data) : null);
      if (!arr) throw new Error('Buffer.from: unsupported value');
      var out = new Buffer(arr.length);
      out.set(arr);
      return out;
    }
    static alloc(size, fill) {
      var b = new Buffer(size);
      if (fill !== undefined) b.fill(typeof fill === 'number' ? fill : 0);
      return b;
    }
    static isBuffer(v) { return v instanceof Buffer; }
    static byteLength(v, enc) {
      if (typeof v === 'string') return Buffer.from(v, enc).length;
      return v.byteLength;
    }
    static concat(list, total) {
      var size = 0;
      list.forEach(function (x) { size += x.length; });
      if (total !== undefined) size = total;
      var out = new Buffer(size);
      var pos = 0;
      list.forEach(function (x) {
        if (pos < size) {
          out.set(x.subarray(0, Math.min(x.length, size - pos)), pos);
          pos += x.length;
        }
      });
      return out;
    }
  }
  g.Buffer = Buffer;

  // ---------------------------------------------------------------------------
  // secure random for crypto-js (WordArray.random) and plugins
  // ---------------------------------------------------------------------------
  g.crypto = {
    getRandomValues: function (arr) {
      var bytes = hexToBytes(host.random(arr.byteLength));
      new Uint8Array(arr.buffer, arr.byteOffset, arr.byteLength).set(bytes);
      return arr;
    }
  };

  // ---------------------------------------------------------------------------
  // URLSearchParams / URL (subset)
  // ---------------------------------------------------------------------------
  function formEncode(s) {
    return encodeURIComponent(s).replace(/%20/g, '+').replace(/[!'()~]/g, function (c) {
      return '%' + c.charCodeAt(0).toString(16).toUpperCase();
    });
  }
  function formDecode(s) {
    try { return decodeURIComponent(String(s).replace(/\+/g, ' ')); } catch (e) { return String(s); }
  }
  class URLSearchParams {
    constructor(init) {
      this._list = [];
      if (init === undefined || init === null) return;
      if (typeof init === 'string') {
        var s = init.charAt(0) === '?' ? init.slice(1) : init;
        var self = this;
        s.split('&').forEach(function (pair) {
          if (!pair) return;
          var i = pair.indexOf('=');
          var k = i < 0 ? pair : pair.slice(0, i);
          var v = i < 0 ? '' : pair.slice(i + 1);
          self._list.push([formDecode(k), formDecode(v)]);
        });
      } else if (init instanceof URLSearchParams) {
        this._list = init._list.map(function (p) { return [p[0], p[1]]; });
      } else if (Array.isArray(init)) {
        this._list = init.map(function (p) { return [String(p[0]), String(p[1])]; });
      } else if (typeof init === 'object') {
        for (var key in init) {
          if (Object.prototype.hasOwnProperty.call(init, key)) this._list.push([key, String(init[key])]);
        }
      }
    }
    append(k, v) { this._list.push([String(k), String(v)]); }
    set(k, v) {
      k = String(k);
      var idx = -1;
      this._list = this._list.filter(function (p, i) {
        if (p[0] !== k) return true;
        if (idx < 0) { idx = i; return true; }
        return false;
      });
      if (idx < 0) this._list.push([k, String(v)]);
      else this._list[idx][1] = String(v);
    }
    get(k) {
      k = String(k);
      for (var i = 0; i < this._list.length; i++) if (this._list[i][0] === k) return this._list[i][1];
      return null;
    }
    getAll(k) {
      k = String(k);
      return this._list.filter(function (p) { return p[0] === k; }).map(function (p) { return p[1]; });
    }
    has(k) { return this.get(k) !== null; }
    delete(k) { k = String(k); this._list = this._list.filter(function (p) { return p[0] !== k; }); }
    forEach(cb, thisArg) {
      var self = this;
      this._list.forEach(function (p) { cb.call(thisArg, p[1], p[0], self); });
    }
    keys() { return this._list.map(function (p) { return p[0]; })[Symbol.iterator](); }
    values() { return this._list.map(function (p) { return p[1]; })[Symbol.iterator](); }
    entries() { return this._list.map(function (p) { return [p[0], p[1]]; })[Symbol.iterator](); }
    [Symbol.iterator]() { return this.entries(); }
    sort() { this._list.sort(function (a, b) { return a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : 0; }); }
    toString() {
      return this._list.map(function (p) { return formEncode(p[0]) + '=' + formEncode(p[1]); }).join('&');
    }
  }
  g.URLSearchParams = URLSearchParams;

  var URL_RE = /^([a-zA-Z][a-zA-Z0-9+.-]*:)\/\/(?:([^:@\/?#]*)(?::([^@\/?#]*))?@)?(\[[^\]]*\]|[^:\/?#]*)(?::(\d*))?([^?#]*)(\?[^#]*)?(#.*)?$/;
  function normalizePath(path) {
    var segs = path.split('/');
    var out = [];
    for (var i = 0; i < segs.length; i++) {
      var s = segs[i];
      if (s === '..') { if (out.length > 1) out.pop(); }
      else if (s !== '.') out.push(s);
    }
    var res = out.join('/');
    if (segs[segs.length - 1] === '.' || segs[segs.length - 1] === '..') res += '/';
    return res.charAt(0) === '/' ? res : '/' + res;
  }
  class URL {
    constructor(input, base) {
      input = String(input).trim();
      var m = URL_RE.exec(input);
      if (!m && base !== undefined) {
        var b = base instanceof URL ? base : new URL(base);
        var path;
        var search = '';
        var hash = '';
        var rest = input;
        var hi = rest.indexOf('#');
        if (hi >= 0) { hash = rest.slice(hi); rest = rest.slice(0, hi); }
        var qi = rest.indexOf('?');
        if (qi >= 0) { search = rest.slice(qi); rest = rest.slice(0, qi); }
        if (input.slice(0, 2) === '//') {
          m = URL_RE.exec(b.protocol + input);
        } else {
          if (rest === '') {
            path = b.pathname;
            if (qi < 0) search = b.search;
          } else if (rest.charAt(0) === '/') {
            path = rest;
          } else {
            path = b.pathname.replace(/[^\/]*$/, '') + rest;
          }
          m = URL_RE.exec(b.protocol + '//' + b.host + normalizePath(path) + search + hash);
        }
      }
      if (!m) throw new TypeError('Invalid URL: ' + input);
      this.protocol = m[1].toLowerCase();
      this.username = m[2] || '';
      this.password = m[3] || '';
      this.hostname = (m[4] || '').toLowerCase();
      this.port = m[5] || '';
      this.pathname = normalizePath(m[6] || '/');
      this.hash = m[8] && m[8] !== '#' ? m[8] : '';
      this.searchParams = new URLSearchParams(m[7] || '');
      this._search = m[7] && m[7] !== '?' ? m[7] : '';
    }
    get host() { return this.hostname + (this.port ? ':' + this.port : ''); }
    get origin() { return this.protocol + '//' + this.host; }
    get search() {
      var s = this.searchParams.toString();
      if (this._search && new URLSearchParams(this._search).toString() === s) return this._search;
      return s ? '?' + s : '';
    }
    set search(v) {
      v = String(v);
      this._search = v ? (v.charAt(0) === '?' ? v : '?' + v) : '';
      this.searchParams = new URLSearchParams(this._search);
    }
    get href() {
      var auth = this.username ? this.username + (this.password ? ':' + this.password : '') + '@' : '';
      return this.protocol + '//' + auth + this.host + this.pathname + this.search + this.hash;
    }
    toString() { return this.href; }
    toJSON() { return this.href; }
  }
  g.URL = URL;

  // ---------------------------------------------------------------------------
  // timers: driven by host sleep in short slices; stale timers stop after each call
  // ---------------------------------------------------------------------------
  var timerSeq = 0;
  var cleared = {};
  var generation = 0;
  var MAX_DELAY = 30000;
  function sliceSleep(ms, id, gen) {
    var left = Math.max(0, Math.min(Number(ms) || 0, MAX_DELAY));
    function step() {
      if (cleared[id] || gen !== generation) return Promise.resolve(false);
      if (left <= 0) return Promise.resolve(true);
      var s = Math.min(left, 100);
      left -= s;
      return host.sleep(s).then(step);
    }
    return step();
  }
  g.setTimeout = function (fn, ms) {
    var id = ++timerSeq;
    var args = Array.prototype.slice.call(arguments, 2);
    var gen = generation;
    sliceSleep(ms, id, gen).then(function (fire) {
      delete cleared[id];
      if (fire && typeof fn === 'function') fn.apply(null, args);
    });
    return id;
  };
  g.setInterval = function (fn, ms) {
    var id = ++timerSeq;
    var args = Array.prototype.slice.call(arguments, 2);
    var gen = generation;
    var every = Math.max(10, Number(ms) || 0);
    function tick() {
      sliceSleep(every, id, gen).then(function (fire) {
        if (!fire) { delete cleared[id]; return; }
        if (typeof fn === 'function') fn.apply(null, args);
        tick();
      });
    }
    tick();
    return id;
  };
  g.clearTimeout = g.clearInterval = function (id) {
    if (id !== undefined && id !== null) cleared[id] = true;
  };
  g.setImmediate = function (fn) {
    var args = Array.prototype.slice.call(arguments, 1);
    Promise.resolve().then(function () { fn.apply(null, args); });
    return 0;
  };

  g.process = { env: { NODE_ENV: 'production' }, platform: 'android', version: 'v0.0.0', versions: {} };
  g.global = g;
  g.window = undefined;

  // ---------------------------------------------------------------------------
  // axios-style HTTP client (subset of axios 0.27 behaviour), backed by OkHttp
  // ---------------------------------------------------------------------------
  function isPlainObject(v) {
    return v !== null && typeof v === 'object' && Object.prototype.toString.call(v) === '[object Object]';
  }
  function merge() {
    var out = {};
    for (var i = 0; i < arguments.length; i++) {
      var src = arguments[i];
      if (!src) continue;
      for (var k in src) {
        if (!Object.prototype.hasOwnProperty.call(src, k)) continue;
        var v = src[k];
        if (isPlainObject(v) && isPlainObject(out[k])) out[k] = merge(out[k], v);
        else if (isPlainObject(v)) out[k] = merge(v);
        else if (v !== undefined) out[k] = v;
      }
    }
    return out;
  }
  function encodeParam(val) {
    return encodeURIComponent(val)
      .replace(/%3A/gi, ':').replace(/%24/g, '$').replace(/%2C/gi, ',')
      .replace(/%20/g, '+').replace(/%5B/gi, '[').replace(/%5D/gi, ']');
  }
  function serializeParams(params) {
    if (params instanceof URLSearchParams) return params.toString();
    var parts = [];
    Object.keys(params).forEach(function (key) {
      var val = params[key];
      if (val === null || val === undefined) return;
      var k = key;
      var vals = val;
      if (Array.isArray(val)) k = key + '[]';
      else vals = [val];
      vals.forEach(function (v) {
        if (v instanceof Date) v = v.toISOString();
        else if (v !== null && typeof v === 'object') v = JSON.stringify(v);
        parts.push(encodeParam(k) + '=' + encodeParam(v));
      });
    });
    return parts.join('&');
  }
  function buildURL(url, params, serializer) {
    if (!params) return url;
    var q = serializer ? serializer(params) : serializeParams(params);
    if (!q) return url;
    var hi = url.indexOf('#');
    if (hi >= 0) url = url.slice(0, hi);
    return url + (url.indexOf('?') === -1 ? '?' : '&') + q;
  }
  function combineURL(base, rel) {
    if (!base || /^([a-z][a-z\d+\-.]*:)?\/\//i.test(rel)) return rel;
    return rel ? base.replace(/\/+$/, '') + '/' + rel.replace(/^\/+/, '') : base;
  }
  function headerGet(headers, name) {
    var lower = name.toLowerCase();
    for (var k in headers) if (k.toLowerCase() === lower) return headers[k];
    return undefined;
  }
  function headerDelete(headers, name) {
    var lower = name.toLowerCase();
    for (var k in headers) if (k.toLowerCase() === lower) delete headers[k];
  }
  function flattenHeaders(config) {
    var h = config.headers || {};
    var method = config.method;
    var out = merge(h.common || {}, h[method] || {});
    for (var k in h) {
      if (['common', 'get', 'post', 'put', 'patch', 'delete', 'head', 'options'].indexOf(k) >= 0) continue;
      var v = h[k];
      if (v !== undefined && v !== null && typeof v !== 'object') out[k] = String(v);
    }
    return out;
  }
  function AxiosError(message, code, config, request, response) {
    var e = new Error(message);
    e.name = 'AxiosError';
    e.code = code;
    e.config = config;
    e.request = request;
    e.response = response;
    e.isAxiosError = true;
    e.toJSON = function () { return { message: e.message, code: e.code, status: response && response.status }; };
    return e;
  }

  function dispatch(config) {
    var method = String(config.method || 'get').toLowerCase();
    config.method = method;
    var url = buildURL(combineURL(config.baseURL, String(config.url || '')), config.params, config.paramsSerializer);
    var headers = flattenHeaders(config);
    var data = config.data;
    var body = null;
    var bodyBase64 = null;
    if (data !== undefined && data !== null && method !== 'get' && method !== 'head') {
      var bytes = toBytes(data);
      if (bytes) {
        bodyBase64 = bytesToBase64(bytes);
      } else if (data instanceof URLSearchParams) {
        if (!headerGet(headers, 'Content-Type')) headers['Content-Type'] = 'application/x-www-form-urlencoded;charset=utf-8';
        body = data.toString();
      } else if (typeof data === 'object') {
        // axios 0.27: object payloads are sent as JSON; Content-Type defaults to JSON
        // unless the caller set one explicitly.
        var explicit = false;
        var top = config.headers || {};
        for (var hk in top) {
          if (hk.toLowerCase() === 'content-type' && typeof top[hk] !== 'object') explicit = true;
        }
        if (!explicit) {
          headerDelete(headers, 'Content-Type');
          headers['Content-Type'] = 'application/json';
        }
        body = JSON.stringify(data);
      } else {
        body = String(data);
      }
    }
    if (config.auth) {
      headers.Authorization = 'Basic ' + g.btoa(unescape(encodeURIComponent((config.auth.username || '') + ':' + (config.auth.password || ''))));
    }
    var responseType = String(config.responseType || 'json').toLowerCase();
    var request = {
      method: method.toUpperCase(),
      url: url,
      headers: headers,
      body: body,
      bodyBase64: bodyBase64,
      timeoutMs: Number(config.timeout) || 0,
      followRedirects: config.maxRedirects !== 0,
      binary: responseType === 'arraybuffer' || responseType === 'blob'
    };
    var requestInfo = { url: url, method: request.method };
    return host.http(JSON.stringify(request)).then(function (raw) {
      var res = JSON.parse(raw);
      if (res.error) {
        throw AxiosError(res.error, res.code || 'ERR_NETWORK', config, requestInfo);
      }
      var payload;
      if (request.binary) {
        var bin = base64ToBytes(res.bodyBase64 || '');
        payload = bin.buffer.slice(bin.byteOffset, bin.byteOffset + bin.byteLength);
      } else {
        payload = res.body === undefined || res.body === null ? '' : res.body;
        if (responseType === 'json' && typeof payload === 'string' && payload.length) {
          try { payload = JSON.parse(payload); } catch (e) { /* axios keeps the text */ }
        }
      }
      var response = {
        data: payload,
        status: res.status,
        statusText: res.statusText || '',
        headers: res.headers || {},
        config: config,
        request: { responseURL: res.url || url }
      };
      if (typeof config.transformResponse === 'function') {
        response.data = config.transformResponse(response.data, response.headers);
      } else if (Array.isArray(config.transformResponse)) {
        config.transformResponse.forEach(function (fn) { response.data = fn(response.data, response.headers); });
      }
      var validate = config.validateStatus === undefined
        ? function (s) { return s >= 200 && s < 300; }
        : config.validateStatus;
      if (!validate || validate(response.status)) return response;
      throw AxiosError('Request failed with status code ' + response.status,
        response.status >= 500 ? 'ERR_BAD_RESPONSE' : 'ERR_BAD_REQUEST', config, requestInfo, response);
    });
  }

  function InterceptorManager() { this.handlers = []; }
  InterceptorManager.prototype.use = function (fulfilled, rejected) {
    this.handlers.push({ fulfilled: fulfilled, rejected: rejected });
    return this.handlers.length - 1;
  };
  InterceptorManager.prototype.eject = function (id) { this.handlers[id] = null; };
  InterceptorManager.prototype.clear = function () { this.handlers = []; };

  function defaultConfig() {
    return {
      timeout: 0,
      headers: {
        common: { Accept: 'application/json, text/plain, */*' },
        post: { 'Content-Type': 'application/x-www-form-urlencoded' },
        put: { 'Content-Type': 'application/x-www-form-urlencoded' },
        patch: { 'Content-Type': 'application/x-www-form-urlencoded' }
      }
    };
  }

  function createInstance(defaults) {
    var ctx = {
      defaults: defaults,
      interceptors: { request: new InterceptorManager(), response: new InterceptorManager() }
    };
    function request(configOrUrl, maybeConfig) {
      var cfg = typeof configOrUrl === 'string'
        ? merge(maybeConfig || {}, { url: configOrUrl })
        : (configOrUrl || {});
      var config = merge(ctx.defaults, cfg);
      if (cfg.data !== undefined) config.data = cfg.data;
      if (cfg.params !== undefined) config.params = cfg.params;
      var chain = [dispatch, undefined];
      ctx.interceptors.request.handlers.forEach(function (h) { if (h) chain.unshift(h.fulfilled, h.rejected); });
      ctx.interceptors.response.handlers.forEach(function (h) { if (h) chain.push(h.fulfilled, h.rejected); });
      var p = Promise.resolve(config);
      while (chain.length) p = p.then(chain.shift(), chain.shift());
      return p;
    }
    var inst = function (a, b) { return request(a, b); };
    inst.request = request;
    inst.defaults = ctx.defaults;
    inst.interceptors = ctx.interceptors;
    ['get', 'delete', 'head', 'options'].forEach(function (m) {
      inst[m] = function (url, config) { return request(merge(config || {}, { method: m, url: url })); };
    });
    ['post', 'put', 'patch'].forEach(function (m) {
      inst[m] = function (url, data, config) {
        var c = merge(config || {}, { method: m, url: url });
        c.data = data;
        return request(c);
      };
    });
    inst.getUri = function (config) {
      var c = merge(ctx.defaults, config || {});
      return buildURL(combineURL(c.baseURL, String(c.url || '')), c.params, c.paramsSerializer);
    };
    return inst;
  }

  var axios = createInstance(defaultConfig());
  axios.create = function (cfg) { return createInstance(merge(defaultConfig(), axios.defaults, cfg || {})); };
  axios.all = function (list) { return Promise.all(list); };
  axios.spread = function (cb) { return function (arr) { return cb.apply(null, arr); }; };
  axios.isAxiosError = function (e) { return !!(e && e.isAxiosError); };
  axios.isCancel = function () { return false; };
  axios.AxiosError = AxiosError;
  axios.default = axios;

  // ---------------------------------------------------------------------------
  // require(): a fixed whitelist. Unknown modules return a stub that throws when used,
  // so a plugin still loads and the missing module is reported to the user.
  // ---------------------------------------------------------------------------
  var BUNDLED = ['crypto-js', 'dayjs', 'qs', 'he', 'big-integer', 'cheerio'];
  var cache = { axios: axios, buffer: { Buffer: Buffer }, url: { URL: URL, URLSearchParams: URLSearchParams } };
  var required = {};
  var missing = {};
  function missingStub(name) {
    var fail = function () { throw new Error('ByMusic 不支持模块 "' + name + '"'); };
    return new Proxy(fail, {
      get: function (t, prop) {
        if (prop === '__esModule' || prop === 'then' || typeof prop === 'symbol') return undefined;
        return fail();
      },
      apply: fail,
      construct: fail
    });
  }
  function bmRequire(name) {
    name = String(name);
    required[name] = true;
    if (Object.prototype.hasOwnProperty.call(cache, name)) return cache[name];
    if (BUNDLED.indexOf(name) >= 0) {
      var src = host.lib(name);
      if (src) {
        var lib = (0, eval)('(function(){' + src + '\n;return __bm_lib;})()');
        cache[name] = lib;
        return lib;
      }
    }
    missing[name] = true;
    return missingStub(name);
  }

  // ---------------------------------------------------------------------------
  // plugin lifecycle, called from Kotlin
  // ---------------------------------------------------------------------------
  var state = { instance: null, userVariables: {} };

  g.env = {
    getUserVariables: function () { return Object.assign({}, state.userVariables); },
    appVersion: '0.0.0',
    os: 'android',
    lang: 'zh-CN'
  };

  function str(v) { return typeof v === 'string' ? v : (v === undefined || v === null ? null : String(v)); }

  g.__bm_init = function (envJson) {
    var cfg = JSON.parse(envJson || '{}');
    state.userVariables = cfg.userVariables || {};
    if (cfg.appVersion) g.env.appVersion = String(cfg.appVersion);
    var factory = g.__bm_plugin_factory;
    delete g.__bm_plugin_factory;
    if (typeof factory !== 'function') throw new Error('插件代码没有加载');
    var module = { exports: {} };
    factory.call(module.exports, module, module.exports, bmRequire, g.env);
    var inst = module.exports;
    if (inst && inst.default && typeof inst.default === 'object' && !inst.platform) inst = inst.default;
    if (!inst || typeof inst !== 'object') throw new Error('插件没有导出对象（module.exports）');
    state.instance = inst;
    generation++;
    var methods = [];
    for (var k in inst) if (typeof inst[k] === 'function') methods.push(k);
    var vars = Array.isArray(inst.userVariables) ? inst.userVariables.filter(function (v) {
      return v && typeof v.key === 'string';
    }).map(function (v) {
      return { key: v.key, name: str(v.name || v.title) , hint: str(v.hint) };
    }) : [];
    return JSON.stringify({
      platform: str(inst.platform),
      version: str(inst.version),
      author: str(inst.author),
      srcUrl: str(inst.srcUrl),
      description: str(inst.description),
      appVersion: str(inst.appVersion),
      cacheControl: str(inst.cacheControl),
      primaryKey: Array.isArray(inst.primaryKey) ? inst.primaryKey.map(String) : null,
      supportedSearchType: Array.isArray(inst.supportedSearchType) ? inst.supportedSearchType.map(String) : null,
      userVariables: vars,
      methods: methods,
      requiredModules: Object.keys(required),
      missingModules: Object.keys(missing)
    });
  };

  g.__bm_set_user_variables = function (json) {
    state.userVariables = JSON.parse(json || '{}');
  };

  g.__bm_missing_modules = function () { return JSON.stringify(Object.keys(missing)); };

  g.__bm_call = function (method, argsJson) {
    var inst = state.instance;
    if (!inst) return Promise.reject(new Error('插件没有初始化'));
    var fn = inst[method];
    if (typeof fn !== 'function') return Promise.reject(new Error('BM_METHOD_MISSING:' + method));
    var args = JSON.parse(argsJson || '[]');
    return Promise.resolve()
      .then(function () { return fn.apply(inst, args); })
      .then(function (result) {
        generation++;
        return JSON.stringify(result === undefined ? null : result);
      }, function (err) {
        generation++;
        throw err;
      });
  };
})(globalThis);
