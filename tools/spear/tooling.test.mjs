import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { spawnSync } from 'node:child_process';
import { validate } from './ears.mjs';

const stateScript = fileURLToPath(new URL('./state.mjs', import.meta.url));

test('validator imports from evaluation without CLI arguments', () => {
  const url = new URL('./ears.mjs', import.meta.url).href;
  const result = spawnSync(process.execPath, ['--input-type=module', '-e',
    `const {validate} = await import(${JSON.stringify(url)}); if (!validate('### REQ-001 - Test\\nTHE SYSTEM SHALL work.', 'test.md').ok) throw Error('validation failed');`], {encoding:'utf8'});
  assert.equal(result.status, 0, result.stderr);
});

test('test evidence rejects blank identifiers and invalid status without writes', t => {
  for (const args of [['', 'test', 'red'], ['file', ' ', 'red'], ['file', 'test', 'unknown']]) {
    const previous = '{"version":1,"phase":"prove"}';
    const f = fixture(t, previous);
    const result = spawnSync(process.execPath, [stateScript, 'state_record_test', ...args], {env:f.env,encoding:'utf8'});
    assert.notEqual(result.status, 0);
    assert.equal(fs.readFileSync(f.file, 'utf8'), previous);
  }
});

test('missing clauses are rejected at the next header and EOF', () => {
  const result = validate('### REQ-001 - Missing\n### REQ-002 - Valid\nTHE SYSTEM SHALL work.\n### REQ-003 - Missing\n', 'test.md');
  assert.equal(result.ok, false);
  assert.deepEqual(result.errors.map(error => error.id), ['REQ-001', 'REQ-003']);
});

test('blank responses remain rejected and valid patterns remain accepted', () => {
  for (const prefix of ['THE SYSTEM SHALL', 'WHEN ready THE SYSTEM SHALL',
    'WHILE ready THE SYSTEM SHALL', 'IF broken THEN THE SYSTEM SHALL']) {
    assert.equal(validate(`### REQ-001 - Test\n${prefix}   `, 'test.md').ok, false);
    assert.equal(validate(`### REQ-001 - Test\n${prefix} act.`, 'test.md').ok, true);
  }
});

function fixture(t, content) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'warzoneduels-spear-test-'));
  t.after(() => fs.rmSync(directory, { recursive: true, force: true }));
  const file = path.join(directory, 'state.json');
  fs.writeFileSync(file, content);
  return {directory, file, env: {...process.env, SPEAR_STATE_FILE: file}};
}

test('malformed JSON reports an actionable error without changing the file', t => {
  const f = fixture(t, '{broken');
  const result = spawnSync(process.execPath, [stateScript, 'state_assert_phase', 'idle'], {env: f.env, encoding: 'utf8'});
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /SPEAR state file is not valid JSON/);
  assert.equal(fs.readFileSync(f.file, 'utf8'), '{broken');
});

test('non-object state is rejected clearly', t => {
  for (const content of ['null', '[]', '42', '"idle"', 'true']) {
    const f = fixture(t, content);
    const result = spawnSync(process.execPath, [stateScript, 'state_assert_phase', 'idle'], {env: f.env, encoding: 'utf8'});
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /SPEAR state file must contain a JSON object/);
    assert.equal(fs.readFileSync(f.file, 'utf8'), content);
  }
});

test('failed rename preserves state and removes its temporary file', t => {
  const previous = '{"version":1,"phase":"idle"}';
  const f = fixture(t, previous);
  const code = `import fs from 'node:fs';
    fs.renameSync = () => { throw Error('injected rename failure'); };
    process.argv = [process.execPath, ${JSON.stringify(stateScript)}, 'state_set_phase', 'spec'];
    await import(${JSON.stringify(pathToFileURL(stateScript).href)});`;
  const result = spawnSync(process.execPath, ['--input-type=module', '-e', code], {env: f.env, encoding: 'utf8'});
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /injected rename failure/);
  assert.equal(fs.readFileSync(f.file, 'utf8'), previous);
  assert.deepEqual(fs.readdirSync(f.directory), ['state.json']);
});

test('history append failure warns without misreporting a persisted transition', t => {
  const f = fixture(t, '{"version":1,"phase":"idle"}');
  const code = `import fs from 'node:fs';
    fs.appendFileSync = () => { throw Error('injected history failure'); };
    process.argv = [process.execPath, ${JSON.stringify(stateScript)}, 'state_set_phase', 'spec'];
    await import(${JSON.stringify(pathToFileURL(stateScript).href)});`;
  const result = spawnSync(process.execPath, ['--input-type=module', '-e', code], {env: f.env, encoding: 'utf8'});
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stderr, /SPEAR state saved, but history append failed: injected history failure/);
  assert.equal(JSON.parse(fs.readFileSync(f.file, 'utf8')).phase, 'spec');
  const invalid = spawnSync(process.execPath, [stateScript, 'state_set_phase', 'engine'], {env: f.env, encoding: 'utf8'});
  assert.notEqual(invalid.status, 0);
  assert.equal(JSON.parse(fs.readFileSync(f.file, 'utf8')).phase, 'spec');
});

test('successful transitions rename within the same directory and retain gates', t => {
  const f = fixture(t, '{"version":1,"phase":"idle"}');
  const code = `import fs from 'node:fs'; import path from 'node:path';
    const rename = fs.renameSync; let renamed = false;
    fs.renameSync = (source, target) => {
      if (path.dirname(source) !== path.dirname(target)) throw Error('cross-directory rename');
      if (JSON.parse(fs.readFileSync(target, 'utf8')).phase !== 'idle') throw Error('old state overwritten early');
      rename(source, target); renamed = true;
    };
    process.argv = [process.execPath, ${JSON.stringify(stateScript)}, 'state_set_phase', 'spec'];
    await import(${JSON.stringify(pathToFileURL(stateScript).href)});
    if (!renamed) throw Error('rename was not used');`;
  const result = spawnSync(process.execPath, ['--input-type=module', '-e', code], {env: f.env, encoding: 'utf8'});
  assert.equal(result.status, 0, result.stderr);
  assert.equal(JSON.parse(fs.readFileSync(f.file, 'utf8')).phase, 'spec');
  assert.equal(fs.readFileSync(path.join(f.directory, 'spear-history.jsonl'), 'utf8').trim().split('\n').length, 1);
  const invalid = spawnSync(process.execPath, [stateScript, 'state_set_phase', 'engine'], {env: f.env, encoding: 'utf8'});
  assert.notEqual(invalid.status, 0);
  assert.equal(JSON.parse(fs.readFileSync(f.file, 'utf8')).phase, 'spec');
});
