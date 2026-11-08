const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const {execFileSync} = require('node:child_process');
const {tmpdir} = require('node:os');
const {join} = require('node:path');
const html = fs.readFileSync('index.html','utf8');
const match = html.match(/<script>([\s\S]*?)<\/script>/);
assert.ok(match, 'Inline JavaScript niet gevonden');
const tmp = join(tmpdir(), 'mijn-dag-inline-check.js');
fs.writeFileSync(tmp, match[1]);
execFileSync(process.execPath, ['--check',tmp], {stdio:'inherit'});
const js=match[1];
const migration=js.slice(js.indexOf('  // Eenmalige, behoudende overgang'),js.indexOf('  function loadState()'));
assert.ok(migration.includes('function migrateHouseholdUpgradeNov2026'));
const migrationCtx={normalizeHabit:x=>({...x}),normalizeHouseholdTask:x=>({...x})};
vm.createContext(migrationCtx);
vm.runInContext(migration, migrationCtx);
const initial={
  habits:[
    {id:'habit-evening-reset',name:'Avondreset',history:{'2026-11-01':true}},
    {id:'habit-day-close',name:'Dag afsluiten',history:{}},
    {id:'basis-relax',name:'Bewust ontspannen',anchor:'Na de avondreset',history:{}}
  ],
  tasks:[
    {id:'toilet-old',name:'Toilet schoonmaken',frequency:7,history:{'2026-11-05':true}},
    {id:'bath-old',name:'Badkamer – grondige ronde',frequency:28,history:{}},
    {id:'wash-old',name:'Was draaien',frequency:3,history:{}},
    {id:'wash-put',name:'Was opruimen',frequency:3,history:{}},
    {id:'keep',name:'Magnetron schoonmaken',frequency:14,history:{}}
  ]
};
const upgraded=migrationCtx.migrateHouseholdUpgradeNov2026({}, initial.habits,initial.tasks);
assert.equal(upgraded.habits.some(h=>h.name==='Avondreset'),false);
assert.ok(upgraded.habits.find(h=>h.id==='habit-day-close').history['2026-11-01']);
assert.equal(upgraded.householdTasks.find(t=>t.id==='toilet-old').history['2026-11-05'],true);
assert.equal(upgraded.householdTasks.find(t=>t.id==='wash-old').kind,'laundry');
assert.equal(upgraded.householdTasks.find(t=>t.id==='wash-put').retired,true);
assert.ok(upgraded.householdTasks.some(t=>t.name==='Toilet – kleine schoonmaak'));
assert.ok(upgraded.householdTasks.some(t=>t.name==='Badkamer – kleine schoonmaak'));
assert.ok(upgraded.householdTasks.some(t=>t.name==='Meubels vochtig afnemen'));
assert.ok(upgraded.householdTasks.some(t=>t.id==='keep'));
const segment=js.slice(js.indexOf('  function householdLimit()'),js.indexOf('  function renderHousehold()'));
const today='2026-11-08';
const state={
  energy:2,householdTasks:[
    {id:'big1',name:'Toilet – grote schoonmaak',effort:3,durationMinutes:20,frequency:7,history:{}},
    {id:'big2',name:'Badkamer – grote schoonmaak',effort:3,durationMinutes:30,frequency:28,history:{}},
    {id:'small1',name:'Badkamer – kleine schoonmaak',effort:1,durationMinutes:5,frequency:3,history:{}},
    {id:'wash',name:'Was doen',kind:'laundry',manualOnly:true,effort:3,frequency:0,history:{}}
  ],laundryLoads:[],householdTodayChoice:{date:today,taskIds:[]},householdSkipToday:{date:today,taskIds:[]}
};
const ctx={state,Date,Number,Object,Array,Set,Map,
 localDateKey:()=>today,
 dayLoadSuggestion:()=>({capacity:2,energy:2}),
 storedDayTimeline:()=>({blocks:[]}),
 todayAgendaCache:()=>[],
 dayPlanMinutes:()=>null,
 dayPlanBlockMode:()=>'',dayPlanNormalizeText:s=>s,
 escapeHtml:s=>String(s)};
vm.createContext(ctx);
vm.runInContext(segment,ctx);
let result=ctx.householdVisibleToday();
assert.equal(result.filter(ctx.householdLarge).length,1,'Niet meer dan 1 grote suggestie');
assert.ok(result.some(t=>t.id==='small1'),'Kleine taak als aanvulling');
state.laundryLoads.push({id:'wash1',steps:[true,false,false],completedOn:''});
result=ctx.householdVisibleToday();
assert.ok(result.some(t=>t.id==='wash'),'Actieve was is zichtbaar');
assert.equal(result.filter(ctx.householdLarge).length,1,'Was blokkeert andere grote klussen');
assert.ok(result.some(t=>t.id==='small1'),'Kleine klus kan blijven');
state.laundryLoads[0].steps=[true,true,true];
state.laundryLoads[0].completedOn=today;
result=ctx.householdVisibleToday();
assert.ok(result.some(t=>t.id==='wash'),'Afgeronde was staat bij vandaag');
assert.equal(result.filter(ctx.householdLarge).length,1,'Afgeronde was voorkomt andere grote klus');
console.log('JS syntax, bewaarmigratie, dagselectie en wasdag: OK');
