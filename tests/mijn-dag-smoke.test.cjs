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
    dayPlanBlockMode:b=>b.fixed===true||b.linkType==='agenda'?'fixed':'flex',
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


test('aflopende gewone Focus zet alleen de afrondkeuze klaar, zonder taak af te vinken', () => {
  const from=main.indexOf('  function focusCountdownSync(){');
  const to=main.indexOf('  function restoreNativeFocusNotification(){',from);
  assert.ok(from>0&&to>from,'Bestaande Focus-aftelling gevonden');
  const task={id:'task1',done:false,elapsed:0};
  const focus={running:true,endsAt:10_000,remainingSec:1,taskId:'task1',finishPending:false};
  const state={focus,tasks:[task]};
  let saves=0;
  const context={
    state,Date:{now:()=>11_000},Math,Number,
    document:{getElementById:()=>null,visibilityState:'hidden'},
    window:{AndroidWidgetBridge:{}},
    saveState:()=>saves++,formatTime:sec=>sec,showToast:()=>{throw Error('Geen toast bij scherm uit')},
    playFocusFinishedSignal:()=>{throw Error('Native Focus moet het geluid regelen')}
  };
  vm.runInNewContext(main.slice(from,to)+'\nthis.run=focusCountdownSync;',context);
  context.run();
  assert.equal(focus.running,false);
  assert.equal(focus.finishPending,true);
  assert.equal(task.done,false);
  assert.equal(task.elapsed,1);
  assert.ok(saves>0);
});
test('aflopende dagplanning-Focus markeert de activiteit niet vanzelf als klaar', () => {
  const from=main.indexOf('  function tickDayPlanFocus(){');
  const to=main.indexOf('\n  function ',from+12);
  assert.ok(from>0&&to>from,'Bestaande dagplanning-timer gevonden');
  const block={id:'plan1',done:false};
  const focus={expiryShown:false};
  let rendered=0;
  const context={
    activeDayPlanFocus:()=>({block,focus}),
    dayPlanFocusRemaining:()=>0,
    dayPlanFocusClock:()=> '00:00',
    document:{querySelectorAll:()=>[]},
    renderFocus:()=>rendered++,
    refreshDayPlanFocusPanels:()=>rendered++,
    window:{AndroidWidgetBridge:{}},
    playFocusFinishedSignal:()=>{throw Error('Geen HTML-audiosignaal op Android')}
  };
  vm.runInNewContext(main.slice(from,to)+'\nthis.run=tickDayPlanFocus;',context);
  context.run();
  assert.equal(focus.expiryShown,true);
  assert.equal(block.done,false);
  assert.equal(rendered,2);
});


test('begintijd handmatig wijzigen: alleen flexibel blok en behoud van duur', () => {
  const a=main.indexOf('  function moveDayPlanBlockIntoGap(');
  const b=main.indexOf('  function saveDayPlanDragOrder()',a);
  assert.ok(a>0&&b>a);
  const blocks=[
    {id:'a',start:'08:00',end:'08:30',fixed:false,text:'A'},
    {id:'b',start:'10:00',end:'11:00',fixed:true,text:'Afspraak'},
    {id:'c',start:'12:00',end:'12:30',fixed:false,text:'C'}
  ];
  const plan={date:'2026-10-17',blocks,manualOrderIds:[]};
  const errors=[];
  const env={
    state:{dayTimeline:plan,focus:{running:false}},
    dayPlanIsCurrent:()=>true,dayPlanBlockDone:()=>false,activeDayPlanFocus:()=>false,
    dayPlanBlockMode:x=>x.fixed?'fixed':'flex',
    dayPlanMinutes:time=>{const [h,m]=time.split(':').map(Number);return h*60+m;},
    dayPlanClock:n=>String(Math.floor(n/60)).padStart(2,'0')+':'+String(n%60).padStart(2,'0'),
    dayPlanAlarmId:()=> 'reminder',
    window:{AndroidWidgetBridge:{cancelReminder:()=>{}}},
    scheduleDayPlanAlarms:()=>{},saveState:()=>{},renderToday:()=>{},
    showToast:m=>errors.push(m),
    Math,Number
  };
  vm.runInNewContext(main.slice(a,b)+'\nthis.setStart=setDayPlanBlockStartTime;',env);
  assert.equal(env.setStart('a','09:00'),true);
  assert.equal(blocks.find(x=>x.id==='a').start,'09:00');
  assert.equal(blocks.find(x=>x.id==='a').end,'09:30');
  assert.equal(blocks.find(x=>x.id==='b').start,'10:00');
  assert.equal(env.setStart('b','09:00'),false,'Vast blok blijft staan');
  assert.equal(env.setStart('a','10:30'),false,'Geen overlap met vaste afspraak');
  assert.equal(env.setStart('a','ongeldig'),false);
  assert.equal(blocks.find(x=>x.id==='a').start,'09:00');
});


test('Focus en andere extra panelen verbergen het onderdelenmenu', () => {
  const from = main.indexOf('  function setDailyTab(tab){');
  const to = main.indexOf('  function energyLimit(){', from);
  assert.ok(from > 0 && to > from, 'Tabwissel gevonden');
  const shell = { dataset: { mydayMode: 'onderdelen' } };
  const tools = { hidden: false };
  const back = { hidden: true, textContent: '' };
  const subtitle = { textContent: '' };
  const elements = { myDayTools: tools, myDayPanelBack: back, todaySubTitle: subtitle };
  const context = {
    document: {
      querySelector: selector => selector === '.today-shell' ? shell : null,
      querySelectorAll: () => [],
      getElementById: id => elements[id] || null
    },
    window: { scrollTo: () => {} },
    renderFocus: () => {},
    renderShutdown: () => {},
    renderDump: () => {},
    renderAllTasks: () => {}
  };
  vm.runInNewContext(main.slice(from, to) + '\nthis.setTab = setDailyTab;', context);
  context.setTab('focus');
  assert.equal(shell.dataset.mydayPanel, 'focus');
  assert.equal(tools.hidden, true);
  assert.equal(back.hidden, false);
  context.setTab('start');
  assert.equal(shell.dataset.mydayPanel, 'start');
  assert.equal(back.hidden, true);
  assert.match(html, /\.today-shell\[data-myday-panel\]:not\(\[data-myday-panel="start"\]\) \.myday-part-menu/);
});

test('agenda heeft geen wijzigbare Vast/Flexibel-knop', () => {
  assert.match(main, /b\.linkType==='agenda'\?'':'<button type="button" class="day-plan-mini/);
  assert.match(main, /if\(b&&b\.linkType!=='agenda'\)\{b\.fixed=/);
});

test('agenda blijft vast, ook met oude fixed:false gegevens', () => {
  const start=main.indexOf('  function dayPlanBlockMode(block){');
  const end=main.indexOf('  function dayPlanEveningStage(',start);
  assert.ok(start>0&&end>start);
  const ctx={dayPlanMealWindow:()=>null};
  vm.runInNewContext(main.slice(start,end)+'\nthis.mode=dayPlanBlockMode;',ctx);
  assert.equal(ctx.mode({linkType:'agenda',fixed:false}),'fixed');
  assert.equal(ctx.mode({linkType:'agenda'}),'fixed');
  assert.equal(ctx.mode({linkType:'task',fixed:false}),'flex');
  assert.equal(ctx.mode({linkType:'task',fixed:true}),'fixed');
});
