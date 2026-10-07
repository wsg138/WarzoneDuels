// Project-local Windows adaptation of SPEAR's MIT-licensed hooks/lib/state.sh.
import fs from 'node:fs';
import path from 'node:path';
import { randomUUID } from 'node:crypto';
const file = process.env.SPEAR_STATE_FILE || '.claude/spear-state.json';
function load() {
  if (!fs.existsSync(file)) return {version:1,phase:'idle'};
  const content = fs.readFileSync(file, 'utf8');
  let parsed;
  try {
    parsed = JSON.parse(content);
  } catch (cause) {
    throw new Error('SPEAR state file is not valid JSON: ' + file, {cause});
  }
  if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error('SPEAR state file must contain a JSON object: ' + file);
  }
  return parsed;
}
const state = load();
const [fn,...args] = process.argv.slice(2);
const next = {'idle':['spec'],'spec':['spec-done'],'spec-done':['prove','arch'],'prove':['prove-done'],'prove-done':['engine'],'engine':['engine-done'],'engine-done':['arch'],'arch':['arch-done'],'arch-done':['refine'],'refine':['idle']};
function save(s) {
  fs.mkdirSync(path.dirname(file),{recursive:true});
  s.lastUpdated = new Date().toISOString();
  const temporary = file + '.' + randomUUID() + '.tmp';
  try {
    fs.writeFileSync(temporary,JSON.stringify(s,null,2)+'\n', {flag:'wx'});
    fs.renameSync(temporary,file);
  } finally {
    fs.rmSync(temporary, {force:true});
  }
  try {
    fs.appendFileSync(path.join(path.dirname(file),'spear-history.jsonl'),JSON.stringify(s)+'\n');
  } catch (error) {
    console.error('SPEAR state saved, but history append failed: ' + error.message);
  }
}
switch(fn) {
case 'state_assert_phase':
  if(state.phase!==args[0]) throw Error('spear requires phase='+args[0]+'; current phase='+state.phase);
  break;
case 'state_set_phase':
  if(!(next[state.phase]||[]).includes(args[0])) throw Error('Invalid SPEAR transition: '+state.phase+' -> '+args[0]);
  if(args[0]==='prove-done' && state.testStatus!=='red') throw Error('A recorded red test is required');
  if(args[0]==='engine-done' && state.testStatus!=='green') throw Error('A recorded green test is required');
  save({...state,phase:args[0]}); break;
case 'state_record_test':
  if(!['prove','engine'].includes(state.phase)) throw Error('Tests can only be recorded in prove/engine');
  if(!args[0]?.trim() || !args[1]?.trim() || !['red','green'].includes(args[2])) {
    throw Error('Test evidence requires nonempty file/name and red or green status');
  }
  save({...state,testFile:args[0],testName:args[1],testStatus:args[2]}); break;
case 'state_task':
  if(state.phase!=='idle') throw Error('Select a task only while idle');
  save({...state,currentTaskId:args[0],reqId:args[1]}); break;
case 'state_clear':
  if(state.phase!=='refine') throw Error('Clear only after refine');
  save({version:1,phase:'idle'}); break;
default: throw Error('Unknown state operation: '+fn);
}
