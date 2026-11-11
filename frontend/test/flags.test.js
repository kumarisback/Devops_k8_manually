import assert from 'node:assert/strict';
import test from 'node:test';

import { flagConfig, loadFlagConfig, RUNTIME_CONFIG_PATH } from '../src/lib/flags.js';

test('a runtime config naming Unleash becomes the FlagProvider settings', () => {
  assert.deepEqual(flagConfig({ unleash: { url: '/flags/api/frontend', clientKey: 'default:development.abc' } }), {
    url: '/flags/api/frontend',
    clientKey: 'default:development.abc',
    appName: 'frontend',
    refreshInterval: 30,
  });
});

test('no flag service: an empty, partial or odd config gives null', () => {
  assert.equal(flagConfig(undefined), null);
  assert.equal(flagConfig({}), null);
  assert.equal(flagConfig({ unleash: { url: '/flags/api/frontend' } }), null);
  assert.equal(flagConfig({ unleash: { url: '', clientKey: 'x' } }), null);
  assert.equal(flagConfig({ unleash: { url: 42, clientKey: 'x' } }), null);
});

test('loads the config from the runtime file, uncached', async () => {
  const calls = [];
  const fetchImpl = async (path, options) => {
    calls.push({ path, options });
    return { ok: true, json: async () => ({ unleash: { url: '/flags/api/frontend', clientKey: 'k' } }) };
  };
  const config = await loadFlagConfig(fetchImpl);
  assert.equal(config.clientKey, 'k');
  assert.deepEqual(calls, [{ path: RUNTIME_CONFIG_PATH, options: { cache: 'no-store' } }]);
});

test('a missing file, a bad body or a network error means no flags', async () => {
  assert.equal(await loadFlagConfig(async () => ({ ok: false })), null);
  assert.equal(await loadFlagConfig(async () => ({ ok: true, json: async () => { throw new SyntaxError('bad'); } })), null);
  assert.equal(await loadFlagConfig(async () => { throw new TypeError('offline'); }), null);
});
