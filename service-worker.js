const CACHE_NAME='mijn-persoonlijke-app-v1-7-14-notes-v2';
const APP_SHELL=['./','./index.html','./manifest.webmanifest','./icon-192.png','./icon-512.png','./apple-touch-icon.png','./calendar.js','./recipes-extra-1.js','./recipes-extra-2.js','./recipes-extra-3.js','./recipes-extra-4.js','./notes-v2.js'];

self.addEventListener('install',event=>{
  event.waitUntil(caches.open(CACHE_NAME).then(cache=>cache.addAll(APP_SHELL)));
  self.skipWaiting();
});

self.addEventListener('activate',event=>{
  event.waitUntil((async()=>{
    const keys=await caches.keys();
    await Promise.all(keys.filter(key=>key!==CACHE_NAME).map(key=>caches.delete(key)));
    await self.clients.claim();
    const clients=await self.clients.matchAll({type:'window',includeUncontrolled:true});
    await Promise.all(clients.map(client=>client.navigate(client.url).catch(()=>{})));
  })());
});

function applyDailyFixes(html){
  html=html.replace('<!-- Mijn persoonlijke app v1.7.1 -->','<!-- Mijn persoonlijke app v1.7.13 -->');

  html=html.replace(
    'focus: { taskId: null, targetType: null, targetId: null, durationSec: 300, remainingSec: 300, running: false, soundEnabled: true },',
    'focus: { taskId: null, targetType: null, targetId: null, durationSec: 300, remainingSec: 300, running: false, endAt: null, soundEnabled: true },'
  );

  html=html.replace(
    "focus: {...structuredClone(defaultState.focus), ...(saved.focus||{}), targetType:saved.focus?.targetType||(saved.focus?.taskId?'task':null), targetId:saved.focus?.targetId||saved.focus?.taskId||null, running:false},",
    "focus: {...structuredClone(defaultState.focus), ...(saved.focus||{}), targetType:saved.focus?.targetType||(saved.focus?.taskId?'task':null), targetId:saved.focus?.targetId||saved.focus?.taskId||null, running:Boolean(saved.focus?.running&&Number(saved.focus?.endAt)>Date.now()), endAt:Number(saved.focus?.endAt)||null, remainingSec:(saved.focus?.running&&Number(saved.focus?.endAt)>Date.now())?Math.max(0,Math.ceil((Number(saved.focus.endAt)-Date.now())/1000)):Number(saved.focus?.remainingSec||saved.focus?.durationSec||300)},"
  );

  html=html.replace(
    "function saveState(){ try { const s=structuredClone(state); s.tasks=s.tasks.map(t=>({...t,running:false})); s.focus.running=false; localStorage.setItem(STORAGE_KEY, JSON.stringify(s)); } catch {} }",
    "function saveState(){ try { const s=structuredClone(state); s.tasks=s.tasks.map(t=>({...t,running:false})); if(!s.focus.running)s.focus.endAt=null; localStorage.setItem(STORAGE_KEY, JSON.stringify(s)); } catch {} }"
  );

  html=html.replace(
    "function selectFocusItem(type,id){ state.focus.targetType=type;state.focus.targetId=id;state.focus.taskId=type==='task'?id:null;state.focus.remainingSec=state.focus.durationSec||300;state.focus.running=false;saveState();setDailyTab('focus'); }",
    "function selectFocusItem(type,id){ state.focus.targetType=type;state.focus.targetId=id;state.focus.taskId=type==='task'?id:null;state.focus.remainingSec=state.focus.durationSec||300;state.focus.running=false;state.focus.endAt=null;saveState();setDailyTab('focus'); }"
  );

  html=html.replace(
    "const dur=e.target.closest('[data-focus-duration]');if(dur){state.focus.durationSec=Number(dur.dataset.focusDuration)*60;state.focus.remainingSec=state.focus.durationSec;state.focus.running=false;saveState();return renderFocus();}",
    "const dur=e.target.closest('[data-focus-duration]');if(dur){state.focus.durationSec=Number(dur.dataset.focusDuration)*60;state.focus.remainingSec=state.focus.durationSec;state.focus.running=false;state.focus.endAt=null;saveState();return renderFocus();}"
  );

  html=html.replace(
    "if(e.target.closest('#customFocusButton')){const m=Math.max(1,Math.min(180,Number(document.getElementById('customFocusMinutes').value)||0));if(!m)return showToast('Vul een aantal minuten in.');state.focus.durationSec=m*60;state.focus.remainingSec=m*60;state.focus.running=false;saveState();return renderFocus();}",
    "if(e.target.closest('#customFocusButton')){const m=Math.max(1,Math.min(180,Number(document.getElementById('customFocusMinutes').value)||0));if(!m)return showToast('Vul een aantal minuten in.');state.focus.durationSec=m*60;state.focus.remainingSec=m*60;state.focus.running=false;state.focus.endAt=null;saveState();return renderFocus();}"
  );

  html=html.replace(
    "if(e.target.closest('#focusStartButton')){if(!state.focus.running)ensureFocusAudio();state.focus.running=!state.focus.running;if(state.focus.remainingSec<=0)state.focus.remainingSec=state.focus.durationSec||300;saveState();return renderFocus();}",
    "if(e.target.closest('#focusStartButton')){if(!state.focus.running){ensureFocusAudio();if(state.focus.remainingSec<=0)state.focus.remainingSec=state.focus.durationSec||300;state.focus.endAt=Date.now()+state.focus.remainingSec*1000;state.focus.running=true;}else{state.focus.remainingSec=Math.max(0,Math.ceil((Number(state.focus.endAt||Date.now())-Date.now())/1000));state.focus.running=false;state.focus.endAt=null;}saveState();return renderFocus();}"
  );

  html=html.replace(
    "setInterval(()=>{ if(state.focus.running){ state.focus.remainingSec=Math.max(0,(state.focus.remainingSec||0)-1); const task=state.tasks.find(t=>t.id===state.focus.taskId); if(task)task.elapsed=(task.elapsed||0)+1; const el=document.getElementById('focusClockText');if(el)el.textContent=formatTime(state.focus.remainingSec); if(state.focus.remainingSec<=0){state.focus.running=false;playFocusFinishedSignal();showToast('Focusblok klaar.');saveState();renderFocus();} if(state.focus.remainingSec%10===0)saveState(); } },1000);",
    "setInterval(()=>{ if(state.focus.running){ const before=Number(state.focus.remainingSec||0),now=Date.now(); if(!state.focus.endAt)state.focus.endAt=now+before*1000; state.focus.remainingSec=Math.max(0,Math.ceil((Number(state.focus.endAt)-now)/1000)); const elapsed=Math.max(0,before-state.focus.remainingSec),task=state.tasks.find(t=>t.id===state.focus.taskId); if(task&&elapsed)task.elapsed=(task.elapsed||0)+elapsed; const el=document.getElementById('focusClockText');if(el)el.textContent=formatTime(state.focus.remainingSec); if(state.focus.remainingSec<=0){state.focus.running=false;state.focus.endAt=null;playFocusFinishedSignal();showToast('Focusblok klaar.');saveState();renderFocus();} else if(state.focus.remainingSec%10===0)saveState(); } },1000);"
  );

  html=html.replace(
`  function playFocusFinishedSignal(){
    if(!state.focus.soundEnabled) return;
    const ctx=ensureFocusAudio();
    if(ctx){
      try{
        const now=ctx.currentTime;
        [[0,660],[0.18,880],[0.36,1040]].forEach(([delay,freq])=>{
          const osc=ctx.createOscillator(),gain=ctx.createGain();
          osc.type='sine'; osc.frequency.value=freq;
          gain.gain.setValueAtTime(0.0001,now+delay);
          gain.gain.exponentialRampToValueAtTime(0.16,now+delay+0.015);
          gain.gain.exponentialRampToValueAtTime(0.0001,now+delay+0.15);
          osc.connect(gain); gain.connect(ctx.destination);
          osc.start(now+delay); osc.stop(now+delay+0.17);
        });
      }catch{}
    }
    if(navigator.vibrate){ try{ navigator.vibrate([180,100,180,100,320]); }catch{} }
  }`,
`  let focusAlarmNodes=[];
  let focusAlarmVibrateTimer=null;
  function stopFocusAlarm(){
    focusAlarmNodes.forEach(node=>{try{if(typeof node.stop==='function')node.stop();}catch{}try{if(typeof node.disconnect==='function')node.disconnect();}catch{}});
    focusAlarmNodes=[];
    if(focusAlarmVibrateTimer){clearInterval(focusAlarmVibrateTimer);focusAlarmVibrateTimer=null;}
    if(navigator.vibrate){try{navigator.vibrate(0);}catch{}}
    document.getElementById('focusAlarmOverlay')?.remove();
    if('serviceWorker'in navigator){navigator.serviceWorker.ready.then(reg=>reg.getNotifications?reg.getNotifications({tag:'focus-finished'}):[]).then(list=>(list||[]).forEach(n=>n.close())).catch(()=>{});}
  }
  function focusAlarmVibrate(){if(navigator.vibrate){try{navigator.vibrate([420,180,420,180,420,180,650]);}catch{}}}
  function showFocusAlarmOverlay(){
    document.getElementById('focusAlarmOverlay')?.remove();
    const overlay=document.createElement('div');
    overlay.id='focusAlarmOverlay';
    overlay.setAttribute('role','dialog');
    overlay.setAttribute('aria-modal','true');
    overlay.style.cssText='position:fixed;inset:0;z-index:99999;background:rgba(20,25,20,.74);display:grid;place-items:center;padding:24px;backdrop-filter:blur(6px)';
    overlay.innerHTML='<div style="width:min(100%,420px);background:#fff;border-radius:26px;padding:28px 22px;text-align:center;box-shadow:0 24px 70px rgba(0,0,0,.3)"><div style="font-size:32px;font-weight:900;margin-bottom:8px;color:#20251f">Focus klaar</div><div style="font-size:16px;line-height:1.45;color:#6b7168;margin-bottom:24px">Je focusblok is afgelopen.</div><button id="focusAlarmStopButton" type="button" style="width:100%;min-height:68px;border:0;border-radius:18px;background:#45624d;color:#fff;font-size:20px;font-weight:900;cursor:pointer">Stop alarm</button></div>';
    document.body.appendChild(overlay);
    document.getElementById('focusAlarmStopButton')?.addEventListener('click',stopFocusAlarm,{once:true});
  }
  function playFocusFinishedSignal(){
    if(!state.focus.soundEnabled) return;
    stopFocusAlarm();
    const ctx=ensureFocusAudio();
    if(ctx){
      try{
        if(ctx.state==='suspended')ctx.resume().catch(()=>{});
        const carrier=ctx.createOscillator(),gate=ctx.createGain(),pulse=ctx.createOscillator(),depth=ctx.createGain();
        carrier.type='square';carrier.frequency.value=920;
        gate.gain.value=0.32;
        pulse.type='square';pulse.frequency.value=1.65;
        depth.gain.value=0.32;
        pulse.connect(depth);depth.connect(gate.gain);carrier.connect(gate);gate.connect(ctx.destination);
        carrier.start();pulse.start();
        focusAlarmNodes=[carrier,pulse,gate,depth];
      }catch{}
    }
    showFocusAlarmOverlay();
    focusAlarmVibrate();
    focusAlarmVibrateTimer=setInterval(focusAlarmVibrate,4200);
    if(document.hidden&&'serviceWorker'in navigator&&typeof Notification!=='undefined'&&Notification.permission==='granted'){
      navigator.serviceWorker.ready.then(reg=>reg.showNotification('Focus klaar',{body:'Open de app om het alarm te stoppen.',tag:'focus-finished',icon:'./icon-192.png',badge:'./icon-192.png',requireInteraction:true,vibrate:[420,180,420,180,650],data:{url:'./'}})).catch(()=>{});
    }
  }`
  );

  html=html.replace(
    "function renderShopping(){ const wrap=document.getElementById('shoppingContent'); if(!wrap)return; if(!state.shopping.length)",
    "function renderShopping(){ const wrap=document.getElementById('shoppingContent'); if(!wrap)return; const hadShoppingGroups=Boolean(wrap.querySelector('.shop-group')); const openShoppingGroups=new Set([...wrap.querySelectorAll('.shop-group[open]')].map(d=>d.querySelector('summary span')?.textContent||'')); if(!state.shopping.length)"
  );

  html=html.replace(
    "<details class=\"shop-group\" ${gi<2?'open':''}>",
    "<details class=\"shop-group\" ${openShoppingGroups.has(cat)||(!hadShoppingGroups&&gi<2)?'open':''}>"
  );

  html=html.replace(
    '.household-manage-actions { display:grid; grid-template-columns:1fr 1fr; gap:7px; margin-top:10px; }',
    '.household-manage-actions { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:7px; margin-top:10px; }'
  );
  html=html.replace(
    '.household-manage-actions button.danger { color:var(--danger); }',
    '.household-manage-actions button.danger { color:var(--danger); } .household-manage-actions button.wide { grid-column:1/-1; }'
  );

  html=html.replace(
    "function householdVisibleToday(){ const energy=Number(state.energy)||1,limit=householdLimit(); const fixed=state.householdTasks.filter(t=>t.fixedReminder&&(householdDoneToday(t)||householdIsDue(t))); const doneToday=state.householdTasks.filter(t=>!t.fixedReminder&&householdDoneToday(t)); const due=state.householdTasks.filter(t=>!t.fixedReminder&&householdIsDue(t)&&Number(t.effort||1)<=energy).sort((a,b)=>householdPriority(b)-householdPriority(a)||Number(a.effort)-Number(b.effort)); const openSlots=Math.max(0,limit-doneToday.length); return [...fixed,...doneToday,...due.slice(0,openSlots)]; }",
    "function householdTodayChoiceState(){const today=localDateKey();if(!state.householdTodayChoice||state.householdTodayChoice.date!==today)state.householdTodayChoice={date:today,taskIds:[]};state.householdTodayChoice.taskIds=Array.isArray(state.householdTodayChoice.taskIds)?state.householdTodayChoice.taskIds:[];return state.householdTodayChoice;} function householdSwapState(){const today=localDateKey();if(!state.householdSwapState||state.householdSwapState.date!==today)state.householdSwapState={date:today,taskIds:[]};state.householdSwapState.taskIds=Array.isArray(state.householdSwapState.taskIds)?state.householdSwapState.taskIds:[];return state.householdSwapState;} function householdVisibleToday(){ const energy=Number(state.energy)||1,limit=householdLimit(),today=localDateKey(); const fixed=state.householdTasks.filter(t=>t.fixedReminder&&(householdDoneToday(t)||householdIsDue(t))); const doneToday=state.householdTasks.filter(t=>!t.fixedReminder&&householdDoneToday(t)); const byId=new Map(state.householdTasks.map(t=>[t.id,t])),choice=householdTodayChoiceState(),skipped=new Set(householdSwapState().taskIds||[]); const manual=choice.taskIds.map(id=>byId.get(id)).filter(t=>t&&!t.fixedReminder&&!householdDoneToday(t)&&!t.paused); const manualIds=new Set(manual.map(t=>t.id)); const due=state.householdTasks.filter(t=>!t.fixedReminder&&householdIsDue(t)&&Number(t.effort||1)<=energy&&!manualIds.has(t.id)&&!skipped.has(t.id)).sort((a,b)=>householdPriority(b)-householdPriority(a)||Number(a.effort)-Number(b.effort)); const openSlots=Math.max(0,limit-doneToday.length),picked=manual.slice(0,openSlots),fill=Math.max(0,openSlots-picked.length); return [...fixed,...doneToday,...picked,...due.slice(0,fill)]; }"
  );

  html=html.replace(
    "function renderHouseholdManage(){ const wrap=document.getElementById('householdManageList');if(!wrap)return; const today=localDateKey();wrap.innerHTML=state.householdTasks.length?state.householdTasks.map(t=>`<div class=\"household-manage-item ${t.paused?'paused':''}\"><div class=\"household-manage-top\"><div class=\"household-manage-name\">${escapeHtml(t.name)}<div class=\"household-meta\">${t.fixedReminder?'<span class=\"household-badge light\">Vaste reminder</span>':`<span class=\"household-badge ${Number(t.effort)===1?'light':''}\">${escapeHtml(householdEffortLabel(t.effort))}</span>`}<span class=\"household-badge\">${escapeHtml(householdFrequencyLabel(t.frequency))}</span>${t.paused?'<span class=\"household-badge\">Gepauzeerd</span>':''}${t.deferUntil&&t.deferUntil>today?`<span class=\"household-badge\">Tot ${escapeHtml(formatDateShort(t.deferUntil))}</span>`:''}</div></div></div><div class=\"household-manage-actions\"><button class=\"accent\" data-household-edit=\"${t.id}\">Bewerken</button><button data-household-tomorrow=\"${t.id}\">Morgen</button><button data-household-pause=\"${t.id}\">${t.paused?'Activeren':'Pauzeren'}</button><button class=\"danger\" data-household-delete=\"${t.id}\">Verwijderen</button></div></div>`).join(''):`<div class=\"empty-state\">Nog geen huishoudklusjes.</div>`; }",
    "function renderHouseholdManage(){ const wrap=document.getElementById('householdManageList');if(!wrap)return; const today=localDateKey(),chosen=new Set(householdTodayChoiceState().taskIds||[]);wrap.innerHTML=state.householdTasks.length?state.householdTasks.map(t=>{const done=householdDoneToday(t),picked=chosen.has(t.id);return `<div class=\"household-manage-item ${t.paused?'paused':''}\"><div class=\"household-manage-top\"><div class=\"household-manage-name\">${escapeHtml(t.name)}<div class=\"household-meta\">${t.fixedReminder?'<span class=\"household-badge light\">Vaste reminder</span>':`<span class=\"household-badge ${Number(t.effort)===1?'light':''}\">${escapeHtml(householdEffortLabel(t.effort))}</span>`}<span class=\"household-badge\">${escapeHtml(householdFrequencyLabel(t.frequency))}</span>${picked?'<span class=\"household-badge light\">Vandaag gekozen</span>':''}${done?'<span class=\"household-badge light\">Vandaag gedaan</span>':''}${t.paused?'<span class=\"household-badge\">Gepauzeerd</span>':''}${t.deferUntil&&t.deferUntil>today?`<span class=\"household-badge\">Tot ${escapeHtml(formatDateShort(t.deferUntil))}</span>`:''}</div></div></div><div class=\"household-manage-actions\"><button class=\"accent\" data-household-now=\"${t.id}\">${picked?'Vandaag ✓':'Nu doen'}</button><button data-household-done=\"${t.id}\">${done?'Ongedaan':'Gedaan'}</button><button data-household-manage-focus=\"${t.id}\">Focus</button><button data-household-edit=\"${t.id}\">Bewerken</button><button data-household-tomorrow=\"${t.id}\">Morgen</button><button data-household-pause=\"${t.id}\">${t.paused?'Activeren':'Pauzeren'}</button><button class=\"danger wide\" data-household-delete=\"${t.id}\">Verwijderen</button></div></div>`;}).join(''):`<div class=\"empty-state\">Nog geen huishoudklusjes.</div>`; }"
  );

  html=html.replace(
    '<button class="focus-list-button" data-focus-household="${t.id}">Focus</button></div>',
    '<div class="focus-item-actions"><button class="focus-list-button" data-focus-household="${t.id}">Focus</button>${t.fixedReminder?\'\':`<button class="focus-list-button" data-household-tomorrow="${t.id}">Morgen</button><button class="focus-list-button" data-household-swap="${t.id}">Andere</button>`}</div></div>'
  );

  html=html.replace(
    "const htom=e.target.closest('[data-household-tomorrow]');if(htom){const t=state.householdTasks.find(x=>x.id===htom.dataset.householdTomorrow);if(t){t.deferUntil=addDaysKey(1);t.paused=false;saveState();renderHousehold();renderHouseholdManage();showToast('Huishoudklus staat voor morgen.');}return;}",
    "const hnow=e.target.closest('[data-household-now]');if(hnow){const t=state.householdTasks.find(x=>x.id===hnow.dataset.householdNow);if(t){t.paused=false;t.deferUntil='';const c=householdTodayChoiceState();c.taskIds=[t.id,...c.taskIds.filter(id=>id!==t.id)].slice(0,householdLimit());const s=householdSwapState();s.taskIds=s.taskIds.filter(id=>id!==t.id);saveState();renderHousehold();renderHouseholdManage();showToast(`${t.name} staat nu bij Vandaag.`);}return;} const hdone=e.target.closest('[data-household-done]');if(hdone){const t=state.householdTasks.find(x=>x.id===hdone.dataset.householdDone);if(t){t.history||={};const today=localDateKey();if(t.history[today])delete t.history[today];else{t.history[today]=true;t.deferUntil='';}saveState();renderHousehold();renderHouseholdManage();showToast(t.history[today]?`${t.name} afgevinkt.`:`${t.name} staat weer open.`);}return;} const hmf=e.target.closest('[data-household-manage-focus]');if(hmf){const t=state.householdTasks.find(x=>x.id===hmf.dataset.householdManageFocus);if(t){t.paused=false;t.deferUntil='';const c=householdTodayChoiceState();c.taskIds=[t.id,...c.taskIds.filter(id=>id!==t.id)].slice(0,householdLimit());const s=householdSwapState();s.taskIds=s.taskIds.filter(id=>id!==t.id);document.getElementById('householdModal').classList.remove('open');saveState();renderHousehold();return selectFocusItem('household',t.id);}return;} const htom=e.target.closest('[data-household-tomorrow]');if(htom){const t=state.householdTasks.find(x=>x.id===htom.dataset.householdTomorrow);if(t){t.deferUntil=addDaysKey(1);t.paused=false;const c=householdTodayChoiceState();c.taskIds=c.taskIds.filter(id=>id!==t.id);saveState();renderHousehold();renderHouseholdManage();showToast('Huishoudklus staat voor morgen.');}return;} const hswap=e.target.closest('[data-household-swap]');if(hswap){const t=state.householdTasks.find(x=>x.id===hswap.dataset.householdSwap);if(t){const c=householdTodayChoiceState();c.taskIds=c.taskIds.filter(id=>id!==t.id);const s=householdSwapState();if(!s.taskIds.includes(t.id))s.taskIds.push(t.id);const energy=Number(state.energy)||1;const remaining=state.householdTasks.filter(x=>!x.fixedReminder&&!householdDoneToday(x)&&householdIsDue(x)&&Number(x.effort||1)<=energy&&!s.taskIds.includes(x.id));if(!remaining.length)s.taskIds=[t.id];saveState();renderHousehold();renderHouseholdManage();showToast('Andere huishoudtaak gekozen.');}return;}"
  );

  html=html.replace(
    "const hdel=e.target.closest('[data-household-delete]');if(hdel){const t=state.householdTasks.find(x=>x.id===hdel.dataset.householdDelete);if(!t)return;if(!confirm(`“${t.name}” verwijderen?`))return;state.householdTasks=state.householdTasks.filter(x=>x.id!==t.id);saveState();renderHousehold();renderHouseholdManage();return showToast('Huishoudklus verwijderd.');}",
    "const hdel=e.target.closest('[data-household-delete]');if(hdel){const t=state.householdTasks.find(x=>x.id===hdel.dataset.householdDelete);if(!t)return;state.householdTasks=state.householdTasks.filter(x=>x.id!==t.id);if(state.householdTodayChoice?.taskIds)state.householdTodayChoice.taskIds=state.householdTodayChoice.taskIds.filter(id=>id!==t.id);if(state.householdSwapState?.taskIds)state.householdSwapState.taskIds=state.householdSwapState.taskIds.filter(id=>id!==t.id);if(state.focus?.targetType==='household'&&state.focus?.targetId===t.id){state.focus.targetType=null;state.focus.targetId=null;state.focus.running=false;}saveState();renderHousehold();renderHouseholdManage();return showToast('Huishoudklus verwijderd.');}"
  );

  const extraRecipeScripts='<script src="./recipes-extra-1.js"></script>\n<script src="./recipes-extra-2.js"></script>\n<script src="./recipes-extra-3.js"></script>\n<script src="./recipes-extra-4.js"></script>\n';
  if(!html.includes('recipes-extra-1.js')){
    html=html.replace(/<script>\s*\(\(\) => \{/,extraRecipeScripts+'<script>\n(() => {');
  }
  html=html.replace('const recipes = [{','const recipes = [...(window.EXTRA_RECIPES||[]),{');

  return html;
}

async function enhanceHtml(response){
  const type=response.headers.get('content-type')||'';
  if(!type.includes('text/html'))return response;
  let html=applyDailyFixes(await response.text());
  if(!html.includes('calendar.js'))html=html.replace(/<\/body>/i,'  <script src="./calendar.js"></script>\n</body>');
  const headers=new Headers(response.headers);
  headers.delete('content-length');
  headers.set('content-type','text/html; charset=utf-8');
  return new Response(html,{status:response.status,statusText:response.statusText,headers});
}

self.addEventListener('fetch',event=>{
  if(event.request.method!=='GET')return;
  if(event.request.mode==='navigate'){
    event.respondWith((async()=>{
      try{
        const network=await fetch(event.request,{cache:'no-store'});
        return enhanceHtml(network);
      }catch{
        const cached=await caches.match(event.request)||await caches.match('./index.html');
        return cached?enhanceHtml(cached):new Response('Offline',{status:503});
      }
    })());
    return;
  }
  event.respondWith(fetch(event.request).then(r=>{const copy=r.clone();caches.open(CACHE_NAME).then(c=>c.put(event.request,copy)).catch(()=>{});return r;}).catch(()=>caches.match(event.request)));
});

self.addEventListener('push',event=>{
  let data={};
  try{data=event.data?event.data.json():{};}catch{try{data={body:event.data?.text()||''};}catch{data={};}}
  const title=data.title||'Mijn app';
  const options={body:data.body||'Je hebt nog iets kleins openstaan.',tag:data.tag||'mijn-app-herinnering',icon:'./icon-192.png',badge:'./icon-192.png',data:{url:data.url||'./'},renotify:false};
  event.waitUntil(self.registration.showNotification(title,options));
});

self.addEventListener('notificationclick',event=>{
  event.notification.close();
  const target=event.notification?.data?.url||'./';
  event.waitUntil(self.clients.matchAll({type:'window',includeUncontrolled:true}).then(list=>{
    for(const client of list){if('focus'in client&&client.url.includes('/mijn-persoonlijke-app/')){client.navigate(target).catch(()=>{});return client.focus();}}
    return self.clients.openWindow?self.clients.openWindow(target):undefined;
  }));
});
