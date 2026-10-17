const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const html = fs.readFileSync('index.html', 'utf8');

function embeddedAppCode() {
  const scripts = [...html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g)];
  const main = scripts.map(x => x[1]).sort((a, b) => b.length - a.length)[0];
  assert.ok(main && main.length > 100000, 'Bestaande appscript gevonden');
  return main;
}
const main = embeddedAppCode();

test('bestaande JavaScript bevat geen syntaxisfouten', () => {
  assert.doesNotThrow(() => new Function(main));
});
test('nieuw beginscherm heeft dagplanning, onderdelen en Focus', () => {
  assert.match(html, /class="view active" data-view="today"/);
  for (const target of ['planning','onderdelen'])
    assert.ok(html.includes('data-myday-tab="' + target + '"'));
  for (const target of ['routines','taken','huishouden','agenda'])
    assert.ok(html.includes('data-myday-part="' + target + '"'));
  assert.match(html, /data-myday-focus/);
  assert.match(main, /setDailyTab\(mode==='taken'\?'tasks':'start'\)/);
});
test('herinnerings- en Focus-functies blijven bestaan', () => {
  for (const name of ['renderFocus','scheduleDayPlanAlarms',
    'restoreNativeFocusNotification','scheduleNativeFocusGuideAlarm']) {
    assert.ok(main.includes('function ' + name + '('), name);
  }
});
test('widget afvinken triggert niet opnieuw herplannen', () => {
  const start = main.indexOf('window.applyNativeDayTimelineChanges=function(raw)');
  const end = main.indexOf('function renderTodayAgenda()', start);
  assert.ok(start > 0 && end > start);
  assert.doesNotMatch(main.slice(start, end), /replanDayFromNow\(/);
});
function makeRunner() {
  const start = main.indexOf('  function moveDayPlanBlockIntoGap(');
  const end = main.indexOf('  function saveDayPlanDragOrder()', start);
  assert.ok(start > 0 && end > start);
  const blocks = [
    {id:'task',linkType:'task',start:'08:00',end:'08:30',text:'Oefenen',remind:true},
    {id:'agenda',linkType:'agenda',start:'10:00',end:'11:00',text:'Afspraak'},
    {id:'second',linkType:'task',start:'11:30',end:'12:00',text:'Huishouden',remind:true}
  ];
  const plan={date:'2026-10-17',blocks,manualOrderIds:[]};
  const state={dayTimeline:plan,focus:{running:false}};
  const messages=[];const cancelled=[];
  let saved=0,scheduled=0,rendered=0;
  const ctx={
    state, window:{AndroidWidgetBridge:{cancelReminder:id=>cancelled.push(id)}},
    activeDayPlanFocus:()=>false,
    dayPlanIsCurrent:()=>true,
    dayPlanBlockDone:b=>Boolean(b.done),
    dayPlanMinutes:value=>{const m=/^(\d+):(\d{2})$/.exec(value||'');return m?Number(m[1])*60+Number(m[2]):null;},
    dayPlanClock:n=>String(Math.floor(n/60)).padStart(2,'0')+':'+String(n%60).padStart(2,'0'),
    dayPlanAlarmId:(_p,b)=>'alarm-'+b.id,
    saveState:()=>saved++,
    scheduleDayPlanAlarms:()=>scheduled++,
    renderToday:()=>rendered++,
    showToast:msg=>messages.push(msg)
  };
  vm.runInNewContext(main.slice(start,end)+'\nthis.move=moveDayPlanBlockIntoGap;',ctx);
  return {move:ctx.move,blocks,state,plan,messages,cancelled,get counts(){return [saved,scheduled,rendered];}};
}
test('slepen in vrije ruimte verplaatst alleen gekozen activiteit', () => {
  const x=makeRunner(),before=x.blocks.slice(1).map(b=>[b.id,b.start,b.end]);
  assert.equal(x.move('task',540,600),true);
  assert.equal(x.blocks.find(b=>b.id==='task').start,'09:00');
  assert.equal(x.blocks.find(b=>b.id==='task').end,'09:30');
  assert.deepEqual(x.blocks.filter(b=>b.id!=='task').map(b=>[b.id,b.start,b.end]),before);
  assert.deepEqual(x.cancelled,['alarm-task']);
  assert.deepEqual(x.counts,[1,1,1]);
});
test('agenda niet verplaatsen en afspraak niet overlappen', () => {
  const x=makeRunner();
  assert.equal(x.move('agenda',480,600),false);
  assert.equal(x.move('task',610,660),false);
  assert.equal(x.blocks.find(b=>b.id==='agenda').start,'10:00');
  assert.deepEqual(x.counts,[0,0,0]);
});
test('geen slepen als Focus loopt of activiteit niet past', () => {
  const x=makeRunner();
  x.state.focus.running=true;
  assert.equal(x.move('task',540,600),false);
  x.state.focus.running=false;
  assert.equal(x.move('task',580,600),false);
  assert.equal(x.blocks[0].start,'08:00');
});
